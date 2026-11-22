package app.plainly.ledger;

import app.plainly.pricing.ProRata;
import java.time.LocalDate;

/**
 * A ledger line about to be posted. Pro-rata lines carry their arithmetic ({@code calc}) so the
 * UI can show "annual × days ÷ days in term" next to the amount.
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
        Long reversesEntryId) {

    public static NewEntry charge(
            LocalDate entryDate, String description, Long coverageId, ProRata.Charge calc, LocalDate from, LocalDate to) {
        return new NewEntry(EntryType.CHARGE, entryDate, description, calc.amountCents(), coverageId, calc, from, to, null);
    }

    /** {@code amountCents} must be the exact negative of the original line; the poster checks. */
    public static NewEntry reversal(LocalDate entryDate, String description, long reversesEntryId, long amountCents) {
        return new NewEntry(EntryType.REVERSAL, entryDate, description, amountCents, null, null, null, null, reversesEntryId);
    }
}
