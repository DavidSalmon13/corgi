package app.plainly.policy;

import app.plainly.billing.PlannedRefund;
import app.plainly.ledger.LedgerLine;
import app.plainly.pricing.ProRata;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * A cancellation priced before anything is saved. B4's preview shows it and saving posts exactly this: one credit
 * line per live cover for its unused days, then any refund of what the credit leaves over.
 *
 * @param balanceBeforeCents what the policy owed before the cancellation (negative: we already owed the customer)
 * @param keptBackCents      the part of the credit that pays off what was still owed, so it isn't refunded
 * @param keptBackFor        the policy change that left that balance owing (ISSUE, ADD_COVERAGE, CORRECT), or null
 * @param balanceAfterCents  the balance once the credits post, before any refund settles
 * @param stillOwedCents     what the customer still owes for the days they were covered, when the credit is smaller
 * @param refunds            what goes back to cards, latest payment first
 */
public record CancelPolicyPlan(long policyId, LocalDate cancelDate, LocalDate policyStart, LocalDate policyEnd,
        long usedDays, long unusedDays, long termDays, List<Credit> credits, long balanceBeforeCents,
        long keptBackCents, String keptBackFor, long balanceAfterCents, long stillOwedCents,
        List<PlannedRefund> refunds) {

    /** One cover's unused days, priced like a charge (annual × days ÷ days in term, rounded once) and given back. */
    public record Credit(long coverageId, CoverKind kind, String label, LocalDate from, LocalDate to,
            ProRata.Charge calc) {

        /** Negative: the line takes the unused days off the balance. */
        @JsonProperty
        public long amountCents() {
            return -calc.amountCents();
        }
    }

    /** All credits together, negative. */
    @JsonProperty
    public long creditTotalCents() {
        return credits.stream().mapToLong(Credit::amountCents).sum();
    }

    /** Everything going back to cards, summed for B4's save label ("Cancel policy and refund $1,434.82"). */
    @JsonProperty
    public long refundTotalCents() {
        return refunds.stream().mapToLong(PlannedRefund::amountCents).sum();
    }

    /**
     * Each live cover's credit, priced over max(its start, the cancel day) → its end. A cover that starts on or after
     * the cancel day is credited in full, to the cent of its charge. A cover worth nothing for those days is skipped.
     * Pure, so preview and save price it the same way.
     */
    static List<Credit> credits(PolicyRepository.Term policy, List<CoverageRepository.Live> covers,
            LocalDate cancelDate) {
        List<Credit> credits = new ArrayList<>();
        for (CoverageRepository.Live cover : covers) {
            LocalDate from = cover.from().isAfter(cancelDate) ? cover.from() : cancelDate;
            if (!from.isBefore(cover.to())) {
                continue;
            }
            ProRata.Charge unused = ProRata.charge(cover.annualPremiumCents(), from, cover.to(), policy.start(),
                    policy.end());
            if (unused.amountCents() > 0) {
                credits.add(new Credit(cover.id(), cover.kind(), cover.kind().label() + ", unused days", from,
                        cover.to(), unused));
            }
        }
        return List.copyOf(credits);
    }

    /**
     * Puts the plan together. The credit first pays off what is still owed ({@code keptBack}); a credit smaller than
     * that leaves the rest owed for the days the customer was covered.
     */
    static CancelPolicyPlan of(PolicyRepository.Term policy, LocalDate cancelDate, List<Credit> credits,
            long balanceBefore, String owedFor, List<PlannedRefund> refunds) {
        long credit = -credits.stream().mapToLong(Credit::amountCents).sum();
        long keptBack = Math.max(0, Math.min(balanceBefore, credit));
        long balanceAfter = balanceBefore - credit;
        long used = ChronoUnit.DAYS.between(policy.start(), cancelDate);
        long term = ChronoUnit.DAYS.between(policy.start(), policy.end());
        return new CancelPolicyPlan(policy.id(), cancelDate, policy.start(), policy.end(), used, term - used, term,
                credits, balanceBefore, keptBack, keptBack > 0 ? owedFor : null, balanceAfter,
                Math.max(0, balanceAfter), refunds);
    }

    /**
     * The policy change that alone left the balance owing, to name what the credit keeps back ("Correction still
     * owed"): the newest change, but only if everything owed before its first line was already paid. Otherwise the
     * balance mixes several changes and naming one would mislead, so it is null and the words stay neutral.
     *
     * @param lines        the ledger, oldest first, before the cancellation
     * @param transactions the policy changes, oldest first, before the cancellation
     */
    public static String soleCauseOfBalance(List<LedgerLine> lines, List<PolicyViews.Transaction> transactions) {
        if (transactions.isEmpty()) {
            return null;
        }
        PolicyViews.Transaction newest = transactions.getLast();
        for (int i = 0; i < lines.size(); i++) {
            if (newest.displayId().equals(lines.get(i).policyTransactionDisplayId())) {
                long owedBefore = i == 0 ? 0 : lines.get(i - 1).balanceAfterCents();
                return owedBefore <= 0 ? newest.type() : null;
            }
        }
        return null;
    }
}
