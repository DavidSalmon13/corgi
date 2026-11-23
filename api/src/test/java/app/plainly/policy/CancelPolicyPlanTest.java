package app.plainly.policy;

import static org.assertj.core.api.Assertions.assertThat;

import app.plainly.pricing.ProRata;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

// covers remove-cover AC6, T9
/**
 * Cancelling after a removal, without the database, on a 366-day policy period (Mar 1, 2027 → Mar 1, 2028), where
 * $730.00 a year doesn't divide into whole cents a day. Product liability runs May 6, 2027 → Mar 1, 2028 (300 days,
 * charged $598.36) and is removed from May 16 ($578.42 back).
 */
class CancelPolicyPlanTest {

    static final LocalDate MAR_1 = LocalDate.of(2027, 3, 1);
    static final LocalDate MAR_1_NEXT = LocalDate.of(2028, 3, 1);
    static final LocalDate MAY_6 = LocalDate.of(2027, 5, 6);
    static final LocalDate MAY_16 = LocalDate.of(2027, 5, 16);

    final PolicyRepository.Term term = new PolicyRepository.Term(1, "ACTIVE", MAR_1, MAR_1_NEXT, null);
    final CoverageRepository.Live removed =
            new CoverageRepository.Live(21, CoverKind.PRODUCT_LIABILITY, 73_000, MAY_6, MAR_1_NEXT, MAY_16);

    long charge(LocalDate from, LocalDate to) {
        return ProRata.charge(73_000, from, to, MAR_1, MAR_1_NEXT).amountCents();
    }

    @Test
    void theRemovalAndALaterCancellationTogetherCreditExactlyWhatWasCharged() {
        long charged = charge(MAY_6, MAR_1_NEXT);
        long removalCredit = charge(MAY_16, MAR_1_NEXT);
        assertThat(charged).isEqualTo(59_836);
        assertThat(removalCredit).isEqualTo(57_842);

        CancelPolicyPlan.Credit c = CancelPolicyPlan.credits(term, List.of(removed), MAR_1).getFirst();

        // Rounded on its own, May 6 → May 16 would be $19.95, and the cover would get $598.37 back for $598.36 paid.
        assertThat(c.from()).isEqualTo(MAY_6);
        assertThat(c.to()).isEqualTo(MAY_16);
        assertThat(c.calc().days()).isEqualTo(10);
        assertThat(c.amountCents()).isEqualTo(-(charged - removalCredit));
        assertThat(-c.amountCents() + removalCredit).isEqualTo(charged);
    }

    @Test
    void aCoverThatWasNeverRemovedIsStillPricedOnItsOwn() {
        CoverageRepository.Live running =
                new CoverageRepository.Live(21, CoverKind.PRODUCT_LIABILITY, 73_000, MAY_6, MAR_1_NEXT, null);

        CancelPolicyPlan.Credit c = CancelPolicyPlan.credits(term, List.of(running), MAY_16).getFirst();

        assertThat(c.amountCents()).isEqualTo(-charge(MAY_16, MAR_1_NEXT));
    }
}
