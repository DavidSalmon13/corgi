package app.plainly.ledger;

/** What a ledger line records. Positive amounts mean the customer owes more; negative are credits. */
public enum EntryType {
    /** Premium for a cover over a span of days (from a policy transaction). */
    CHARGE,
    /** Undoes one earlier line, to the cent (from a correcting policy transaction). */
    REVERSAL,
    /** Money received (from a Stripe payment). */
    PAYMENT,
    /** Money sent back (from a Stripe refund). */
    REFUND,
    /** Gives back a cover's unused days (from a cancelling policy transaction); always negative. */
    CREDIT
}
