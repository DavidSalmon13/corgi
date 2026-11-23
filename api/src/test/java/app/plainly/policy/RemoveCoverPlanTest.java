package app.plainly.policy;

import static org.assertj.core.api.Assertions.assertThat;

import app.plainly.billing.PlannedRefund;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

// covers remove-cover AC3, AC4, T5
/**
 * Pricing a removal without the database: product liability at $730.00 a year from Oct 8 on a Sep 1, 2026 → Sep 1,
 * 2027 policy (365 days), removed from Dec 1: $730.00 × 274 ÷ 365 = $548.00 back.
 */
class RemoveCoverPlanTest {

    static final LocalDate SEP_1 = LocalDate.of(2026, 9, 1);
    static final LocalDate SEP_1_NEXT = LocalDate.of(2027, 9, 1);
    static final LocalDate OCT_8 = LocalDate.of(2026, 10, 8);
    static final LocalDate DEC_1 = LocalDate.of(2026, 12, 1);

    final PolicyRepository.Term term = new PolicyRepository.Term(1, "ACTIVE", SEP_1, SEP_1_NEXT, null);
    final CoverageRepository.Live extra =
            new CoverageRepository.Live(21, CoverKind.PRODUCT_LIABILITY, 73_000, OCT_8, SEP_1_NEXT, null);
    final List<PlannedRefund> back = List.of(new PlannedRefund(9, "PAY-0002", "4242", 54_800));

    @Test
    void theCreditIsTheUnusedDaysFromTheRemovalDay() {
        CancelPolicyPlan.Credit c = RemoveCoverPlan.creditFor(term, extra, DEC_1);

        assertThat(c.coverageId()).isEqualTo(21);
        assertThat(c.kind()).isEqualTo(CoverKind.PRODUCT_LIABILITY);
        assertThat(c.label()).isEqualTo("Product liability return premium");
        assertThat(c.from()).isEqualTo(DEC_1);
        assertThat(c.to()).isEqualTo(SEP_1_NEXT);
        assertThat(c.calc().days()).isEqualTo(274);
        assertThat(c.calc().termDays()).isEqualTo(365);
        assertThat(c.amountCents()).isEqualTo(-54_800);
    }

    @Test
    void removingOnTheStartDayCreditsTheWholeCharge() {
        // Oct 8 → Sep 1 is 328 days: $730.00 × 328 ÷ 365 = $656.00, the charge to the cent
        assertThat(RemoveCoverPlan.creditFor(term, extra, OCT_8).amountCents()).isEqualTo(-65_600);
    }

    @Test
    void aPaidPolicyGetsTheWholeCreditBack() {
        RemoveCoverPlan plan = RemoveCoverPlan.of(term, extra, DEC_1, RemoveCoverPlan.creditFor(term, extra, DEC_1), 0,
                null, back);

        assertThat(plan.policyId()).isEqualTo(1);
        assertThat(plan.coverageId()).isEqualTo(21);
        assertThat(plan.endDate()).isEqualTo(DEC_1);
        assertThat(plan.coverFrom()).isEqualTo(OCT_8);
        assertThat(plan.policyEnd()).isEqualTo(SEP_1_NEXT);
        assertThat(plan.creditCents()).isEqualTo(-54_800);
        assertThat(plan.keptBackCents()).isZero();
        assertThat(plan.keptBackFor()).isNull();
        assertThat(plan.balanceAfterCents()).isEqualTo(-54_800);
        assertThat(plan.stillOwedCents()).isZero();
        assertThat(plan.refundTotalCents()).isEqualTo(54_800);
    }

    @Test
    void theCreditFirstPaysOffWhatWasOwedAndNamesWhy() {
        RemoveCoverPlan plan = RemoveCoverPlan.of(term, extra, DEC_1, RemoveCoverPlan.creditFor(term, extra, DEC_1),
                1_400, "CORRECT", List.of(new PlannedRefund(9, "PAY-0002", "4242", 53_400)));

        assertThat(plan.keptBackCents()).isEqualTo(1_400);
        assertThat(plan.keptBackFor()).isEqualTo("CORRECT");
        assertThat(plan.balanceAfterCents()).isEqualTo(-53_400);
        assertThat(plan.stillOwedCents()).isZero();
    }

    @Test
    void anUnpaidEndorsementLeavesTheDaysItCoveredOwed() {
        RemoveCoverPlan plan = RemoveCoverPlan.of(term, extra, DEC_1, RemoveCoverPlan.creditFor(term, extra, DEC_1),
                65_600, "ADD_COVERAGE", List.of());

        assertThat(plan.keptBackCents()).isEqualTo(54_800);
        assertThat(plan.keptBackFor()).isEqualTo("ADD_COVERAGE");
        assertThat(plan.balanceAfterCents()).isEqualTo(10_800);
        assertThat(plan.stillOwedCents()).isEqualTo(10_800);
        assertThat(plan.refundTotalCents()).isZero();
    }

    @Test
    void whatWeAlreadyOwedIsNotKeptBack() {
        RemoveCoverPlan plan = RemoveCoverPlan.of(term, extra, DEC_1, RemoveCoverPlan.creditFor(term, extra, DEC_1),
                -1_400, "CORRECT", back);

        assertThat(plan.keptBackCents()).isZero();
        assertThat(plan.keptBackFor()).isNull();
        assertThat(plan.balanceAfterCents()).isEqualTo(-56_200);
    }
}
