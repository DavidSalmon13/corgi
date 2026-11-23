package app.plainly.policy;

import app.plainly.billing.PlannedRefund;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;

/**
 * Removing the extra cover, priced before anything is saved. B4's preview shows it and saving posts exactly this: one
 * credit line for the extra cover's unused days (priced like a cancellation credits a cover), then any return premium
 * of what the credit leaves over.
 *
 * @param coverageId         the extra cover being removed
 * @param endDate            the day the extra cover stops, at the start of that day
 * @param coverFrom          when the extra cover started
 * @param credit             its unused days, endDate → policy end, as a negative amount
 * @param balanceBeforeCents what the policy owed before the removal (negative: we already owed the customer)
 * @param keptBackCents      the part of the credit that pays off what was still owed, so it isn't refunded
 * @param keptBackFor        the policy change that left that balance owing (ISSUE, ADD_COVERAGE, CORRECT), or null
 * @param balanceAfterCents  the balance once the credit posts, before any refund settles
 * @param stillOwedCents     what the customer still owes when the credit is smaller than the balance
 * @param refunds            what goes back to cards, latest payment first
 */
public record RemoveCoverPlan(long policyId, long coverageId, LocalDate endDate, LocalDate coverFrom,
        LocalDate policyEnd, CancelPolicyPlan.Credit credit, long balanceBeforeCents, long keptBackCents,
        String keptBackFor, long balanceAfterCents, long stillOwedCents, List<PlannedRefund> refunds) {

    /** The credit, negative. */
    @JsonProperty
    public long creditCents() {
        return credit.amountCents();
    }

    /**
     * The removed cover's credit: its unused days from the removal day to its end, priced exactly as a cancellation
     * credits a cover (CancelPolicyPlan.credits). Removing on the cover's start day credits its whole charge. Validation
     * keeps the day before the cover ends, so there is always something to credit.
     */
    static CancelPolicyPlan.Credit creditFor(PolicyRepository.Term policy, CoverageRepository.Live cover,
            LocalDate endDate) {
        return CancelPolicyPlan.credits(policy, List.of(cover), endDate).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("cover " + cover.id() + " has no unused days from " + endDate));
    }

    /**
     * Puts the plan together, as a cancellation does: the credit first pays off what is still owed ({@code keptBack},
     * named by {@code owedFor}); a credit smaller than that leaves the rest owed for the days the cover ran.
     */
    static RemoveCoverPlan of(PolicyRepository.Term policy, CoverageRepository.Live cover, LocalDate endDate,
            CancelPolicyPlan.Credit credit, long balanceBefore, String owedFor, List<PlannedRefund> refunds) {
        long amount = -credit.amountCents();
        long keptBack = Math.max(0, Math.min(balanceBefore, amount));
        long balanceAfter = balanceBefore - amount;
        return new RemoveCoverPlan(policy.id(), cover.id(), endDate, cover.from(), policy.end(), credit,
                balanceBefore, keptBack, keptBack > 0 ? owedFor : null, balanceAfter, Math.max(0, balanceAfter),
                List.copyOf(refunds));
    }

    /** Everything going back to cards, for B4's save label ("Remove product liability and refund $548.00"). */
    @JsonProperty
    public long refundTotalCents() {
        return refunds.stream().mapToLong(PlannedRefund::amountCents).sum();
    }
}
