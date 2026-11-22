package app.plainly.policy;

import app.plainly.pricing.ProRata;
import java.time.LocalDate;
import java.util.List;

/**
 * A correction to the extra cover's start date, priced before anything is saved. B4's preview shows it and saving
 * posts exactly this: the undo of the live charge, the corrected charge, and any refund of what is now overpaid.
 *
 * @param netCents         rebill + undo: positive means the customer owes more, negative means we owe them
 * @param balanceAfterCents the policy's balance once both lines post, before any refund settles
 * @param refunds          what goes back to the card, latest payment first; empty unless the credit is larger
 *                         than the open balance
 */
public record CorrectionPlan(long policyId, long coverageId, LocalDate oldStart, LocalDate newStart, Undo undo,
        Rebill rebill, long netCents, long balanceAfterCents, List<PlannedRefund> refunds) {

    /** The live charge being undone, to the cent, with the arithmetic it was priced with. */
    public record Undo(long entryId, String entryDisplayId, String label, ProRata.Charge calc, long amountCents) {}

    /** The charge priced from the corrected start to the policy's end. */
    public record Rebill(String label, LocalDate from, LocalDate to, ProRata.Charge calc) {}

    /** One refund against one earlier payment, never more than that payment has left to give back. */
    public record PlannedRefund(long paymentId, String paymentDisplayId, String cardLast4, long amountCents) {}

    public long refundTotalCents() {
        return refunds.stream().mapToLong(PlannedRefund::amountCents).sum();
    }
}
