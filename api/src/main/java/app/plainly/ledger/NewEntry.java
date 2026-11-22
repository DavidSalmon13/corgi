package app.plainly.ledger;

import app.plainly.pricing.ProRata;
import java.time.LocalDate;

/**
 * A ledger line about to be posted. Pro-rata lines carry their arithmetic ({@code calc}) so the
 * UI can show "annual × days ÷ days in term" next to the amount. Payment lines carry the payment and the
 * Stripe event behind them instead.
 */
public record NewEntry(
        EntryType type,
        LocalDate entryDate,
        String description,
        long amountCents,
        Long coverageId,
        ProRata.Charge calc,
        LocalDate coverFrom,
        LocalDate coverTo,
        Long reversesEntryId,
        Long paymentId,
        String stripeEventId) {

    public static NewEntry charge(
            LocalDate entryDate, String description, Long coverageId, ProRata.Charge calc, LocalDate from, LocalDate to) {
        return new NewEntry(EntryType.CHARGE, entryDate, description, calc.amountCents(), coverageId, calc, from, to, null, null, null);
    }

    /** {@code amountCents} must be the exact negative of the original line; the poster checks. */
    public static NewEntry reversal(LocalDate entryDate, String description, long reversesEntryId, long amountCents) {
        return new NewEntry(EntryType.REVERSAL, entryDate, description, amountCents, null, null, null, null, reversesEntryId, null, null);
    }

    /**
     * Money received for a payment, settled by Stripe event {@code stripeEventId}. A credit, so never positive;
     * a declined attempt is a $0.00 line, which keeps the attempt visible without moving the balance.
     */
    public static NewEntry payment(LocalDate entryDate, String description, long amountCents, long paymentId,
            String stripeEventId) {
        if (amountCents > 0) {
            throw new IllegalArgumentException("a payment is a credit: its amount can't be positive");
        }
        return new NewEntry(EntryType.PAYMENT, entryDate, description, amountCents, null, null, null, null, null,
                paymentId, stripeEventId);
    }
}
