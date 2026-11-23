package app.plainly.statement;

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
import app.plainly.ledger.LedgerQueryService;
import app.plainly.policy.AddCoverageService;
import app.plainly.policy.CancelPolicyCommand;
import app.plainly.policy.CancelPolicyService;
import app.plainly.policy.CorrectCoverageCommand;
import app.plainly.policy.CorrectCoverageService;
import app.plainly.policy.IssuePolicyService;
import app.plainly.statement.History.Action;
import app.plainly.statement.History.ActionKind;
import app.plainly.statement.Statement.Ref;
import app.plainly.statement.Statement.RefKind;
import app.plainly.statement.Statement.Row;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * B5 "History": the statement's rows grouped into one card per action. Worked numbers as in StatementServiceTest:
 * a Sep 1, 2026 → Sep 1, 2027 policy, base cover $1,200.00 paid, extra cover $730.00 a year from Oct 15 = $642.00
 * paid. Moving its start to Oct 8 undoes $642.00 and charges $656.00 (net +$14.00); moving it to Oct 22 charges
 * $628.00 (net −$14.00, refunded to card ending 4242).
 */
// covers AC2, AC3, AC4, AC5, AC6, AC8, T1
@IntegrationTest
@AutoConfigureMockMvc
class HistoryServiceTest {

    static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);
    static final LocalDate TODAY = LocalDate.of(2026, 11, 22); // plainly.today in IntegrationTest

    @Autowired HistoryService service;
    @Autowired StatementService statements;
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

    String ownerName(long policyId) {
        return statements.forPolicy(policyId).rows().stream()
                .filter(r -> r.kind() == EntryType.PAYMENT).findFirst().orElseThrow()
                .trace().cause().actorName();
    }

    String paymentDisplayId(String intentId) {
        return jdbc.sql("select display_id from payments where stripe_payment_intent_id = ?").param(intentId)
                .query(String.class).single();
    }

    Started paymentWaiting(Owner owner) throws Exception {
        Started started = PaymentTestData.startPayment(mvc, jdbc, owner);
        deliver(StripeTestSupport.intentProcessing(StripeTestSupport.newEventId(), started.intentId(),
                started.amountCents()));
        return started;
    }

    Started paymentDeclined(Owner owner, String last4) throws Exception {
        Started started = PaymentTestData.startPayment(mvc, jdbc, owner);
        deliver(StripeTestSupport.chargeFailed(StripeTestSupport.newEventId(), started.intentId(),
                started.amountCents(), last4, "Your card was declined."));
        return started;
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

    void refundFails(SentRefund r, String why) throws Exception {
        deliver(StripeTestSupport.refundFailed(StripeTestSupport.newEventId(), r.stripeRefundId(), r.id(),
                r.intentId(), r.amountCents(), why));
    }

    void deliver(String body) throws Exception {
        mvc.perform(StripeTestSupport.delivery(body)).andExpect(status().isOk());
    }

    static Action action(History h, String id) {
        return h.actions().stream().filter(a -> id.equals(a.id())).findFirst().orElseThrow();
    }

    static List<Action> ofKind(History h, ActionKind kind) {
        return h.actions().stream().filter(a -> a.kind() == kind).toList();
    }

    static long settledRowsSum(Action a) {
        return a.rows().stream().filter(r -> !r.pending()).mapToLong(Row::amountCents).sum();
    }

    // --- one card per action, oldest first, with who did it (AC2) ---

    @Test
    void everyChangeAndPaymentIsOneCardOldestFirstWithWhoDidIt() throws Exception {
        Paid paid = paid("One Card Each Bakery LLC");
        long id = paid.owner().policyId();

        History h = service.forPolicy(id);

        assertThat(h.actions()).extracting(Action::kind)
                .containsExactly(ActionKind.CHANGE, ActionKind.PAYMENT, ActionKind.CHANGE, ActionKind.PAYMENT);
        assertThat(h.actions()).extracting(Action::changeType).containsExactly("ISSUE", null, "ADD_COVERAGE", null);
        assertThat(h.actions()).extracting(Action::amountCents).containsExactly(120_000L, -120_000L, 64_200L, -64_200L);
        assertThat(h.actions()).extracting(Action::status)
                .containsExactly(null, History.Status.PAID, null, History.Status.PAID);
        assertThat(h.actions()).extracting(Action::pending).containsOnly(false);
        assertThat(h.actions()).extracting(Action::date).isSortedAccordingTo(Comparator.naturalOrder());
        assertThat(h.actions().get(0).id()).startsWith("PTX-");
        assertThat(h.actions().get(2).id()).startsWith("PTX-");
        assertThat(h.actions().get(3).id()).isEqualTo(paymentDisplayId(paid.extraIntentId()));
        assertThat(h.actions().get(0).actorName()).isEqualTo(brokerName());
        assertThat(h.actions().get(2).actorName()).isEqualTo(brokerName());
        assertThat(h.actions().get(1).actorName()).isEqualTo(ownerName(id)).isNotBlank();
        assertThat(h.actions().get(3).cardLast4()).isEqualTo("4242");
        assertThat(h.actions()).allSatisfy(a -> assertThat(a.rows()).as(a.id()).isNotEmpty());
        assertThat(h.businessName()).isEqualTo("One Card Each Bakery LLC");
        assertThat(h.policyNumber()).isNotBlank();
        assertThat(h.today()).isEqualTo(TODAY);
    }

    @Test
    void anUnknownPolicyIsNotFound() {
        assertThatThrownBy(() -> service.forPolicy(Long.MAX_VALUE)).isInstanceOf(NotFoundException.class);
    }

    // --- the money on each card, and the same numbers as the statement (AC3) ---

    @Test
    void settledCardsAddUpToTheLedgerBalanceAndTheStatementsLastBalance() throws Exception {
        Paid paid = paid("Adds Up Bakery LLC");
        long id = paid.owner().policyId();
        CorrectCoverageService.Corrected toOct22 = correct(id, OCT_22); // −$14.00, refunded
        refundSettles(refund(toOct22.refunds().getFirst().displayId()));
        correct(id, OCT_8); // +$28.00 owed
        paymentDeclined(paid.owner(), "0002");
        paymentWaiting(paid.owner()); // pending, not counted

        History h = service.forPolicy(id);
        Statement s = statements.forPolicy(id);

        long settled = h.actions().stream().filter(a -> !a.pending()).mapToLong(Action::amountCents).sum();
        List<Row> settledRows = s.rows().stream().filter(r -> !r.pending()).toList();
        assertThat(settled).isEqualTo(ledger.balance(id)).isEqualTo(settledRows.getLast().balanceAfterCents())
                .isEqualTo(2_800);
        assertThat(h.balanceCents()).isEqualTo(ledger.balance(id));
        assertThat(h.actions()).filteredOn(a -> !a.pending())
                .allSatisfy(a -> assertThat(a.amountCents()).as(a.id()).isEqualTo(settledRowsSum(a)));
        // every statement row sits on exactly one card, with the same numbers and trace
        assertThat(h.actions().stream().flatMap(a -> a.rows().stream()).toList())
                .containsExactlyInAnyOrderElementsOf(s.rows());
    }

    @Test
    void aPaymentWithTheBankIsAPendingCardOutsideTheBalance() throws Exception {
        Paid paid = paid("Pending Card Bakery LLC");
        long id = paid.owner().policyId();
        correct(id, OCT_8);
        Started waiting = paymentWaiting(paid.owner());

        Action a = action(service.forPolicy(id), paymentDisplayId(waiting.intentId()));

        assertThat(a.kind()).isEqualTo(ActionKind.PAYMENT);
        assertThat(a.pending()).isTrue();
        assertThat(a.status()).isEqualTo(History.Status.WAITING);
        assertThat(a.amountCents()).isEqualTo(-1_400);
        assertThat(a.date()).isEqualTo(TODAY);
        assertThat(a.rows()).singleElement().satisfies(r -> assertThat(r.pending()).isTrue());
    }

    // --- detail: refs and struck lines (AC4) ---

    @Test
    void aSettledRefundIsItsOwnCardWithItsStripeReferences() throws Exception {
        long id = paid("Refund Card Bakery LLC").owner().policyId();
        CorrectCoverageService.Corrected saved = correct(id, OCT_22);
        SentRefund sent = refund(saved.refunds().getFirst().displayId());
        refundSettles(sent);

        Action a = action(service.forPolicy(id), sent.displayId());

        assertThat(a.kind()).isEqualTo(ActionKind.REFUND);
        assertThat(a.status()).isEqualTo(History.Status.REFUND_SENT);
        assertThat(a.pending()).isFalse();
        assertThat(a.amountCents()).isEqualTo(1_400);
        assertThat(a.actorName()).isEqualTo(brokerName());
        assertThat(a.cardLast4()).isEqualTo("4242");
        assertThat(a.failureReason()).isNull();
        assertThat(a.rows()).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo(EntryType.REFUND);
            assertThat(r.trace().refs()).contains(new Ref(RefKind.STRIPE_REFUND, sent.stripeRefundId()));
        });
    }

    @Test
    void theLineACorrectionUndidIsMarkedUndoneOnTheCardItCameFrom() throws Exception {
        long id = paid("Struck Line Bakery LLC").owner().policyId();
        correct(id, OCT_8);

        History h = service.forPolicy(id);

        Action addCover = h.actions().stream().filter(a -> "ADD_COVERAGE".equals(a.changeType()))
                .findFirst().orElseThrow();
        assertThat(addCover.rows()).singleElement().satisfies(r -> assertThat(r.undoneOn()).isNotNull());
        assertThat(addCover.amountCents()).isEqualTo(64_200);
    }

    // --- the real state of money that went wrong (AC5) ---

    @Test
    void aRefundStripeFailedSaysSoWithStripesReason() throws Exception {
        long id = paid("Failed Refund Bakery LLC").owner().policyId();
        CorrectCoverageService.Corrected saved = correct(id, OCT_22);
        SentRefund sent = refund(saved.refunds().getFirst().displayId());
        refundFails(sent, "expired_or_canceled_card");

        Action a = action(service.forPolicy(id), sent.displayId());

        assertThat(a.kind()).isEqualTo(ActionKind.REFUND);
        assertThat(a.status()).isEqualTo(History.Status.REFUND_FAILED);
        assertThat(a.failureReason()).isEqualTo("expired_or_canceled_card");
        assertThat(a.pending()).isTrue();
        assertThat(a.amountCents()).isEqualTo(1_400);
        // the policyholder's statement still reads "on its way"
        assertThat(statements.forPolicy(id).rows()).filteredOn(r -> sent.displayId().equals(r.id()))
                .singleElement().satisfies(r -> assertThat(r.status()).isEqualTo(Statement.MoneyStatus.REFUND_ON_ITS_WAY));
    }

    @Test
    void aRefundNotYetConfirmedIsOnItsWay() throws Exception {
        long id = paid("Refund On Its Way Bakery LLC").owner().policyId();
        CorrectCoverageService.Corrected saved = correct(id, OCT_22);

        Action a = action(service.forPolicy(id), saved.refunds().getFirst().displayId());

        assertThat(a.status()).isEqualTo(History.Status.REFUND_ON_ITS_WAY);
        assertThat(a.pending()).isTrue();
        assertThat(a.failureReason()).isNull();
    }

    @Test
    void aDeclinedPaymentIsAZeroCardWithTheBanksReason() throws Exception {
        Paid paid = paid("Declined Card Bakery LLC");
        long id = paid.owner().policyId();
        correct(id, OCT_8);
        Started declined = paymentDeclined(paid.owner(), "0002");

        Action a = action(service.forPolicy(id), paymentDisplayId(declined.intentId()));

        assertThat(a.kind()).isEqualTo(ActionKind.PAYMENT);
        assertThat(a.status()).isEqualTo(History.Status.DECLINED);
        assertThat(a.amountCents()).isZero();
        assertThat(a.pending()).isFalse();
        assertThat(a.failureReason()).isEqualTo("Your card was declined.");
        assertThat(a.cardLast4()).isEqualTo("0002");
    }

    // --- the trial scenario: one correction card (AC6) ---

    @Test
    void theOct15ToOct8CorrectionIsOneCardNetFourteenDollars() throws Exception {
        long id = paid("Trial Scenario Bakery LLC").owner().policyId();
        CorrectCoverageService.Corrected saved = correct(id, OCT_8);

        History h = service.forPolicy(id);
        Action a = action(h, saved.transactionDisplayId());

        assertThat(a.kind()).isEqualTo(ActionKind.CHANGE);
        assertThat(a.changeType()).isEqualTo("CORRECT");
        assertThat(a.amountCents()).isEqualTo(1_400);
        assertThat(a.pending()).isFalse();
        assertThat(a.reason()).isEqualTo(REASON);
        assertThat(a.actorName()).isEqualTo(brokerName());
        assertThat(a.rows()).extracting(Row::kind).containsExactly(EntryType.REVERSAL, EntryType.CHARGE);
        assertThat(a.rows()).extracting(Row::amountCents).containsExactly(-64_200L, 65_600L);
        assertThat(a.correction()).isNotNull();
        assertThat(a.correction().netCents()).isEqualTo(1_400);
        assertThat(a.correction().dayDelta()).isEqualTo(7);
        assertThat(a.correction().newStart()).isEqualTo(OCT_8);
        // the same numbers the policyholder sees
        assertThat(a.correction()).isEqualTo(statements.forPolicy(id).corrections().getFirst());
    }

    @Test
    void aCancellationIsOneCardWithItsCreditsAndTheBrokersReason() throws Exception {
        long id = paid("Cancel Card Bakery LLC").owner().policyId();
        String why = "The bakery closed for good.";
        CancelPolicyService.Cancelled cancelled = canceller.cancel(id, new CancelPolicyCommand(DEC_1, why),
                CorrectionTestData.brokerId(jdbc));

        Action a = action(service.forPolicy(id), cancelled.transactionDisplayId());

        assertThat(a.changeType()).isEqualTo("CANCEL");
        assertThat(a.reason()).isEqualTo(why);
        assertThat(a.rows()).isNotEmpty().allSatisfy(r -> assertThat(r.kind()).isEqualTo(EntryType.CREDIT));
        assertThat(a.amountCents()).isNegative().isEqualTo(settledRowsSum(a));
    }

    // --- a second correction adds a card and changes nothing earlier (AC8) ---

    @Test
    void correctingAgainAddsANewCardAndLeavesEarlierCardsAlone() throws Exception {
        long id = paid("Corrected Twice Bakery LLC").owner().policyId();
        CorrectCoverageService.Corrected first = correct(id, OCT_8);
        History before = service.forPolicy(id);

        CorrectCoverageService.Corrected second = correct(id, OCT_22);
        History after = service.forPolicy(id);

        assertThat(after.actions()).filteredOn(a -> "CORRECT".equals(a.changeType())).extracting(Action::id)
                .containsExactly(first.transactionDisplayId(), second.transactionDisplayId());
        Action again = action(after, second.transactionDisplayId());
        assertThat(again.amountCents()).isEqualTo(-2_800); // −$656.00 + $628.00
        // earlier cards keep their money; only the line the new correction undid gains an "undone" mark
        for (Action earlier : before.actions()) {
            assertThat(action(after, earlier.id()).amountCents()).as(earlier.id()).isEqualTo(earlier.amountCents());
        }
        assertThat(action(after, first.transactionDisplayId()).rows())
                .filteredOn(r -> r.kind() == EntryType.CHARGE).singleElement()
                .satisfies(r -> assertThat(r.undoneOn()).isNotNull());
    }
}
