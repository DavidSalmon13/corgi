package app.plainly.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.FakeStripeGateway;
import app.plainly.IntegrationTest;
import app.plainly.PaymentTestData;
import app.plainly.PaymentTestData.Owner;
import app.plainly.PaymentTestData.Started;
import app.plainly.StripeTestSupport;
import app.plainly.TestData;
import app.plainly.billing.PlannedRefund;
import app.plainly.billing.RefundStarted;
import app.plainly.common.NotFoundException;
import app.plainly.common.ValidationException;
import app.plainly.ledger.EntryType;
import app.plainly.ledger.LedgerLine;
import app.plainly.ledger.LedgerQueryService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The trial scenario and its edges: the extra cover's start was recorded wrong, and the broker fixes it after the
 * customer paid. Worked numbers: a Sep 1, 2026 → Sep 1, 2027 policy (365 days), extra cover at $730.00 a year from
 * Oct 15 = $730.00 × 321 ÷ 365 = $642.00.
 */
// covers AC3, AC4, AC5, AC6, AC7, AC8, AC9, T1
@IntegrationTest
@AutoConfigureMockMvc
class CorrectCoverageServiceTest {

    static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    static final LocalDate POLICY_END = LocalDate.of(2027, 9, 1);
    static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
    static final LocalDate OCT_8 = LocalDate.of(2026, 10, 8);
    static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);
    static final LocalDate OCT_22 = LocalDate.of(2026, 10, 22);
    static final LocalDate OCT_29 = LocalDate.of(2026, 10, 29);
    static final LocalDate JUN_1 = LocalDate.of(2027, 6, 1);
    static final LocalDate TODAY = LocalDate.of(2026, 11, 22); // plainly.today in IntegrationTest
    static final String REASON = "Customer's signed request was dated Oct 8; cover was entered a week late.";

    @Autowired CorrectCoverageService service;
    @Autowired AddCoverageService adder;
    @Autowired IssuePolicyService issuer;
    @Autowired LedgerQueryService ledger;
    @Autowired FakeStripeGateway stripe;
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    // --- scenario builders ---

    long brokerId() {
        return jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
    }

    /** A signed-up owner whose base-cover-only policy (Sep 1, 2026 → Sep 1, 2027) has $1,200.00 due. */
    Owner owner() throws Exception {
        return PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Juniper & Rye Bakery LLC");
    }

    void addExtraCover(Owner owner, LocalDate start) {
        adder.add(owner.policyId(), new AddCoverageCommand(CoverKind.PRODUCT_LIABILITY, start), brokerId());
    }

    /** Pays the whole balance due by card, settled by a signed charge.succeeded. Returns the payment id. */
    long payInFull(Owner owner, String last4) throws Exception {
        Started started = PaymentTestData.startPayment(mvc, jdbc, owner);
        mvc.perform(StripeTestSupport.delivery(StripeTestSupport.chargeSucceeded(StripeTestSupport.newEventId(),
                started.intentId(), started.amountCents(), last4, "pm_" + last4))).andExpect(status().isOk());
        assertThat(PaymentTestData.paymentStatus(jdbc, started.paymentId())).isEqualTo("SUCCEEDED");
        return started.paymentId();
    }

    /** The trial's starting point: base cover paid, extra cover from Oct 15 added and paid ($642.00, card 4242). */
    record Paid(Owner owner, long basePayment, long extraPayment) {}

    Paid paidWithExtraCoverFromOct15() throws Exception {
        Owner owner = owner();
        long base = payInFull(owner, "4242");
        addExtraCover(owner, OCT_15);
        long extra = payInFull(owner, "4242");
        assertThat(ledger.balance(owner.policyId())).isZero();
        return new Paid(owner, base, extra);
    }

    static CorrectCoverageCommand startOn(LocalDate start) {
        return new CorrectCoverageCommand(start, REASON);
    }

    String paymentDisplayId(long paymentId) {
        return jdbc.sql("select display_id from payments where id = ?").param(paymentId).query(String.class).single();
    }

    LedgerLine liveExtraCharge(long policyId) {
        List<LedgerLine> lines = ledger.lines(policyId);
        return lines.stream()
                .filter(l -> l.type() == EntryType.CHARGE && l.description().startsWith("Product liability"))
                .filter(l -> l.reversedByDisplayId() == null)
                .reduce((a, b) -> b).orElseThrow();
    }

    // --- preview: the customer owes more (AC3) ---

    @Test
    void movingTheStartAWeekEarlierUndoes642AndCharges656SoThe14DollarsIsDue() throws Exception {
        Paid paid = paidWithExtraCoverFromOct15();
        long id = paid.owner().policyId();
        LedgerLine original = liveExtraCharge(id);

        CorrectionPlan plan = service.preview(id, OCT_8);

        assertThat(plan.policyId()).isEqualTo(id);
        assertThat(plan.oldStart()).isEqualTo(OCT_15);
        assertThat(plan.newStart()).isEqualTo(OCT_8);

        assertThat(plan.undo().entryDisplayId()).isEqualTo(original.displayId());
        assertThat(plan.undo().label()).isEqualTo("Product liability endorsement from Oct 15");
        assertThat(plan.undo().amountCents()).isEqualTo(-64_200);
        assertThat(plan.undo().calc().annualCents()).isEqualTo(73_000);
        assertThat(plan.undo().calc().days()).isEqualTo(321);
        assertThat(plan.undo().calc().termDays()).isEqualTo(365);

        assertThat(plan.rebill().label()).isEqualTo("Product liability endorsement from Oct 8");
        assertThat(plan.rebill().from()).isEqualTo(OCT_8);
        assertThat(plan.rebill().to()).isEqualTo(POLICY_END);
        assertThat(plan.rebill().calc().days()).isEqualTo(328);
        assertThat(plan.rebill().calc().termDays()).isEqualTo(365);
        assertThat(plan.rebill().calc().amountCents()).isEqualTo(65_600);

        assertThat(plan.netCents()).isEqualTo(1_400);
        assertThat(plan.balanceAfterCents()).isEqualTo(1_400);
        assertThat(plan.refunds()).isEmpty();
        assertThat(plan.refundTotalCents()).isZero();
    }

    // --- preview: we owe the customer (AC4, AC5) ---

    @Test
    void movingTheStartAWeekLaterRefunds14DollarsToTheLatestPayment() throws Exception {
        Paid paid = paidWithExtraCoverFromOct15();

        CorrectionPlan plan = service.preview(paid.owner().policyId(), OCT_22);

        assertThat(plan.rebill().calc().days()).isEqualTo(314);
        assertThat(plan.rebill().calc().amountCents()).isEqualTo(62_800);
        assertThat(plan.netCents()).isEqualTo(-1_400);
        assertThat(plan.balanceAfterCents()).isEqualTo(-1_400);
        assertThat(plan.refunds()).containsExactly(new PlannedRefund(paid.extraPayment(),
                paymentDisplayId(paid.extraPayment()), "4242", 1_400));
        assertThat(plan.refundTotalCents()).isEqualTo(1_400);
    }

    @Test
    void aCreditFirstPaysOffAnUnpaidBalanceSoNothingIsRefunded() throws Exception {
        Owner owner = owner();
        payInFull(owner, "4242");
        addExtraCover(owner, OCT_15); // $642.00 still owed

        CorrectionPlan plan = service.preview(owner.policyId(), OCT_22);

        assertThat(plan.netCents()).isEqualTo(-1_400);
        assertThat(plan.balanceAfterCents()).isEqualTo(62_800);
        assertThat(plan.refunds()).isEmpty();
    }

    @Test
    void aCreditLargerThanTheUnpaidBalanceRefundsOnlyTheRest() throws Exception {
        Paid paid = paidWithExtraCoverFromOct15();
        PaymentTestData.addCharge(jdbc, paid.owner().policyId(), 500); // $5.00 owed again

        CorrectionPlan plan = service.preview(paid.owner().policyId(), OCT_22);

        assertThat(plan.netCents()).isEqualTo(-1_400);
        assertThat(plan.balanceAfterCents()).isEqualTo(-900);
        assertThat(plan.refunds()).singleElement().satisfies(r -> {
            assertThat(r.paymentId()).isEqualTo(paid.extraPayment());
            assertThat(r.amountCents()).isEqualTo(900);
        });
    }

    @Test
    void aRefundLargerThanTheLatestPaymentSpillsIntoTheOneBefore() throws Exception {
        Paid paid = paidWithExtraCoverFromOct15();
        long policyId = paid.owner().policyId();
        PaymentTestData.addCharge(jdbc, policyId, 1_000);
        long small = payInFull(paid.owner(), "0005"); // the latest payment collected only $10.00

        // Oct 15 → Jun 1, 2027: $730.00 × 92 ÷ 365 = $184.00, so $458.00 comes back
        CorrectionPlan plan = service.preview(policyId, JUN_1);

        assertThat(plan.rebill().calc().days()).isEqualTo(92);
        assertThat(plan.rebill().calc().amountCents()).isEqualTo(18_400);
        assertThat(plan.netCents()).isEqualTo(-45_800);
        assertThat(plan.refunds()).containsExactly(
                new PlannedRefund(small, paymentDisplayId(small), "0005", 1_000),
                new PlannedRefund(paid.extraPayment(), paymentDisplayId(paid.extraPayment()), "4242", 44_800));
        assertThat(plan.refundTotalCents()).isEqualTo(45_800);
    }

    @Test
    void moneyAlreadyBeingRefundedFromAPaymentIsNotRefundedFromItAgain() throws Exception {
        Paid paid = paidWithExtraCoverFromOct15();
        long policyId = paid.owner().policyId();
        PaymentTestData.addCharge(jdbc, policyId, 1_000);
        long small = payInFull(paid.owner(), "0005");

        // First fix: Oct 15 → Oct 22 refunds $14.00: all $10.00 of the latest payment, then $4.00 of the one before.
        CorrectCoverageService.Corrected first = service.correct(policyId, startOn(OCT_22), brokerId());
        assertThat(first.refunds()).extracting(RefundStarted::paymentId,
                RefundStarted::amountCents)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(small, 1_000L),
                        org.assertj.core.groups.Tuple.tuple(paid.extraPayment(), 400L));

        // Second fix: Oct 22 → Oct 29 owes $14.00 more back. The $10.00 payment has nothing left to give.
        CorrectionPlan second = service.preview(policyId, OCT_29);
        assertThat(second.rebill().calc().amountCents()).isEqualTo(61_400);
        assertThat(second.refunds()).containsExactly(new PlannedRefund(paid.extraPayment(),
                paymentDisplayId(paid.extraPayment()), "4242", 1_400));
    }

    @Test
    void aPolicyIssuedWithExtraCoverUndoesTheIssueCharge() throws Exception {
        long id = issuer.issue(new IssuePolicyCommand("Both Covers LLC", "a shop", "Bo Owner",
                TestData.uniqueEmail("bo"), SEP_1, true), brokerId()).policyId();
        LedgerLine issued = liveExtraCharge(id);

        CorrectionPlan plan = service.preview(id, OCT_1); // cover really started a month in

        assertThat(plan.undo().entryDisplayId()).isEqualTo(issued.displayId());
        assertThat(plan.undo().amountCents()).isEqualTo(-73_000);
        assertThat(plan.rebill().calc().days()).isEqualTo(335);
        assertThat(plan.rebill().calc().amountCents()).isEqualTo(67_000);
        assertThat(plan.netCents()).isEqualTo(-6_000);
        assertThat(plan.refunds()).isEmpty(); // nothing was paid, so the credit only lowers what's owed
    }

    @Test
    void previewSavesNothing() throws Exception {
        Paid paid = paidWithExtraCoverFromOct15();
        long before = writes();
        service.preview(paid.owner().policyId(), OCT_8);
        service.preview(paid.owner().policyId(), OCT_22);
        assertThat(writes()).isEqualTo(before);
    }

    // --- validation (AC6) ---

    @Test
    void aMissingStartDateIsRejected() throws Exception {
        long id = paidWithExtraCoverFromOct15().owner().policyId();
        assertFieldError(() -> service.preview(id, null), "startDate", CorrectCoverageService.NO_START);
    }

    @Test
    void aStartBeforeThePolicyIsRejected() throws Exception {
        long id = paidWithExtraCoverFromOct15().owner().policyId();
        assertFieldError(() -> service.preview(id, SEP_1.minusDays(1)),
                "startDate", "The effective date can't be before the policy period starts (Sep 1, 2026).");
    }

    @Test
    void aStartOnOrAfterThePolicyEndIsRejected() throws Exception {
        long id = paidWithExtraCoverFromOct15().owner().policyId();
        assertFieldError(() -> service.preview(id, POLICY_END),
                "startDate", "The effective date has to be before the policy period ends (Sep 1, 2027).");
        assertFieldError(() -> service.preview(id, POLICY_END.plusDays(3)),
                "startDate", "The effective date has to be before the policy period ends (Sep 1, 2027).");
    }

    @Test
    void theSameStartIsNotACorrection() throws Exception {
        long id = paidWithExtraCoverFromOct15().owner().policyId();
        assertFieldError(() -> service.preview(id, OCT_15), "startDate", CorrectCoverageService.SAME_START);
    }

    @Test
    void savingNeedsAReasonTheCustomerCanRead() throws Exception {
        long id = paidWithExtraCoverFromOct15().owner().policyId();
        assertFieldError(() -> service.correct(id, new CorrectCoverageCommand(OCT_8, null), brokerId()),
                "reason", CorrectCoverageService.NO_REASON);
        assertFieldError(() -> service.correct(id, new CorrectCoverageCommand(OCT_8, "   "), brokerId()),
                "reason", CorrectCoverageService.NO_REASON);
        assertFieldError(() -> service.correct(id, new CorrectCoverageCommand(OCT_8, "x".repeat(501)), brokerId()),
                "reason", CorrectCoverageService.REASON_TOO_LONG);
    }

    @Test
    void aReasonOfExactly500CharactersIsFine() throws Exception {
        long id = paidWithExtraCoverFromOct15().owner().policyId();
        CorrectCoverageService.Corrected saved =
                service.correct(id, new CorrectCoverageCommand(OCT_8, "x".repeat(500)), brokerId());
        assertThat(saved.netCents()).isEqualTo(1_400);
    }

    @Test
    void savingReportsEveryFieldToFixAtOnce() throws Exception {
        long id = paidWithExtraCoverFromOct15().owner().policyId();
        assertThatThrownBy(() -> service.correct(id, new CorrectCoverageCommand(null, ""), brokerId()))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.fields()).containsExactly(
                        Map.entry("startDate", CorrectCoverageService.NO_START),
                        Map.entry("reason", CorrectCoverageService.NO_REASON)));
    }

    @Test
    void aPolicyWithoutExtraCoverHasNothingToCorrect() throws Exception {
        long id = owner().policyId();
        assertFieldError(() -> service.preview(id, OCT_8), "policy", CorrectCoverageService.NO_EXTRA_COVER);
        assertFieldError(() -> service.correct(id, startOn(OCT_8), brokerId()),
                "policy", CorrectCoverageService.NO_EXTRA_COVER);
    }

    @Test
    void aCancelledPolicyCannotBeCorrected() throws Exception {
        long id = paidWithExtraCoverFromOct15().owner().policyId();
        jdbc.sql("update policies set status = 'CANCELLED', cancelled_on = date '2026-12-01' where id = ?").param(id).update();
        assertFieldError(() -> service.preview(id, OCT_8), "policy", CorrectCoverageService.CANCELLED);
        assertFieldError(() -> service.correct(id, startOn(OCT_8), brokerId()), "policy", CorrectCoverageService.CANCELLED);
    }

    @Test
    void aPaymentStillWithTheBankBlocksACorrection() throws Exception {
        Owner owner = owner();
        payInFull(owner, "4242");
        addExtraCover(owner, OCT_15);
        Started waiting = PaymentTestData.startPayment(mvc, jdbc, owner);
        mvc.perform(StripeTestSupport.delivery(StripeTestSupport.intentProcessing(StripeTestSupport.newEventId(),
                waiting.intentId(), waiting.amountCents()))).andExpect(status().isOk());

        assertFieldError(() -> service.preview(owner.policyId(), OCT_8), "policy", CorrectCoverageService.PAYMENT_WAITING);
        assertFieldError(() -> service.correct(owner.policyId(), startOn(OCT_8), brokerId()),
                "policy", CorrectCoverageService.PAYMENT_WAITING);
    }

    @Test
    void anUnknownPolicyIsNotFound() {
        assertThatThrownBy(() -> service.preview(Long.MAX_VALUE, OCT_8)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.correct(Long.MAX_VALUE, startOn(OCT_8), brokerId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void anInvalidCorrectionSavesNothing() throws Exception {
        long id = paidWithExtraCoverFromOct15().owner().policyId();
        long before = writes();
        assertThatThrownBy(() -> service.correct(id, startOn(OCT_15), brokerId())).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.correct(id, new CorrectCoverageCommand(OCT_8, ""), brokerId()))
                .isInstanceOf(ValidationException.class);
        assertThat(writes()).isEqualTo(before);
    }

    // --- saving (AC7, AC8) ---

    @Test
    void savingUndoesTheOldChargeAddsTheCorrectedOneAndReplacesTheCover() throws Exception {
        Paid paid = paidWithExtraCoverFromOct15();
        long id = paid.owner().policyId();
        LedgerLine original = liveExtraCharge(id);
        long oldCoverage = original.coverageId();
        CorrectionPlan preview = service.preview(id, OCT_8);

        CorrectCoverageService.Corrected saved = service.correct(id, startOn(OCT_8), brokerId());

        assertThat(saved.policyId()).isEqualTo(id);
        assertThat(saved.transactionDisplayId()).matches("PTX-\\d{4,}");
        assertThat(saved.netCents()).isEqualTo(preview.netCents()).isEqualTo(1_400);
        assertThat(saved.balanceCents()).isEqualTo(1_400).isEqualTo(ledger.balance(id));
        assertThat(saved.refunds()).isEmpty();

        var txs = jdbc.sql("""
                select display_id, effective_date, recorded_on, reason, created_by from policy_transactions
                where policy_id = ? and type = 'CORRECT'""")
                .param(id)
                .query((rs, n) -> List.of(rs.getString(1), rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate(),
                        rs.getString(4), rs.getLong(5)))
                .list();
        assertThat(txs).containsExactly(List.of(saved.transactionDisplayId(), OCT_8, TODAY, REASON, brokerId()));

        var covers = jdbc.sql("""
                select id, effective_from, effective_to, replaced_by_coverage_id from coverages
                where policy_id = ? and kind = 'PRODUCT_LIABILITY' order by id""")
                .param(id).query().listOfRows();
        assertThat(covers).hasSize(2);
        long newCoverage = ((Number) covers.get(1).get("id")).longValue();
        assertThat(((Number) covers.get(0).get("id")).longValue()).isEqualTo(oldCoverage);
        assertThat(((Number) covers.get(0).get("replaced_by_coverage_id")).longValue()).isEqualTo(newCoverage);
        assertThat(covers.get(1).get("effective_from").toString()).isEqualTo("2026-10-08");
        assertThat(covers.get(1).get("effective_to").toString()).isEqualTo("2027-09-01");
        assertThat(covers.get(1).get("replaced_by_coverage_id")).isNull();

        List<LedgerLine> correction = ledger.lines(id).stream()
                .filter(l -> saved.transactionDisplayId().equals(l.policyTransactionDisplayId())).toList();
        assertThat(correction).hasSize(2);
        assertThat(correction.get(0)).satisfies(undo -> {
            assertThat(undo.displayId()).isEqualTo(saved.undoDisplayId());
            assertThat(undo.type()).isEqualTo(EntryType.REVERSAL);
            assertThat(undo.entryDate()).isEqualTo(TODAY);
            assertThat(undo.amountCents()).isEqualTo(-64_200);
            assertThat(undo.reversesDisplayId()).isEqualTo(original.displayId());
            assertThat(undo.coverageId()).isEqualTo(oldCoverage);
        });
        assertThat(correction.get(1)).satisfies(rebill -> {
            assertThat(rebill.displayId()).isEqualTo(saved.rebillDisplayId());
            assertThat(rebill.type()).isEqualTo(EntryType.CHARGE);
            assertThat(rebill.entryDate()).isEqualTo(TODAY);
            assertThat(rebill.description()).isEqualTo("Product liability endorsement from Oct 8");
            assertThat(rebill.amountCents()).isEqualTo(65_600);
            assertThat(rebill.coverageId()).isEqualTo(newCoverage);
            assertThat(rebill.calcAnnualCents()).isEqualTo(73_000L);
            assertThat(rebill.calcDays()).isEqualTo(328);
            assertThat(rebill.calcTermDays()).isEqualTo(365);
            assertThat(rebill.coverFrom()).isEqualTo(OCT_8);
            assertThat(rebill.coverTo()).isEqualTo(POLICY_END);
        });

        // The original line is still there, unchanged, and now marked as undone.
        LedgerLine stillThere = ledger.lines(id).stream()
                .filter(l -> l.displayId().equals(original.displayId())).findFirst().orElseThrow();
        assertThat(stillThere.amountCents()).isEqualTo(64_200);
        assertThat(stillThere.reversedByDisplayId()).isEqualTo(saved.undoDisplayId());
    }

    @Test
    void savingACreditRecordsTheRefundsThePreviewShowed() throws Exception {
        Paid paid = paidWithExtraCoverFromOct15();
        long id = paid.owner().policyId();
        CorrectionPlan preview = service.preview(id, OCT_22);

        CorrectCoverageService.Corrected saved = service.correct(id, startOn(OCT_22), brokerId());

        assertThat(saved.netCents()).isEqualTo(-1_400);
        assertThat(saved.balanceCents()).isEqualTo(-1_400); // owed back until the refund settles by webhook
        assertThat(saved.refunds()).singleElement().satisfies(r -> {
            assertThat(r.displayId()).matches("RF-\\d{4,}");
            assertThat(r.paymentId()).isEqualTo(preview.refunds().getFirst().paymentId());
            assertThat(r.amountCents()).isEqualTo(1_400);
        });
    }

    @Test
    void aSecondCorrectionUndoesTheCorrectedLineNotTheOriginal() throws Exception {
        Paid paid = paidWithExtraCoverFromOct15();
        long id = paid.owner().policyId();
        CorrectCoverageService.Corrected first = service.correct(id, startOn(OCT_8), brokerId());

        CorrectionPlan plan = service.preview(id, OCT_1);
        assertThat(plan.oldStart()).isEqualTo(OCT_8);
        assertThat(plan.undo().entryDisplayId()).isEqualTo(first.rebillDisplayId());
        assertThat(plan.undo().amountCents()).isEqualTo(-65_600);
        assertThat(plan.rebill().calc().amountCents()).isEqualTo(67_000);

        CorrectCoverageService.Corrected second = service.correct(id, startOn(OCT_1), brokerId());
        assertThat(second.netCents()).isEqualTo(1_400);
        assertThat(ledger.balance(id)).isEqualTo(2_800);
        assertThat(jdbc.sql("""
                select count(*) from coverages where policy_id = ? and kind = 'PRODUCT_LIABILITY'
                and replaced_by_coverage_id is null""").param(id).query(Long.class).single()).isEqualTo(1);
    }

    @Test
    void twoSimultaneousCorrectionsSaveOnce() throws Exception {
        Paid paid = paidWithExtraCoverFromOct15();
        long id = paid.owner().policyId();
        long broker = brokerId();
        CountDownLatch go = new CountDownLatch(1);
        Callable<CorrectCoverageService.Corrected> correct = () -> {
            go.await();
            return service.correct(id, startOn(OCT_8), broker);
        };

        List<Future<CorrectCoverageService.Corrected>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            results.add(pool.submit(correct));
            results.add(pool.submit(correct));
            go.countDown();
        }

        int saved = 0;
        List<Throwable> rejected = new ArrayList<>();
        for (Future<CorrectCoverageService.Corrected> r : results) {
            try {
                r.get();
                saved++;
            } catch (ExecutionException e) {
                rejected.add(e.getCause());
            }
        }
        assertThat(saved).isEqualTo(1);
        assertThat(rejected).singleElement().isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.fields()).containsExactly(Map.entry("startDate", CorrectCoverageService.SAME_START)));
        assertThat(jdbc.sql("select count(*) from policy_transactions where policy_id = ? and type = 'CORRECT'")
                .param(id).query(Long.class).single()).isEqualTo(1);
        assertThat(ledger.balance(id)).isEqualTo(1_400);
    }

    // --- an old pay page can't pay the old amount (AC9) ---

    @Test
    void savingCancelsAnUnpaidPaymentForTheOldAmount() throws Exception {
        Owner owner = owner();
        payInFull(owner, "4242");
        addExtraCover(owner, OCT_15);
        Started open = PaymentTestData.startPayment(mvc, jdbc, owner); // $642.00, opened but not paid

        service.correct(owner.policyId(), startOn(OCT_8), brokerId());

        assertThat(stripe.statusOf(open.intentId())).isEqualTo("canceled");
        assertThat(jdbc.sql("select stripe_canceled_at is not null from payments where id = ?")
                .param(open.paymentId()).query(Boolean.class).single()).isTrue();
    }

    // --- helpers ---

    private static void assertFieldError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String field,
            String message) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.fields()).containsOnlyKeys(field).containsEntry(field, message));
    }

    private long writes() {
        return jdbc.sql("""
                select (select count(*) from coverages) + (select count(*) from policy_transactions)
                     + (select count(*) from ledger_entries) + (select count(*) from payments)""")
                .query(Long.class).single();
    }
}
