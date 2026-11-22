package app.plainly.statement;

import static app.plainly.CorrectionTestData.OCT_15;
import static app.plainly.CorrectionTestData.OCT_22;
import static app.plainly.CorrectionTestData.OCT_8;
import static app.plainly.CorrectionTestData.REASON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.CorrectionTestData;
import app.plainly.CorrectionTestData.Paid;
import app.plainly.IntegrationTest;
import app.plainly.PaymentTestData;
import app.plainly.PaymentTestData.Owner;
import app.plainly.PaymentTestData.Started;
import app.plainly.StripeTestSupport;
import app.plainly.common.NotFoundException;
import app.plainly.ledger.EntryType;
import app.plainly.ledger.LedgerLine;
import app.plainly.ledger.LedgerQueryService;
import app.plainly.policy.AddCoverageService;
import app.plainly.policy.CancelPolicyCommand;
import app.plainly.policy.CancelPolicyService;
import app.plainly.policy.CorrectCoverageCommand;
import app.plainly.policy.CorrectCoverageService;
import app.plainly.policy.IssuePolicyCommand;
import app.plainly.policy.IssuePolicyService;
import app.plainly.statement.Statement.BannerState;
import app.plainly.statement.Statement.MoneyStatus;
import app.plainly.statement.Statement.Ref;
import app.plainly.statement.Statement.RefKind;
import app.plainly.statement.Statement.Row;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * P3 "Your statement" and the P4 traces, built from real postings. Worked numbers: a Sep 1, 2026 → Sep 1, 2027
 * policy (365 days), base cover $1,200.00, extra cover $730.00 a year from Oct 15 = $730.00 × 321 ÷ 365 = $642.00.
 * Moving its start to Oct 8 undoes $642.00 and charges $730.00 × 328 ÷ 365 = $656.00 (net +$14.00, 7 more days ×
 * $2.00); moving it to Oct 22 charges $730.00 × 314 ÷ 365 = $628.00 (net −$14.00, refunded to card ending 4242).
 */
// covers AC2, AC3, AC4, AC5, AC6, AC7, T1
@IntegrationTest
@AutoConfigureMockMvc
class StatementServiceTest {

    static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    static final LocalDate POLICY_END = LocalDate.of(2027, 9, 1);
    static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);
    static final LocalDate TODAY = LocalDate.of(2026, 11, 22); // plainly.today in IntegrationTest

    @Autowired StatementService service;
    @Autowired LedgerQueryService ledger;
    @Autowired IssuePolicyService issuer;
    @Autowired AddCoverageService adder;
    @Autowired CorrectCoverageService corrector;
    @Autowired CancelPolicyService canceller;
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    // --- scenario builders ---

    Paid paid(String business) throws Exception {
        return CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issuer, adder, business);
    }

    CorrectCoverageService.Corrected correct(long policyId, LocalDate start) {
        return corrector.correct(policyId, new CorrectCoverageCommand(start, REASON),
                CorrectionTestData.brokerId(jdbc));
    }

    String brokerName() {
        return jdbc.sql("select display_name from app_users where id = ?").param(CorrectionTestData.brokerId(jdbc))
                .query(String.class).single();
    }

    /** A payment the owner sent that the bank is still processing. */
    Started paymentWaiting(Owner owner) throws Exception {
        Started started = PaymentTestData.startPayment(mvc, jdbc, owner);
        deliver(StripeTestSupport.intentProcessing(StripeTestSupport.newEventId(), started.intentId(),
                started.amountCents()));
        return started;
    }

    /** A payment the bank declined: a $0.00 PAYMENT line keeps the attempt on the ledger. */
    void paymentDeclined(Owner owner, String last4) throws Exception {
        Started started = PaymentTestData.startPayment(mvc, jdbc, owner);
        deliver(StripeTestSupport.chargeFailed(StripeTestSupport.newEventId(), started.intentId(),
                started.amountCents(), last4, "Your card was declined."));
    }

    record SentRefund(long id, String displayId, String stripeRefundId, String intentId, long amountCents) {}

    SentRefund refund(String displayId) {
        return jdbc.sql("""
                select r.id, r.display_id, r.stripe_refund_id, p.stripe_payment_intent_id, r.amount_cents
                from refunds r join payments p on p.id = r.payment_id where r.display_id = ?""")
                .param(displayId)
                .query((rs, n) -> new SentRefund(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getLong(5)))
                .single();
    }

    void refundSettles(SentRefund r) throws Exception {
        deliver(StripeTestSupport.refundSucceeded(StripeTestSupport.newEventId(), r.stripeRefundId(), r.id(),
                r.intentId(), r.amountCents()));
    }

    void deliver(String body) throws Exception {
        mvc.perform(StripeTestSupport.delivery(body)).andExpect(status().isOk());
    }

    static List<Row> settled(Statement s) {
        return s.rows().stream().filter(r -> !r.pending()).toList();
    }

    static List<Row> pending(Statement s) {
        return s.rows().stream().filter(Row::pending).toList();
    }

    static Row row(Statement s, String id) {
        return s.rows().stream().filter(r -> id.equals(r.id())).findFirst().orElseThrow();
    }

    static LedgerLine line(List<LedgerLine> lines, EntryType type, long amountCents) {
        return lines.stream().filter(l -> l.type() == type && l.amountCents() == amountCents)
                .reduce((a, b) -> b).orElseThrow();
    }

    static List<Ref> refs(Object... kindThenId) {
        List<Ref> out = new java.util.ArrayList<>();
        for (int i = 0; i < kindThenId.length; i += 2) {
            out.add(new Ref((RefKind) kindThenId[i], (String) kindThenId[i + 1]));
        }
        return out;
    }

    // --- every line, oldest first, with a running balance that ends on the ledger balance (AC2) ---

    @Test
    void everyLedgerLineIsListedOldestFirstWithItsRunningBalance() throws Exception {
        Paid paid = paid("Running Balance Bakery LLC");
        long id = paid.owner().policyId();

        Statement s = service.forPolicy(id);

        List<LedgerLine> lines = ledger.lines(id);
        assertThat(settled(s)).extracting(Row::id).containsExactlyElementsOf(lines.stream().map(LedgerLine::displayId).toList());
        assertThat(settled(s)).extracting(Row::amountCents).containsExactly(120_000L, -120_000L, 64_200L, -64_200L);
        assertThat(settled(s)).extracting(Row::balanceAfterCents).containsExactly(120_000L, 0L, 64_200L, 0L);
        assertThat(settled(s)).extracting(Row::kind)
                .containsExactly(EntryType.CHARGE, EntryType.PAYMENT, EntryType.CHARGE, EntryType.PAYMENT);
        assertThat(settled(s)).allSatisfy(r -> {
            assertThat(r.date()).isNotNull();
            assertThat(r.title()).isNotBlank();
        });
        assertThat(s.header().balanceCents()).isZero();
        assertThat(s.header().policyNumber()).isNotBlank();
        assertThat(s.header().businessName()).isEqualTo("Running Balance Bakery LLC");
        assertThat(s.today()).isEqualTo(TODAY);
    }

    @Test
    void theLastBalanceEqualsTheLedgerBalanceToTheCentAfterCorrectionsAndRefunds() throws Exception {
        Paid paid = paid("Balance To The Cent LLC");
        long id = paid.owner().policyId();
        CorrectCoverageService.Corrected toOct22 = correct(id, OCT_22); // −$14.00, refunded
        refundSettles(refund(toOct22.refunds().getFirst().displayId()));
        correct(id, OCT_8); // +$28.00 owed
        paymentDeclined(paid.owner(), "0002");

        Statement s = service.forPolicy(id);

        long running = 0;
        for (Row r : settled(s)) {
            running += r.amountCents();
            assertThat(r.balanceAfterCents()).as(r.id()).isEqualTo(running);
        }
        assertThat(settled(s).getLast().balanceAfterCents()).isEqualTo(ledger.balance(id)).isEqualTo(2_800);
        assertThat(s.header().balanceCents()).isEqualTo(ledger.balance(id));
        assertThat(settled(s)).hasSameSizeAs(ledger.lines(id));
    }

    @Test
    void anUnknownPolicyIsNotFound() {
        assertThatThrownBy(() -> service.forPolicy(Long.MAX_VALUE)).isInstanceOf(NotFoundException.class);
    }

    // --- the banner (AC3) ---

    @Test
    void aNewPolicyOwesItsFirstYear() throws Exception {
        Owner owner = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "First Year Bakery LLC");

        Statement.Banner banner = service.forPolicy(owner.policyId()).banner();

        assertThat(banner.state()).isEqualTo(BannerState.OWES);
        assertThat(banner.amountCents()).isEqualTo(PaymentTestData.BASE_DUE_CENTS);
        assertThat(banner.dueReason().name()).isEqualTo("FIRST_YEAR");
    }

    @Test
    void afterACorrectionTheBannerSaysWhyFourteenDollarsIsDue() throws Exception {
        long id = paid("Owes After Fix LLC").owner().policyId();
        correct(id, OCT_8);

        Statement.Banner banner = service.forPolicy(id).banner();

        assertThat(banner.state()).isEqualTo(BannerState.OWES);
        assertThat(banner.amountCents()).isEqualTo(1_400);
        assertThat(banner.dueReason().name()).isEqualTo("CORRECTION");
    }

    @Test
    void aPaymentWithTheBankMakesTheBannerWaitForTheBank() throws Exception {
        Paid paid = paid("Waiting Bank Bakery LLC");
        correct(paid.owner().policyId(), OCT_8);
        paymentWaiting(paid.owner());

        Statement.Banner banner = service.forPolicy(paid.owner().policyId()).banner();

        assertThat(banner.state()).isEqualTo(BannerState.PAYMENT_WAITING);
        assertThat(banner.amountCents()).isEqualTo(1_400);
    }

    @Test
    void anUnsettledRefundSaysWeOweYouAndWhichCard() throws Exception {
        long id = paid("Owed Back Bakery LLC").owner().policyId();
        correct(id, OCT_22);

        Statement.Banner banner = service.forPolicy(id).banner();

        assertThat(banner.state()).isEqualTo(BannerState.REFUND_WAITING);
        assertThat(banner.amountCents()).isEqualTo(1_400);
        assertThat(banner.cardLast4()).isEqualTo("4242");
        assertThat(banner.dueReason()).isNull();
    }

    @Test
    void nothingOwedEitherWayIsAllPaidUp() throws Exception {
        long id = paid("Paid Up Bakery LLC").owner().policyId();

        Statement.Banner banner = service.forPolicy(id).banner();

        assertThat(banner.state()).isEqualTo(BannerState.PAID_UP);
        assertThat(banner.amountCents()).isZero();
        assertThat(banner.dueReason()).isNull();
    }

    // --- money on its way is shown but not counted; a decline is a $0.00 line (AC4) ---

    @Test
    void aPaymentWithTheBankIsAPendingRowOutsideTheBalance() throws Exception {
        Paid paid = paid("Pending Payment Bakery LLC");
        long id = paid.owner().policyId();
        correct(id, OCT_8);
        Started waiting = paymentWaiting(paid.owner());

        Statement s = service.forPolicy(id);

        assertThat(pending(s)).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo(EntryType.PAYMENT);
            assertThat(r.status()).isEqualTo(MoneyStatus.WAITING);
            assertThat(r.amountCents()).isEqualTo(-1_400);
            assertThat(r.balanceAfterCents()).isEqualTo(1_400); // unchanged
            assertThat(r.trace().money().status()).isEqualTo(MoneyStatus.WAITING);
            assertThat(r.trace().refs()).contains(new Ref(RefKind.STRIPE_PAYMENT, waiting.intentId()));
        });
        assertThat(s.rows().getLast().pending()).isTrue();
        assertThat(settled(s).getLast().balanceAfterCents()).isEqualTo(ledger.balance(id)).isEqualTo(1_400);
        assertThat(s.header().balanceCents()).isEqualTo(1_400);
    }

    @Test
    void aPaymentSentLateInTheEveningIsDatedByTheNewYorkDayNotTheUtcDay() throws Exception {
        Paid paid = paid("Late Evening Bakery LLC");
        long id = paid.owner().policyId();
        correct(id, OCT_8);
        Started waiting = paymentWaiting(paid.owner());
        // 9pm in New York on Nov 22 is already 2am on Nov 23 in UTC.
        jdbc.sql("update payments set created_at = ? where id = ?")
                .param(java.sql.Timestamp.from(java.time.Instant.parse("2026-11-23T02:00:00Z")))
                .param(waiting.paymentId())
                .update();

        Row r = pending(service.forPolicy(id)).getFirst();

        assertThat(r.date()).isEqualTo(TODAY);
        assertThat(r.trace().money().date()).isEqualTo(TODAY);
        assertThat(r.trace().cause().recordedOn()).isEqualTo(TODAY);
    }

    @Test
    void anUnsettledRefundIsAPendingRowOutsideTheBalance() throws Exception {
        long id = paid("Pending Refund Bakery LLC").owner().policyId();
        CorrectCoverageService.Corrected saved = correct(id, OCT_22);
        String refundId = saved.refunds().getFirst().displayId();

        Statement s = service.forPolicy(id);

        assertThat(pending(s)).singleElement().satisfies(r -> {
            assertThat(r.id()).isEqualTo(refundId);
            assertThat(r.kind()).isEqualTo(EntryType.REFUND);
            assertThat(r.status()).isEqualTo(MoneyStatus.REFUND_ON_ITS_WAY);
            assertThat(r.amountCents()).isEqualTo(1_400);
            assertThat(r.balanceAfterCents()).isEqualTo(-1_400); // unchanged until Stripe confirms
            assertThat(r.trace().money().cardLast4()).isEqualTo("4242");
            assertThat(r.trace().refs()).contains(new Ref(RefKind.CHANGE, saved.transactionDisplayId()),
                    new Ref(RefKind.REFUND, refundId));
        });
        assertThat(settled(s).getLast().balanceAfterCents()).isEqualTo(ledger.balance(id)).isEqualTo(-1_400);
    }

    @Test
    void onceTheRefundSettlesItIsALedgerRowAndNoLongerPending() throws Exception {
        long id = paid("Refund Settled Bakery LLC").owner().policyId();
        CorrectCoverageService.Corrected saved = correct(id, OCT_22);
        refundSettles(refund(saved.refunds().getFirst().displayId()));

        Statement s = service.forPolicy(id);

        assertThat(pending(s)).isEmpty();
        Row refund = settled(s).getLast();
        assertThat(refund.kind()).isEqualTo(EntryType.REFUND);
        assertThat(refund.status()).isEqualTo(MoneyStatus.REFUND_SENT);
        assertThat(refund.amountCents()).isEqualTo(1_400);
        assertThat(refund.balanceAfterCents()).isZero();
        assertThat(s.banner().state()).isEqualTo(BannerState.PAID_UP);
    }

    @Test
    void aDeclinedAttemptIsAZeroLineThatMovesNoMoney() throws Exception {
        Owner owner = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Declined Card Bakery LLC");
        paymentDeclined(owner, "0002");

        Statement s = service.forPolicy(owner.policyId());

        Row declined = settled(s).getLast();
        assertThat(declined.kind()).isEqualTo(EntryType.PAYMENT);
        assertThat(declined.status()).isEqualTo(MoneyStatus.DECLINED);
        assertThat(declined.amountCents()).isZero();
        assertThat(declined.balanceAfterCents()).isEqualTo(PaymentTestData.BASE_DUE_CENTS);
        assertThat(declined.pending()).isFalse();
        assertThat(declined.trace().money()).isEqualTo(new Statement.Money("0002", TODAY, MoneyStatus.DECLINED));
        assertThat(s.banner().state()).isEqualTo(BannerState.OWES);
    }

    // --- one card per correction (AC5, AC6) ---

    @Test
    void theTrialCorrectionIsOneCardWithTheOriginalItsUndoAndTheCorrectedLine() throws Exception {
        long id = paid("Trial Scenario Bakery LLC").owner().policyId();
        List<LedgerLine> before = ledger.lines(id);
        LedgerLine original = line(before, EntryType.CHARGE, 64_200);
        CorrectCoverageService.Corrected saved = correct(id, OCT_8);

        Statement s = service.forPolicy(id);

        assertThat(s.corrections()).singleElement().satisfies(c -> {
            assertThat(c.transactionDisplayId()).isEqualTo(saved.transactionDisplayId());
            assertThat(c.recordedOn()).isEqualTo(TODAY);
            assertThat(c.oldStart()).isEqualTo(OCT_15);
            assertThat(c.newStart()).isEqualTo(OCT_8);
            assertThat(c.dayDelta()).isEqualTo(7);
            assertThat(c.netCents()).isEqualTo(1_400);
            assertThat(c.perDayCents()).isEqualTo(200L);
            assertThat(c.reason()).isEqualTo(REASON);
            assertThat(c.actorName()).isEqualTo(brokerName());
            assertThat(c.originalLineId()).isEqualTo(original.displayId());
            assertThat(c.reversalLineId()).isEqualTo(saved.undoDisplayId());
            assertThat(c.rebillLineId()).isEqualTo(saved.rebillDisplayId());
        });

        Row orig = row(s, original.displayId());
        Row undo = row(s, saved.undoDisplayId());
        Row rebill = row(s, saved.rebillDisplayId());
        assertThat(orig.amountCents()).isEqualTo(64_200);
        assertThat(orig.undoneOn()).isEqualTo(TODAY);
        assertThat(undo.kind()).isEqualTo(EntryType.REVERSAL);
        assertThat(undo.amountCents()).isEqualTo(-64_200);
        assertThat(rebill.kind()).isEqualTo(EntryType.CHARGE);
        assertThat(rebill.amountCents()).isEqualTo(65_600);
        assertThat(rebill.undoneOn()).isNull();
        assertThat(List.of(orig, undo, rebill)).extracting(Row::correctionId)
                .containsOnly(saved.transactionDisplayId());
        assertThat(rebill.balanceAfterCents()).isEqualTo(1_400);

        // Lines no correction touched stay outside any card.
        assertThat(settled(s).getFirst().correctionId()).isNull();
        assertThat(settled(s).getFirst().undoneOn()).isNull();
    }

    @Test
    void theMirrorCaseNetsMinusFourteenDollarsAndShowsTheRefund() throws Exception {
        long id = paid("Mirror Case Bakery LLC").owner().policyId();
        CorrectCoverageService.Corrected saved = correct(id, OCT_22);

        Statement s = service.forPolicy(id);

        assertThat(s.corrections()).singleElement().satisfies(c -> {
            assertThat(c.oldStart()).isEqualTo(OCT_15);
            assertThat(c.newStart()).isEqualTo(OCT_22);
            assertThat(c.dayDelta()).isEqualTo(-7);
            assertThat(c.netCents()).isEqualTo(-1_400);
            assertThat(c.perDayCents()).isEqualTo(200L);
        });
        assertThat(row(s, saved.undoDisplayId()).amountCents()).isEqualTo(-64_200);
        assertThat(row(s, saved.rebillDisplayId()).amountCents()).isEqualTo(62_800);
        assertThat(pending(s)).extracting(Row::kind, Row::amountCents)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(EntryType.REFUND, 1_400L));
    }

    @Test
    void aSecondCorrectionIsItsOwnCardAndTheFirstCardStaysAsItWas() throws Exception {
        long id = paid("Debrief Bakery LLC").owner().policyId();
        CorrectCoverageService.Corrected first = correct(id, OCT_8);
        Statement.Correction firstCard = service.forPolicy(id).corrections().getFirst();
        CorrectCoverageService.Corrected second = correct(id, OCT_22);

        Statement s = service.forPolicy(id);

        assertThat(s.corrections()).hasSize(2);
        assertThat(s.corrections().getFirst()).isEqualTo(firstCard);
        assertThat(s.corrections().get(1)).satisfies(c -> {
            assertThat(c.originalLineId()).isEqualTo(first.rebillDisplayId());
            assertThat(c.reversalLineId()).isEqualTo(second.undoDisplayId());
            assertThat(c.oldStart()).isEqualTo(OCT_8);
            assertThat(c.newStart()).isEqualTo(OCT_22);
            assertThat(c.dayDelta()).isEqualTo(-14);
            assertThat(c.netCents()).isEqualTo(-2_800);
        });
        // The first corrected line is now undone too, but still belongs to the card that made it.
        assertThat(row(s, first.rebillDisplayId()).undoneOn()).isEqualTo(TODAY);
        assertThat(row(s, first.rebillDisplayId()).correctionId()).isEqualTo(first.transactionDisplayId());
    }

    @Test
    void thePerDayPriceIsLeftOutWhenItIsNotAWholeNumberOfCents() throws Exception {
        // Mar 1, 2027 → Mar 1, 2028 is 366 days (Feb 29, 2028), and $730.00 ÷ 366 isn't a whole number of cents.
        LocalDate mar1 = LocalDate.of(2027, 3, 1);
        long id = issuer.issue(new IssuePolicyCommand("Leap Year Bakery LLC", "a shop", "Lee Owner",
                app.plainly.TestData.uniqueEmail("leap"), mar1, true), CorrectionTestData.brokerId(jdbc)).policyId();
        correct(id, mar1.plusDays(7));

        Statement.Correction c = service.forPolicy(id).corrections().getFirst();

        assertThat(c.dayDelta()).isEqualTo(-7);
        assertThat(c.netCents()).isEqualTo(71_604 - 73_000); // $730.00 × 359 ÷ 366 = $716.04
        assertThat(c.perDayCents()).isNull();
    }

    // --- every amount has a trace with its references (AC7) ---

    @Test
    void theCorrectedChargeShowsItsMathsTheChangeAndWhoMadeIt() throws Exception {
        long id = paid("Charge Trace Bakery LLC").owner().policyId();
        CorrectCoverageService.Corrected saved = correct(id, OCT_8);

        Statement.Trace t = row(service.forPolicy(id), saved.rebillDisplayId()).trace();

        assertThat(t.kind()).isEqualTo(EntryType.CHARGE);
        assertThat(t.maths()).isEqualTo(new Statement.Maths(73_000L, 328, 365, OCT_8, POLICY_END, 65_600, false,
                null, null));
        assertThat(t.cause()).isEqualTo(new Statement.Cause("CORRECT", OCT_8, TODAY, brokerName(), REASON, OCT_15,
                OCT_8));
        assertThat(t.money()).isNull();
        assertThat(t.refs()).isEqualTo(refs(RefKind.CHANGE, saved.transactionDisplayId(),
                RefKind.LINE, saved.rebillDisplayId()));
    }

    @Test
    void theReversalShowsTheUndoneLinesNumbersWithTheSignFlipped() throws Exception {
        long id = paid("Reversal Trace Bakery LLC").owner().policyId();
        LedgerLine original = line(ledger.lines(id), EntryType.CHARGE, 64_200);
        CorrectCoverageService.Corrected saved = correct(id, OCT_8);

        Statement.Trace t = row(service.forPolicy(id), saved.undoDisplayId()).trace();

        assertThat(t.kind()).isEqualTo(EntryType.REVERSAL);
        assertThat(t.maths()).isEqualTo(new Statement.Maths(73_000L, 321, 365, OCT_15, POLICY_END, -64_200, true,
                null, null));
        assertThat(t.cause().transactionType()).isEqualTo("CORRECT");
        assertThat(t.cause().reason()).isEqualTo(REASON);
        assertThat(t.refs()).isEqualTo(refs(RefKind.CHANGE, saved.transactionDisplayId(),
                RefKind.LINE, saved.undoDisplayId(), RefKind.UNDOES, original.displayId()));
    }

    @Test
    void theIssueChargeIsCausedByIssuingThePolicyWithNoReason() throws Exception {
        long id = paid("Issue Trace Bakery LLC").owner().policyId();

        Statement.Trace t = settled(service.forPolicy(id)).getFirst().trace();

        assertThat(t.maths()).isEqualTo(new Statement.Maths(120_000L, 365, 365, SEP_1, POLICY_END, 120_000, false,
                null, null));
        assertThat(t.cause().transactionType()).isEqualTo("ISSUE");
        assertThat(t.cause().effectiveDate()).isEqualTo(SEP_1);
        assertThat(t.cause().actorName()).isEqualTo(brokerName());
        assertThat(t.cause().reason()).isNull();
        assertThat(t.refs()).extracting(Ref::kind).containsExactly(RefKind.CHANGE, RefKind.LINE);
    }

    @Test
    void aPaymentShowsTheBalanceBeforeAndAfterTheCardAndItsStripeIds() throws Exception {
        Paid paid = paid("Payment Trace Bakery LLC");
        long id = paid.owner().policyId();
        LedgerLine payment = ledger.lines(id).getLast();

        Statement.Trace t = row(service.forPolicy(id), payment.displayId()).trace();

        assertThat(t.kind()).isEqualTo(EntryType.PAYMENT);
        assertThat(t.maths().balanceBeforeCents()).isEqualTo(64_200L);
        assertThat(t.maths().balanceAfterCents()).isEqualTo(0L);
        assertThat(t.maths().resultCents()).isEqualTo(-64_200);
        assertThat(t.cause().actorName()).isEqualTo("Pat Payer"); // the owner paid
        assertThat(t.money()).isEqualTo(new Statement.Money("4242", TODAY, MoneyStatus.PAID));
        assertThat(t.refs()).isEqualTo(refs(RefKind.LINE, payment.displayId(),
                RefKind.STRIPE_PAYMENT, paid.extraIntentId(), RefKind.STRIPE_EVENT, payment.stripeEventId()));
        assertThat(payment.stripeEventId()).startsWith("evt_");
    }

    @Test
    void aSettledRefundShowsTheChangeTheCardAndEveryId() throws Exception {
        long id = paid("Refund Trace Bakery LLC").owner().policyId();
        CorrectCoverageService.Corrected saved = correct(id, OCT_22);
        SentRefund sent = refund(saved.refunds().getFirst().displayId());
        refundSettles(sent);
        LedgerLine line = ledger.lines(id).getLast();

        Statement.Trace t = row(service.forPolicy(id), line.displayId()).trace();

        assertThat(t.kind()).isEqualTo(EntryType.REFUND);
        assertThat(t.maths().balanceBeforeCents()).isEqualTo(-1_400L);
        assertThat(t.maths().balanceAfterCents()).isEqualTo(0L);
        assertThat(t.cause().transactionType()).isEqualTo("CORRECT");
        assertThat(t.cause().oldStart()).isEqualTo(OCT_15);
        assertThat(t.cause().newStart()).isEqualTo(OCT_22);
        assertThat(t.money()).isEqualTo(new Statement.Money("4242", TODAY, MoneyStatus.REFUND_SENT));
        assertThat(t.refs()).isEqualTo(refs(RefKind.CHANGE, saved.transactionDisplayId(),
                RefKind.LINE, line.displayId(), RefKind.REFUND, sent.displayId(),
                RefKind.STRIPE_REFUND, sent.stripeRefundId(), RefKind.STRIPE_EVENT, line.stripeEventId()));
    }

    @Test
    void aCancellationCreditShowsItsUnusedDaysAndTheBrokersReason() throws Exception {
        long id = paid("Credit Trace Bakery LLC").owner().policyId();
        String why = "The bakery closed for good.";
        CancelPolicyService.Cancelled cancelled = canceller.cancel(id, new CancelPolicyCommand(DEC_1, why),
                CorrectionTestData.brokerId(jdbc));
        LedgerLine credit = ledger.lines(id).stream()
                .filter(l -> l.type() == EntryType.CREDIT && l.calcAnnualCents() == 120_000L).findFirst().orElseThrow();

        Statement.Trace t = row(service.forPolicy(id), credit.displayId()).trace();

        assertThat(t.kind()).isEqualTo(EntryType.CREDIT);
        assertThat(t.maths().annualCents()).isEqualTo(120_000L);
        assertThat(t.maths().days()).isEqualTo(274); // Dec 1, 2026 → Sep 1, 2027
        assertThat(t.maths().termDays()).isEqualTo(365);
        assertThat(t.maths().resultCents()).isEqualTo(credit.amountCents()).isNegative();
        assertThat(t.cause().transactionType()).isEqualTo("CANCEL");
        assertThat(t.cause().reason()).isEqualTo(why);
        assertThat(t.cause().actorName()).isEqualTo(brokerName());
        assertThat(t.refs()).isEqualTo(refs(RefKind.CHANGE, cancelled.transactionDisplayId(),
                RefKind.LINE, credit.displayId()));
    }

    @Test
    void everyRowHasATraceOfItsOwnKindWithItsReferences() throws Exception {
        Paid paid = paid("Every Amount Bakery LLC");
        long id = paid.owner().policyId();
        CorrectCoverageService.Corrected toOct22 = correct(id, OCT_22); // a pending refund
        refundSettles(refund(toOct22.refunds().getFirst().displayId()));
        correct(id, OCT_8); // owes $28.00 again
        paymentDeclined(paid.owner(), "0002");
        paymentWaiting(paid.owner()); // a pending payment

        Statement s = service.forPolicy(id);

        assertThat(s.rows()).extracting(Row::kind).contains(EntryType.CHARGE, EntryType.REVERSAL,
                EntryType.PAYMENT, EntryType.REFUND);
        assertThat(pending(s)).hasSize(1);
        assertThat(s.rows()).isNotEmpty().allSatisfy(r -> {
            assertThat(r.trace()).as(r.id()).isNotNull();
            assertThat(r.trace().kind()).as(r.id()).isEqualTo(r.kind());
            assertThat(r.trace().maths().resultCents()).as(r.id()).isEqualTo(r.amountCents());
            assertThat(r.trace().cause()).as(r.id()).isNotNull();
            assertThat(r.trace().refs()).as(r.id()).isNotEmpty();
            if (!r.pending()) {
                assertThat(r.trace().refs()).as(r.id()).contains(new Ref(RefKind.LINE, r.id()));
            }
        });
    }
}
