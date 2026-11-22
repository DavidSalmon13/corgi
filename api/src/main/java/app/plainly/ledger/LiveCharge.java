package app.plainly.ledger;

import app.plainly.pricing.ProRata;
import java.time.LocalDate;

/** A cover's charge that nothing has undone, with the arithmetic it was priced with. It may be an ISSUE line. */
public record LiveCharge(long entryId, String displayId, long amountCents, ProRata.Charge calc, LocalDate coverFrom,
        LocalDate coverTo) {}
