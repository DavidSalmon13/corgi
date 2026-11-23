package app.plainly.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.CorrectionTestData;
import app.plainly.FakeStripeGateway;
import app.plainly.IntegrationTest;
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
 * year, paid; extra cover $730.00 a year from Oct 8, paid ($656.00). Removed from Dec 1, 2026: 274 unused days, so
 * $730.00 × 274 ÷ 365 = $548.00 comes back.
 */
// covers AC1, AC2, AC3, AC4, AC5, T1
@IntegrationTest
@AutoConfigureMockMvc
class RemoveCoverServiceTest {

    static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    static final LocalDate POLICY_END = LocalDate.of(2027, 9, 1);
    static final LocalDate OCT_8 = LocalDate.of(2026, 10, 8);
    static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);
    static final LocalDate JAN_1 = LocalDate.of(2027, 1, 1);
    static final LocalDate TODAY = LocalDate.of(2026, 11, 22); // plainly.today in IntegrationTest
    static final String REASON = "Stopped selling packaged goods to shops.";

    @Autowired RemoveCoverService service;
    @Autowired CancelPolicyService canceller;
    @Autowired AddCoverageService adder;
    @Autowired CorrectCoverageService corrector;
    @Autowired IssuePolicyService issuer;
    @Autowired LedgerQueryService ledger;
    @Autowired FakeStripeGateway stripe;
    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    // --- scenario builders ---

    long brokerId() {
        return CorrectionTestData.brokerId(jdbc);
    }

    /** A signed-up owner whose base-cover-only policy (Sep 1, 2026 → Sep 1, 2027) has $1,200.00 due. */
    Owner owner() throws Exception {
        return PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Juniper & Rye Bakery LLC");
    }

    long payInFull(Owner owner) throws Exception {
        return CorrectionTestData.payInFull(mvc, jdbc, owner).paymentId();
    }

    void addExtraFrom(Owner owner, LocalDate start) {
        adder.add(owner.policyId(), new AddCoverageCommand(CoverKind.PRODUCT_LIABILITY, start), brokerId());
    }

    /** The worked example: base paid, extra cover from Oct 8 added and paid ($656.00). */
    record Example(Owner owner, long basePayment, long extraPayment) {}

    Example workedExample() throws Exception {
        Owner owner = owner();
        long base = payInFull(owner);
        addExtraFrom(owner, OCT_8);
        long extra = payInFull(owner);
        assertThat(ledger.balance(owner.policyId())).isZero();
        return new Example(owner, base, extra);
    }

    static RemoveCoverCommand from(LocalDate day) {
        return new RemoveCoverCommand(day, REASON);
    }

    String paymentDisplayId(long paymentId) {
        return jdbc.sql("select display_id from payments where id = ?").param(paymentId).query(String.class).single();
    }

    long extraCoverId(long policyId) {
        return jdbc.sql("""
                select id from coverages where policy_id = ? and kind = 'PRODUCT_LIABILITY'
                and replaced_by_coverage_id is null""")
                .param(policyId).query(Long.class).single();
    }

    // --- preview (AC3) ---

    @Test
    void removingTheWorkedExampleFromDec1CreditsTheUnusedDaysAndReturnsThemToTheCard() throws Exception {
        Example ex = workedExample();
        long id = ex.owner().policyId();

        RemoveCoverPlan plan = service.preview(id, DEC_1);

        assertThat(plan.policyId()).isEqualTo(id);
        assertThat(plan.coverageId()).isEqualTo(extraCoverId(id));
        assertThat(plan.endDate()).isEqualTo(DEC_1);
        assertThat(plan.coverFrom()).isEqualTo(OCT_8);
        assertThat(plan.policyEnd()).isEqualTo(POLICY_END);
        assertThat(plan.credit()).satisfies(c -> {
            assertThat(c.coverageId()).isEqualTo(extraCoverId(id));
            assertThat(c.kind()).isEqualTo(CoverKind.PRODUCT_LIABILITY);
            assertThat(c.label()).isEqualTo("Product liability return premium");
            assertThat(c.from()).isEqualTo(DEC_1);
            assertThat(c.to()).isEqualTo(POLICY_END);
            assertThat(c.calc().annualCents()).isEqualTo(73_000);
            assertThat(c.calc().days()).isEqualTo(274);
            assertThat(c.calc().termDays()).isEqualTo(365);
            assertThat(c.amountCents()).isEqualTo(-54_800);
        });
        assertThat(plan.creditCents()).isEqualTo(-54_800);
        assertThat(plan.balanceBeforeCents()).isZero();
        assertThat(plan.keptBackCents()).isZero();
        assertThat(plan.keptBackFor()).isNull();
        assertThat(plan.balanceAfterCents()).isEqualTo(-54_800);
        assertThat(plan.stillOwedCents()).isZero();
        assertThat(plan.refunds()).containsExactly(
                new PlannedRefund(ex.extraPayment(), paymentDisplayId(ex.extraPayment()), "4242", 54_800));
        assertThat(plan.refundTotalCents()).isEqualTo(54_800);
    }

    @Test
    void previewSavesNothing() throws Exception {
        long id = workedExample().owner().policyId();
        long before = writes();
        service.preview(id, DEC_1);
        service.preview(id, OCT_8);
        assertThat(writes()).isEqualTo(before);
        assertThat(jdbc.sql("select ended_on is null from coverages where id = ?").param(extraCoverId(id))
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    void previewEqualsWhatSavingPosts() throws Exception {
        long id = workedExample().owner().policyId();
        RemoveCoverPlan preview = service.preview(id, DEC_1);

        RemoveCoverService.Removed saved = service.remove(id, from(DEC_1), brokerId());

        LedgerLine credit = ledger.lines(id).stream()
                .filter(l -> l.displayId().equals(saved.creditDisplayId())).findFirst().orElseThrow();
        assertThat(credit.amountCents()).isEqualTo(preview.creditCents());
        assertThat(saved.balanceCents()).isEqualTo(preview.balanceAfterCents());
        assertThat(saved.refundTotalCents()).isEqualTo(preview.refundTotalCents());
        assertThat(saved.refunds()).extracting(r -> r.paymentId(), r -> r.amountCents())
                .containsExactlyElementsOf(preview.refunds().stream()
                        .map(r -> org.assertj.core.groups.Tuple.tuple(r.paymentId(), r.amountCents())).toList());
    }

    // --- to the cent (AC4) ---

    @Test
    void removingOnTheCoversStartDayCreditsTheWholeChargeToTheCent() throws Exception {
        Owner owner = owner();
        payInFull(owner);
        addExtraFrom(owner, OCT_8);
        LedgerLine charge = ledger.lines(owner.policyId()).stream()
                .filter(l -> l.type() == EntryType.CHARGE && l.description().startsWith("Product liability endorsement"))
                .findFirst().orElseThrow();

        RemoveCoverPlan plan = service.preview(owner.policyId(), OCT_8);

        assertThat(plan.credit().from()).isEqualTo(OCT_8);
        assertThat(plan.credit().calc().days()).isEqualTo(328);
        assertThat(plan.creditCents()).isEqualTo(-charge.amountCents()).isEqualTo(-65_600);
    }

    @Test
    void anUnpaidExtraCoverIsCreditedAgainstWhatIsOwedAndRefundsNothing() throws Exception {
        Owner owner = owner();
        payInFull(owner);
        addExtraFrom(owner, OCT_8); // $656.00 owed

        RemoveCoverPlan plan = service.preview(owner.policyId(), DEC_1);

        assertThat(plan.balanceBeforeCents()).isEqualTo(65_600);
        assertThat(plan.keptBackCents()).isEqualTo(54_800);
        assertThat(plan.keptBackFor()).isEqualTo("ADD_COVERAGE");
        assertThat(plan.balanceAfterCents()).isEqualTo(10_800);
        assertThat(plan.stillOwedCents()).isEqualTo(10_800);
        assertThat(plan.refunds()).isEmpty();
        assertThat(plan.refundTotalCents()).isZero();
    }

    // --- validation (AC1, AC2) ---

    @Test
    void aMissingEndDateIsRejected() throws Exception {
        long id = workedExample().owner().policyId();
        assertFieldError(() -> service.preview(id, null), "endDate", RemoveCoverService.NO_DATE);
    }

    @Test
    void anEndDateBeforeTheExtraCoverStartsIsRejected() throws Exception {
        long id = workedExample().owner().policyId();
        assertFieldError(() -> service.preview(id, OCT_8.minusDays(1)),
                "endDate", "Product liability can't end before its effective date (Oct 8, 2026).");
    }

    @Test
    void anEndDateOnOrAfterThePolicyEndIsRejected() throws Exception {
        long id = workedExample().owner().policyId();
        assertFieldError(() -> service.preview(id, POLICY_END),
                "endDate", "Pick a day before the policy period ends (Sep 1, 2027).");
        assertFieldError(() -> service.preview(id, POLICY_END.plusDays(10)),
                "endDate", "Pick a day before the policy period ends (Sep 1, 2027).");
    }

    @Test
    void savingNeedsAReasonTheCustomerCanRead() throws Exception {
        long id = workedExample().owner().policyId();
        assertFieldError(() -> service.remove(id, new RemoveCoverCommand(DEC_1, null), brokerId()),
                "reason", RemoveCoverService.NO_REASON);
        assertFieldError(() -> service.remove(id, new RemoveCoverCommand(DEC_1, "  "), brokerId()),
                "reason", RemoveCoverService.NO_REASON);
        assertFieldError(() -> service.remove(id, new RemoveCoverCommand(DEC_1, "x".repeat(501)), brokerId()),
                "reason", RemoveCoverService.REASON_TOO_LONG);
    }

    @Test
    void savingReportsEveryFieldToFixAtOnce() throws Exception {
        long id = workedExample().owner().policyId();
        assertThatThrownBy(() -> service.remove(id, new RemoveCoverCommand(null, ""), brokerId()))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.fields()).containsExactly(
                        Map.entry("endDate", RemoveCoverService.NO_DATE),
                        Map.entry("reason", RemoveCoverService.NO_REASON)));
    }

    @Test
    void aPolicyWithoutExtraCoverHasNothingToRemove() throws Exception {
        long id = owner().policyId();
        assertFieldError(() -> service.preview(id, DEC_1), "policy", RemoveCoverService.NO_EXTRA_COVER);
        assertFieldError(() -> service.remove(id, from(DEC_1), brokerId()),
                "policy", RemoveCoverService.NO_EXTRA_COVER);
        assertThat(RemoveCoverService.NO_EXTRA_COVER).isEqualTo("This policy has no product liability to remove.");
    }

    @Test
    void aCancelledPolicyCannotHaveCoverRemoved() throws Exception {
        long id = workedExample().owner().policyId();
        canceller.cancel(id, new CancelPolicyCommand(DEC_1, "Closing the bakery."), brokerId());
        assertFieldError(() -> service.preview(id, DEC_1), "policy", RemoveCoverService.CANCELLED);
        assertFieldError(() -> service.remove(id, from(DEC_1), brokerId()), "policy", RemoveCoverService.CANCELLED);
        assertThat(RemoveCoverService.CANCELLED).isEqualTo("This policy is cancelled.");
    }

    @Test
    void aPaymentStillWithTheBankBlocksARemoval() throws Exception {
        Owner owner = owner();
        payInFull(owner);
        addExtraFrom(owner, OCT_8);
        Started waiting = PaymentTestData.startPayment(mvc, jdbc, owner);
        mvc.perform(StripeTestSupport.delivery(StripeTestSupport.intentProcessing(StripeTestSupport.newEventId(),
                waiting.intentId(), waiting.amountCents()))).andExpect(status().isOk());

        assertFieldError(() -> service.preview(owner.policyId(), DEC_1), "policy", RemoveCoverService.PAYMENT_WAITING);
        assertFieldError(() -> service.remove(owner.policyId(), from(DEC_1), brokerId()),
                "policy", RemoveCoverService.PAYMENT_WAITING);
        assertThat(RemoveCoverService.PAYMENT_WAITING)
                .isEqualTo("A payment is waiting for the bank. Remove this once it settles.");
    }

    @Test
    void anUnknownPolicyIsNotFound() {
        assertThatThrownBy(() -> service.preview(Long.MAX_VALUE, DEC_1)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.remove(Long.MAX_VALUE, from(DEC_1), brokerId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void anInvalidRemovalSavesNothing() throws Exception {
        long id = workedExample().owner().policyId();
        long before = writes();
        assertThatThrownBy(() -> service.remove(id, new RemoveCoverCommand(DEC_1, ""), brokerId()))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.remove(id, from(POLICY_END), brokerId()))
                .isInstanceOf(ValidationException.class);
        assertThat(writes()).isEqualTo(before);
    }

    // --- once per policy (AC1, AC5) ---

    @Test
    void extraCoverCanBeRemovedOncePerPolicy() throws Exception {
        long id = workedExample().owner().policyId();
        service.remove(id, from(DEC_1), brokerId());

        String removed = "Product liability was removed on Dec 1, 2026.";
        assertFieldError(() -> service.preview(id, DEC_1), "policy", removed);
        assertFieldError(() -> service.remove(id, from(JAN_1), brokerId()), "policy", removed);
    }

    // --- saving (AC4) ---

    @Test
    void savingPostsOneTracedCreditEndsTheCoverAndRecordsTheRefundThePreviewShowed() throws Exception {
        Example ex = workedExample();
        long id = ex.owner().policyId();
        long extraId = extraCoverId(id);
        List<LedgerLine> linesBefore = ledger.lines(id);

        RemoveCoverService.Removed saved = service.remove(id, from(DEC_1), brokerId());

        assertThat(saved.policyId()).isEqualTo(id);
        assertThat(saved.endDate()).isEqualTo(DEC_1);
        assertThat(saved.transactionDisplayId()).matches("PTX-\\d{4,}");
        assertThat(saved.creditDisplayId()).matches("LE-\\d{4,}");
        assertThat(saved.balanceCents()).isEqualTo(-54_800).isEqualTo(ledger.balance(id));
        assertThat(saved.refundTotalCents()).isEqualTo(54_800);
        assertThat(saved.refunds()).singleElement().satisfies(r -> {
            assertThat(r.paymentId()).isEqualTo(ex.extraPayment());
            assertThat(r.amountCents()).isEqualTo(54_800);
        });

        var txs = jdbc.sql("""
                select display_id, effective_date, recorded_on, reason, created_by from policy_transactions
                where policy_id = ? and type = 'REMOVE_COVERAGE'""")
                .param(id)
                .query((rs, n) -> List.of(rs.getString(1), rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate(),
                        rs.getString(4), rs.getLong(5)))
                .list();
        assertThat(txs).containsExactly(List.of(saved.transactionDisplayId(), DEC_1, TODAY, REASON, brokerId()));

        List<LedgerLine> posted = ledger.lines(id).stream()
                .filter(l -> saved.transactionDisplayId().equals(l.policyTransactionDisplayId())).toList();
        assertThat(posted).singleElement().satisfies(credit -> {
            assertThat(credit.displayId()).isEqualTo(saved.creditDisplayId());
            assertThat(credit.type()).isEqualTo(EntryType.CREDIT);
            assertThat(credit.entryDate()).isEqualTo(TODAY);
            assertThat(credit.description()).isEqualTo("Product liability return premium");
            assertThat(credit.amountCents()).isEqualTo(-54_800);
            assertThat(credit.coverageId()).isEqualTo(extraId);
            assertThat(credit.calcAnnualCents()).isEqualTo(73_000L);
            assertThat(credit.calcDays()).isEqualTo(274);
            assertThat(credit.calcTermDays()).isEqualTo(365);
            assertThat(credit.coverFrom()).isEqualTo(DEC_1);
            assertThat(credit.coverTo()).isEqualTo(POLICY_END);
        });

        // Every line that was there before is still there, unchanged and not undone.
        assertThat(ledger.lines(id).subList(0, linesBefore.size())).isEqualTo(linesBefore);

        assertThat(jdbc.sql("select ended_on, effective_from, effective_to from coverages where id = ?").param(extraId)
                .query((rs, n) -> List.of(rs.getObject(1, LocalDate.class), rs.getObject(2, LocalDate.class),
                        rs.getObject(3, LocalDate.class))).single())
                .containsExactly(DEC_1, OCT_8, POLICY_END);
        assertThat(jdbc.sql("select status from policies where id = ?").param(id).query(String.class).single())
                .isEqualTo("ACTIVE");

        var refunds = jdbc.sql("""
                select r.payment_id, r.amount_cents, r.status, t.display_id from refunds r
                join policy_transactions t on t.id = r.policy_transaction_id
                where r.policy_id = ? order by r.id""")
                .param(id)
                .query((rs, n) -> List.of(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4)))
                .list();
        assertThat(refunds).containsExactly(
                List.of(ex.extraPayment(), 54_800L, "PENDING", saved.transactionDisplayId()));
    }

    @Test
    void theRefundGoesToStripeAfterCommitWithOneKeyPerAttempt() throws Exception {
        long id = workedExample().owner().policyId();

        service.remove(id, from(DEC_1), brokerId());

        var sent = jdbc.sql("""
                select r.id, p.stripe_payment_intent_id, r.stripe_refund_id from refunds r
                join payments p on p.id = r.payment_id where r.policy_id = ? order by r.id""")
                .param(id)
                .query((rs, n) -> new Object[] {rs.getLong(1), rs.getString(2), rs.getString(3)})
                .list();
        assertThat(sent).hasSize(1);
        Object[] r = sent.getFirst();
        assertThat(r[2]).as("sent to Stripe once the removal committed").isNotNull();
        assertThat(stripe.refundsFor((String) r[1])).extracting(FakeStripeGateway.RefundCall::idempotencyKey)
                .contains("refund-" + r[0] + "-1");
        // Money leaves only by webhook: nothing has posted a REFUND line yet.
        assertThat(ledger.lines(id)).noneMatch(l -> l.type() == EntryType.REFUND);
    }

    @Test
    void savingCancelsAnUnpaidPaymentForTheOldAmount() throws Exception {
        Owner owner = owner();
        payInFull(owner);
        addExtraFrom(owner, OCT_8);
        Started open = PaymentTestData.startPayment(mvc, jdbc, owner); // $656.00, opened but not paid

        RemoveCoverService.Removed saved = service.remove(owner.policyId(), from(DEC_1), brokerId());

        assertThat(stripe.statusOf(open.intentId())).isEqualTo("canceled");
        assertThat(jdbc.sql("select stripe_canceled_at is not null from payments where id = ?")
                .param(open.paymentId()).query(Boolean.class).single()).isTrue();
        assertThat(saved.refunds()).isEmpty();
        assertThat(saved.balanceCents()).isEqualTo(10_800).isEqualTo(ledger.balance(owner.policyId()));
    }

    // --- two brokers at once (AC4) ---

    @Test
    void twoSimultaneousRemovalsSaveOnce() throws Exception {
        long id = workedExample().owner().policyId();
        long broker = brokerId();
        CountDownLatch go = new CountDownLatch(1);
        Callable<RemoveCoverService.Removed> remove = () -> {
            go.await();
            return service.remove(id, from(DEC_1), broker);
        };

        List<Future<RemoveCoverService.Removed>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            results.add(pool.submit(remove));
            results.add(pool.submit(remove));
            go.countDown();
        }

        int saved = 0;
        List<Throwable> rejected = new ArrayList<>();
        for (Future<RemoveCoverService.Removed> r : results) {
            try {
                r.get();
                saved++;
            } catch (ExecutionException e) {
                rejected.add(e.getCause());
            }
        }
        assertThat(saved).isEqualTo(1);
        assertThat(rejected).singleElement().isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.fields()).containsExactly(
                        Map.entry("policy", "Product liability was removed on Dec 1, 2026.")));
        assertThat(jdbc.sql("select count(*) from policy_transactions where policy_id = ? and type = 'REMOVE_COVERAGE'")
                .param(id).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from refunds where policy_id = ?").param(id).query(Long.class).single())
                .isEqualTo(1);
    }

    // --- what is blocked after a removal (AC5) ---

    @Test
    void afterARemovalExtraCoverCannotBeAddedAgain() throws Exception {
        Example ex = workedExample();
        long id = ex.owner().policyId();
        service.remove(id, from(DEC_1), brokerId());

        String message = "Product liability was removed from this policy on Dec 1, 2026, so it can't be added again.";
        assertFieldError(() -> adder.preview(id, CoverKind.PRODUCT_LIABILITY, JAN_1), "policy", message);
        assertFieldError(() -> adder.add(id, new AddCoverageCommand(CoverKind.PRODUCT_LIABILITY, JAN_1),
                brokerId()), "policy", message);
    }

    @Test
    void afterARemovalTheExtraCoversStartCannotBeCorrected() throws Exception {
        long id = workedExample().owner().policyId();
        service.remove(id, from(DEC_1), brokerId());

        String message = "Product liability was removed on Dec 1, 2026, so its effective date can't be corrected.";
        assertFieldError(() -> corrector.preview(id, LocalDate.of(2026, 10, 1)), "policy", message);
        assertFieldError(() -> corrector.correct(id, new CorrectCoverageCommand(LocalDate.of(2026, 10, 1),
                "Signed request was dated Oct 1."), brokerId()), "policy", message);
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
