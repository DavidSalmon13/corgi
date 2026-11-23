package app.plainly.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.FakeStripeGateway;
import app.plainly.IntegrationTest;
import app.plainly.MePaths;
import app.plainly.PaymentTestData;
import app.plainly.PaymentTestData.Owner;
import app.plainly.PaymentTestData.Started;
import app.plainly.StripeTestSupport;
import app.plainly.billing.PlannedRefund;
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
 * The spec's worked example and its edges. A Sep 1, 2026 → Sep 1, 2027 policy (365 days): base cover $1,200.00 a
 * year, paid; extra cover $730.00 a year, added from Oct 15 and paid ($642.00), then corrected to Oct 8 ($656.00), so
 * $14.00 is still owed. Cancelled from Dec 1, 2026: 91 days used, 274 unused.
 */
// covers AC3, AC4, AC5, AC6, AC7, AC8, T5
@IntegrationTest
@AutoConfigureMockMvc
class CancelPolicyServiceTest {

    static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    static final LocalDate POLICY_END = LocalDate.of(2027, 9, 1);
    static final LocalDate OCT_8 = LocalDate.of(2026, 10, 8);
    static final LocalDate OCT_15 = LocalDate.of(2026, 10, 15);
    static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);
    static final LocalDate JAN_1 = LocalDate.of(2027, 1, 1);
    static final LocalDate TODAY = LocalDate.of(2026, 11, 22); // plainly.today in IntegrationTest
    static final String REASON = "Closing the bakery at the end of November.";

    @Autowired CancelPolicyService service;
    @Autowired RemoveCoverService remover;
    @Autowired CorrectCoverageService corrector;
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

    long payInFull(Owner owner) throws Exception {
        Started started = PaymentTestData.startPayment(mvc, jdbc, owner);
        mvc.perform(StripeTestSupport.delivery(StripeTestSupport.chargeSucceeded(StripeTestSupport.newEventId(),
                started.intentId(), started.amountCents(), "4242", "pm_4242"))).andExpect(status().isOk());
        assertThat(PaymentTestData.paymentStatus(jdbc, started.paymentId())).isEqualTo("SUCCEEDED");
        return started.paymentId();
    }

    /** The worked example: everything paid, then the extra cover's start corrected a week earlier ($14.00 owed). */
    record Example(Owner owner, long basePayment, long extraPayment) {}

    Example workedExample() throws Exception {
        Owner owner = owner();
        long base = payInFull(owner);
        adder.add(owner.policyId(), new AddCoverageCommand(CoverKind.PRODUCT_LIABILITY, OCT_15), brokerId());
        long extra = payInFull(owner);
        corrector.correct(owner.policyId(), new CorrectCoverageCommand(OCT_8, "Signed request was dated Oct 8."),
                brokerId());
        assertThat(ledger.balance(owner.policyId())).isEqualTo(1_400);
        return new Example(owner, base, extra);
    }

    static CancelPolicyCommand from(LocalDate day) {
        return new CancelPolicyCommand(day, REASON);
    }

    String paymentDisplayId(long paymentId) {
        return jdbc.sql("select display_id from payments where id = ?").param(paymentId).query(String.class).single();
    }

    long coverageId(long policyId, CoverKind kind) {
        return jdbc.sql("""
                select id from coverages where policy_id = ? and kind = ? and replaced_by_coverage_id is null""")
                .params(policyId, kind.name()).query(Long.class).single();
    }

    // --- preview (AC3) ---

    @Test
    void cancellingTheWorkedExampleFromDec1CreditsBothCoversKeepsBack14AndRefundsTheRest() throws Exception {
        Example ex = workedExample();
        long id = ex.owner().policyId();

        CancelPolicyPlan plan = service.preview(id, DEC_1);

        assertThat(plan.policyId()).isEqualTo(id);
        assertThat(plan.cancelDate()).isEqualTo(DEC_1);
        assertThat(plan.usedDays()).isEqualTo(91);
        assertThat(plan.unusedDays()).isEqualTo(274);
        assertThat(plan.termDays()).isEqualTo(365);

        assertThat(plan.credits()).hasSize(2);
        assertThat(plan.credits().get(0)).satisfies(base -> {
            assertThat(base.coverageId()).isEqualTo(coverageId(id, CoverKind.BASE));
            assertThat(base.kind()).isEqualTo(CoverKind.BASE);
            assertThat(base.label()).isEqualTo("General liability return premium");
            assertThat(base.from()).isEqualTo(DEC_1);
            assertThat(base.to()).isEqualTo(POLICY_END);
            assertThat(base.calc().annualCents()).isEqualTo(120_000);
            assertThat(base.calc().days()).isEqualTo(274);
            assertThat(base.calc().termDays()).isEqualTo(365);
            assertThat(base.amountCents()).isEqualTo(-90_082);
        });
        assertThat(plan.credits().get(1)).satisfies(extra -> {
            assertThat(extra.coverageId()).isEqualTo(coverageId(id, CoverKind.PRODUCT_LIABILITY));
            assertThat(extra.label()).isEqualTo("Product liability return premium");
            assertThat(extra.calc().annualCents()).isEqualTo(73_000);
            assertThat(extra.calc().days()).isEqualTo(274);
            assertThat(extra.amountCents()).isEqualTo(-54_800);
        });
        assertThat(plan.creditTotalCents()).isEqualTo(-144_882);

        assertThat(plan.balanceBeforeCents()).isEqualTo(1_400);
        assertThat(plan.keptBackCents()).isEqualTo(1_400);
        assertThat(plan.keptBackFor()).isEqualTo("CORRECT");
        assertThat(plan.balanceAfterCents()).isEqualTo(-143_482);
        assertThat(plan.stillOwedCents()).isZero();

        assertThat(plan.refunds()).containsExactly(
                new PlannedRefund(ex.extraPayment(), paymentDisplayId(ex.extraPayment()), "4242", 64_200),
                new PlannedRefund(ex.basePayment(), paymentDisplayId(ex.basePayment()), "4242", 79_282));
        assertThat(plan.refundTotalCents()).isEqualTo(143_482);
    }

    @Test
    void previewSavesNothing() throws Exception {
        long id = workedExample().owner().policyId();
        long before = writes();
        service.preview(id, DEC_1);
        service.preview(id, SEP_1);
        assertThat(writes()).isEqualTo(before);
        assertThat(jdbc.sql("select status from policies where id = ?").param(id).query(String.class).single())
                .isEqualTo("ACTIVE");
    }

    // --- which days each cover gives back (AC4) ---

    @Test
    void aCoverStartingAfterTheCancelDayIsCreditedInFull() throws Exception {
        Owner owner = owner();
        payInFull(owner);
        adder.add(owner.policyId(), new AddCoverageCommand(CoverKind.PRODUCT_LIABILITY, JAN_1), brokerId());
        LedgerLine extraCharge = ledger.lines(owner.policyId()).stream()
                .filter(l -> l.type() == EntryType.CHARGE && l.description().startsWith("Product liability"))
                .findFirst().orElseThrow();

        CancelPolicyPlan plan = service.preview(owner.policyId(), DEC_1);

        CancelPolicyPlan.Credit extra = plan.credits().get(1);
        assertThat(extra.from()).isEqualTo(JAN_1);
        assertThat(extra.calc().days()).isEqualTo(243);
        assertThat(extra.amountCents()).isEqualTo(-extraCharge.amountCents());
    }

    @Test
    void cancellingOnTheStartDayCreditsEverythingAndRefundsThePaymentInFull() throws Exception {
        Owner owner = owner();
        long paid = payInFull(owner);

        CancelPolicyPlan plan = service.preview(owner.policyId(), SEP_1);

        assertThat(plan.usedDays()).isZero();
        assertThat(plan.unusedDays()).isEqualTo(365);
        assertThat(plan.credits()).singleElement().satisfies(c -> assertThat(c.amountCents()).isEqualTo(-120_000));
        assertThat(plan.keptBackCents()).isZero();
        assertThat(plan.keptBackFor()).isNull();
        assertThat(plan.refunds()).containsExactly(new PlannedRefund(paid, paymentDisplayId(paid), "4242", 120_000));
    }

    // covers AC3, AC11, T5, T7 — "kept back" names a change only when that change alone left the balance owing
    @Test
    void whatIsKeptBackIsNotPinnedOnTheLatestChangeWhenAnEarlierOneIsAlsoUnpaid() throws Exception {
        Owner owner = owner();
        payInFull(owner); // the first year is paid
        adder.add(owner.policyId(), new AddCoverageCommand(CoverKind.PRODUCT_LIABILITY, OCT_15), brokerId());
        // the extra cover is never paid, then its start is corrected: CORRECT is newest, but most of it is extra cover
        corrector.correct(owner.policyId(), new CorrectCoverageCommand(OCT_8, "Signed request was dated Oct 8."),
                brokerId());
        assertThat(ledger.balance(owner.policyId())).isEqualTo(65_600);

        CancelPolicyPlan plan = service.preview(owner.policyId(), DEC_1);
        assertThat(plan.keptBackCents()).isEqualTo(65_600);
        assertThat(plan.keptBackFor()).isNull();

        service.cancel(owner.policyId(), from(DEC_1), brokerId());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(MePaths.cover(owner.policyId()))
                        .cookie(owner.session()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.cancellation.keptBackCents").value(65600))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.cancellation.keptBackFor").doesNotExist());
    }

    // --- a credit smaller than what's owed (AC5) ---

    @Test
    void anUnpaidPolicyRefundsNothingAndStillOwesForTheDaysCovered() throws Exception {
        long id = owner().policyId(); // $1,200.00 due

        CancelPolicyPlan plan = service.preview(id, DEC_1);

        assertThat(plan.creditTotalCents()).isEqualTo(-90_082);
        assertThat(plan.keptBackCents()).isEqualTo(90_082);
        assertThat(plan.keptBackFor()).isEqualTo("ISSUE");
        assertThat(plan.balanceAfterCents()).isEqualTo(29_918);
        assertThat(plan.stillOwedCents()).isEqualTo(29_918);
        assertThat(plan.refunds()).isEmpty();
        assertThat(plan.refundTotalCents()).isZero();
    }

    // --- validation (AC6) ---

    @Test
    void aMissingCancelDateIsRejected() throws Exception {
        long id = owner().policyId();
        assertFieldError(() -> service.preview(id, null), "cancelDate", CancelPolicyService.NO_DATE);
    }

    @Test
    void aCancelDateBeforeThePolicyStartsIsRejected() throws Exception {
        long id = owner().policyId();
        assertFieldError(() -> service.preview(id, SEP_1.minusDays(1)),
                "cancelDate", "Cover can't end before the policy starts (Sep 1, 2026).");
    }

    @Test
    void aCancelDateOnOrAfterThePolicyEndIsRejected() throws Exception {
        long id = owner().policyId();
        assertFieldError(() -> service.preview(id, POLICY_END),
                "cancelDate", "Pick a day before the policy ends (Sep 1, 2027).");
        assertFieldError(() -> service.preview(id, POLICY_END.plusDays(10)),
                "cancelDate", "Pick a day before the policy ends (Sep 1, 2027).");
    }

    @Test
    void savingNeedsAReasonTheCustomerCanRead() throws Exception {
        long id = owner().policyId();
        assertFieldError(() -> service.cancel(id, new CancelPolicyCommand(DEC_1, null), brokerId()),
                "reason", CancelPolicyService.NO_REASON);
        assertFieldError(() -> service.cancel(id, new CancelPolicyCommand(DEC_1, "  "), brokerId()),
                "reason", CancelPolicyService.NO_REASON);
        assertFieldError(() -> service.cancel(id, new CancelPolicyCommand(DEC_1, "x".repeat(501)), brokerId()),
                "reason", CancelPolicyService.REASON_TOO_LONG);
    }

    @Test
    void savingReportsEveryFieldToFixAtOnce() throws Exception {
        long id = owner().policyId();
        assertThatThrownBy(() -> service.cancel(id, new CancelPolicyCommand(null, ""), brokerId()))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.fields()).containsExactly(
                        Map.entry("cancelDate", CancelPolicyService.NO_DATE),
                        Map.entry("reason", CancelPolicyService.NO_REASON)));
    }

    @Test
    void anAlreadyCancelledPolicyCannotBeCancelledAgain() throws Exception {
        long id = owner().policyId();
        service.cancel(id, from(DEC_1), brokerId());
        assertFieldError(() -> service.preview(id, DEC_1), "policy", CancelPolicyService.ALREADY_CANCELLED);
        assertFieldError(() -> service.cancel(id, from(DEC_1), brokerId()),
                "policy", CancelPolicyService.ALREADY_CANCELLED);
    }

    @Test
    void aPaymentStillWithTheBankBlocksACancellation() throws Exception {
        Owner owner = owner();
        Started waiting = PaymentTestData.startPayment(mvc, jdbc, owner);
        mvc.perform(StripeTestSupport.delivery(StripeTestSupport.intentProcessing(StripeTestSupport.newEventId(),
                waiting.intentId(), waiting.amountCents()))).andExpect(status().isOk());

        assertFieldError(() -> service.preview(owner.policyId(), DEC_1), "policy", CancelPolicyService.PAYMENT_WAITING);
        assertFieldError(() -> service.cancel(owner.policyId(), from(DEC_1), brokerId()),
                "policy", CancelPolicyService.PAYMENT_WAITING);
        assertThat(CancelPolicyService.PAYMENT_WAITING)
                .isEqualTo("A payment is waiting for the bank. Cancel once it settles.");
    }

    @Test
    void anUnknownPolicyIsNotFound() {
        assertThatThrownBy(() -> service.preview(Long.MAX_VALUE, DEC_1)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.cancel(Long.MAX_VALUE, from(DEC_1), brokerId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void anInvalidCancellationSavesNothing() throws Exception {
        long id = owner().policyId();
        long before = writes();
        assertThatThrownBy(() -> service.cancel(id, new CancelPolicyCommand(DEC_1, ""), brokerId()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.cancel(id, from(POLICY_END), brokerId()))
                .isInstanceOf(ValidationException.class);
        assertThat(writes()).isEqualTo(before);
    }

    // --- saving (AC7) ---

    @Test
    void savingPostsOneCreditPerCoverCancelsThePolicyAndRecordsTheRefundsThePreviewShowed() throws Exception {
        Example ex = workedExample();
        long id = ex.owner().policyId();
        List<LedgerLine> linesBefore = ledger.lines(id);
        CancelPolicyPlan preview = service.preview(id, DEC_1);

        CancelPolicyService.Cancelled saved = service.cancel(id, from(DEC_1), brokerId());

        assertThat(saved.policyId()).isEqualTo(id);
        assertThat(saved.cancelDate()).isEqualTo(DEC_1);
        assertThat(saved.transactionDisplayId()).matches("PTX-\\d{4,}");
        assertThat(saved.balanceCents()).isEqualTo(preview.balanceAfterCents()).isEqualTo(ledger.balance(id));
        assertThat(saved.refundTotalCents()).isEqualTo(143_482);
        assertThat(saved.refunds()).extracting(r -> r.paymentId(), r -> r.amountCents()).containsExactly(
                org.assertj.core.groups.Tuple.tuple(ex.extraPayment(), 64_200L),
                org.assertj.core.groups.Tuple.tuple(ex.basePayment(), 79_282L));

        var txs = jdbc.sql("""
                select display_id, effective_date, recorded_on, reason, created_by from policy_transactions
                where policy_id = ? and type = 'CANCEL'""")
                .param(id)
                .query((rs, n) -> List.of(rs.getString(1), rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate(),
                        rs.getString(4), rs.getLong(5)))
                .list();
        assertThat(txs).containsExactly(List.of(saved.transactionDisplayId(), DEC_1, TODAY, REASON, brokerId()));

        List<LedgerLine> credits = ledger.lines(id).stream()
                .filter(l -> saved.transactionDisplayId().equals(l.policyTransactionDisplayId())).toList();
        assertThat(credits).extracting(LedgerLine::displayId).containsExactlyElementsOf(saved.creditDisplayIds());
        assertThat(credits).hasSize(2);
        assertThat(credits.get(0)).satisfies(base -> {
            assertThat(base.type()).isEqualTo(EntryType.CREDIT);
            assertThat(base.entryDate()).isEqualTo(TODAY);
            assertThat(base.description()).isEqualTo("General liability return premium");
            assertThat(base.amountCents()).isEqualTo(-90_082);
            assertThat(base.coverageId()).isEqualTo(coverageId(id, CoverKind.BASE));
            assertThat(base.calcAnnualCents()).isEqualTo(120_000L);
            assertThat(base.calcDays()).isEqualTo(274);
            assertThat(base.calcTermDays()).isEqualTo(365);
            assertThat(base.coverFrom()).isEqualTo(DEC_1);
            assertThat(base.coverTo()).isEqualTo(POLICY_END);
        });
        assertThat(credits.get(1)).satisfies(extra -> {
            assertThat(extra.type()).isEqualTo(EntryType.CREDIT);
            assertThat(extra.description()).isEqualTo("Product liability return premium");
            assertThat(extra.amountCents()).isEqualTo(-54_800);
            assertThat(extra.coverageId()).isEqualTo(coverageId(id, CoverKind.PRODUCT_LIABILITY));
        });

        // Every line that was there before is still there, unchanged and not undone (AC8).
        assertThat(ledger.lines(id).subList(0, linesBefore.size())).isEqualTo(linesBefore);

        assertThat(jdbc.sql("select status, cancelled_on, end_date from policies where id = ?").param(id)
                .query((rs, n) -> List.of(rs.getString(1), rs.getObject(2, LocalDate.class),
                        rs.getObject(3, LocalDate.class))).single())
                .containsExactly("CANCELLED", DEC_1, POLICY_END);

        var refunds = jdbc.sql("""
                select r.payment_id, r.amount_cents, r.status, t.display_id from refunds r
                join policy_transactions t on t.id = r.policy_transaction_id
                where r.policy_id = ? order by r.id""")
                .param(id)
                .query((rs, n) -> List.of(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4)))
                .list();
        assertThat(refunds).containsExactly(
                List.of(ex.extraPayment(), 64_200L, "PENDING", saved.transactionDisplayId()),
                List.of(ex.basePayment(), 79_282L, "PENDING", saved.transactionDisplayId()));
    }

    @Test
    void refundsGoToStripeAfterCommitWithOneKeyPerAttempt() throws Exception {
        Example ex = workedExample();
        long id = ex.owner().policyId();

        service.cancel(id, from(DEC_1), brokerId());

        var sent = jdbc.sql("""
                select r.id, p.stripe_payment_intent_id, r.stripe_refund_id from refunds r
                join payments p on p.id = r.payment_id where r.policy_id = ? order by r.id""")
                .param(id)
                .query((rs, n) -> new Object[] {rs.getLong(1), rs.getString(2), rs.getString(3)})
                .list();
        assertThat(sent).hasSize(2);
        for (Object[] r : sent) {
            assertThat(r[2]).as("sent to Stripe once the cancellation committed").isNotNull();
            assertThat(stripe.refundsFor((String) r[1])).extracting(FakeStripeGateway.RefundCall::idempotencyKey)
                    .contains("refund-" + r[0] + "-1");
        }
        // Money leaves only by webhook: nothing has posted a REFUND line yet.
        assertThat(ledger.lines(id)).noneMatch(l -> l.type() == EntryType.REFUND);
    }

    @Test
    void anUnpaidPolicySavesItsCreditAndStillOwesTheRest() throws Exception {
        long id = owner().policyId();

        CancelPolicyService.Cancelled saved = service.cancel(id, from(DEC_1), brokerId());

        assertThat(saved.refunds()).isEmpty();
        assertThat(saved.refundTotalCents()).isZero();
        assertThat(saved.balanceCents()).isEqualTo(29_918).isEqualTo(ledger.balance(id));
    }

    @Test
    void savingCancelsAnUnpaidPaymentForTheOldAmount() throws Exception {
        Owner owner = owner();
        Started open = PaymentTestData.startPayment(mvc, jdbc, owner); // $1,200.00, opened but not paid

        service.cancel(owner.policyId(), from(DEC_1), brokerId());

        assertThat(stripe.statusOf(open.intentId())).isEqualTo("canceled");
        assertThat(jdbc.sql("select stripe_canceled_at is not null from payments where id = ?")
                .param(open.paymentId()).query(Boolean.class).single()).isTrue();
    }

    // --- after the extra cover was removed (remove-cover AC6) ---

    /** Base paid, extra cover from Oct 8 paid, then removed from Dec 1 ($548.00 back). */
    Owner removedFromDec1() throws Exception {
        Owner owner = owner();
        payInFull(owner);
        adder.add(owner.policyId(), new AddCoverageCommand(CoverKind.PRODUCT_LIABILITY, OCT_8), brokerId());
        payInFull(owner);
        remover.remove(owner.policyId(), new RemoveCoverCommand(DEC_1, "No longer sells to shops."), brokerId());
        return owner;
    }

    // covers remove-cover AC6, T1
    @Test
    void cancellingOnOrAfterTheRemovalDayCreditsTheRemovedExtraCoverNothing() throws Exception {
        long id = removedFromDec1().policyId();

        assertThat(service.preview(id, DEC_1).credits()).extracting(CancelPolicyPlan.Credit::kind)
                .containsExactly(CoverKind.BASE);
        assertThat(service.preview(id, JAN_1).credits()).extracting(CancelPolicyPlan.Credit::kind)
                .containsExactly(CoverKind.BASE);
    }

    // covers remove-cover AC6, T1
    @Test
    void cancellingBeforeTheRemovalDayCreditsTheExtraCoverOnlyUpToIt() throws Exception {
        long id = removedFromDec1().policyId();
        LocalDate nov1 = LocalDate.of(2026, 11, 1);

        CancelPolicyPlan plan = service.preview(id, nov1);

        assertThat(plan.credits()).hasSize(2);
        assertThat(plan.credits().get(1)).satisfies(extra -> {
            assertThat(extra.kind()).isEqualTo(CoverKind.PRODUCT_LIABILITY);
            assertThat(extra.from()).isEqualTo(nov1);
            assertThat(extra.to()).isEqualTo(DEC_1);
            assertThat(extra.calc().days()).isEqualTo(30);
            assertThat(extra.amountCents()).isEqualTo(-6_000);
        });
        // Every extra-cover day is credited once: Nov 1 → Dec 1 here, Dec 1 → Sep 1 by the removal.
        long extraCredited = -jdbc.sql("""
                select coalesce(sum(amount_cents), 0) from ledger_entries
                where policy_id = ? and entry_type = 'CREDIT' and coverage_id = ?""")
                .params(id, coverageId(id, CoverKind.PRODUCT_LIABILITY)).query(Long.class).single()
                - plan.credits().get(1).amountCents();
        assertThat(extraCredited).isEqualTo(54_800 + 6_000);
    }

    // --- two brokers at once (AC8) ---

    @Test
    void twoSimultaneousCancellationsSaveOnce() throws Exception {
        long id = workedExample().owner().policyId();
        long broker = brokerId();
        CountDownLatch go = new CountDownLatch(1);
        Callable<CancelPolicyService.Cancelled> cancel = () -> {
            go.await();
            return service.cancel(id, from(DEC_1), broker);
        };

        List<Future<CancelPolicyService.Cancelled>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            results.add(pool.submit(cancel));
            results.add(pool.submit(cancel));
            go.countDown();
        }

        int saved = 0;
        List<Throwable> rejected = new ArrayList<>();
        for (Future<CancelPolicyService.Cancelled> r : results) {
            try {
                r.get();
                saved++;
            } catch (ExecutionException e) {
                rejected.add(e.getCause());
            }
        }
        assertThat(saved).isEqualTo(1);
        assertThat(rejected).singleElement().isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.fields()).containsExactly(Map.entry("policy", CancelPolicyService.ALREADY_CANCELLED)));
        assertThat(jdbc.sql("select count(*) from policy_transactions where policy_id = ? and type = 'CANCEL'")
                .param(id).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from refunds where policy_id = ?").param(id).query(Long.class).single())
                .isEqualTo(2);
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
                     + (select count(*) from ledger_entries) + (select count(*) from payments)
                     + (select count(*) from refunds)""")
                .query(Long.class).single();
    }
}
