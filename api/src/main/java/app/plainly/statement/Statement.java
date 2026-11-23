package app.plainly.statement;

import app.plainly.ledger.EntryType;
import app.plainly.me.MyCoverService;
import java.time.LocalDate;
import java.util.List;

/**
 * P3 "Your statement" for one policy, role-neutral so the broker's history can reuse it: every ledger line oldest
 * first with its running balance, money still on its way (outside the balance), one card per correction, and a
 * "how we got this" trace behind every amount (P4). Amounts are integer cents; positive means the customer owes more.
 */
public record Statement(Header header, Banner banner, List<Row> rows, List<Correction> corrections, LocalDate today) {

    /**
     * {@code balanceCents} is the ledger balance: positive is owed, negative is owed back. {@code inceptionDate} →
     * {@code expiryDate} is the policy term (end not counted); {@code statementDate} is the day the statement was
     * made (today). {@code dueDate} is the posting date of the oldest line later payments and credits haven't
     * covered, oldest first ("due on receipt"); null when nothing is owed.
     */
    public record Header(String businessName, String policyNumber, long balanceCents, LocalDate inceptionDate,
            LocalDate expiryDate, LocalDate statementDate, LocalDate dueDate) {}

    /** The one thing that matters now, in P3's states. */
    public enum BannerState {
        /** Money is due; {@code dueReason} says why (the same reasons as P1). */
        OWES,
        /** A payment is with the bank and not settled yet. */
        PAYMENT_WAITING,
        /** We owe the customer money that is on its way to their card. */
        REFUND_WAITING,
        /** Nothing owed either way. */
        PAID_UP
    }

    /**
     * {@code amountCents}: what is owed (OWES), waiting at the bank (PAYMENT_WAITING) or on its way back
     * (REFUND_WAITING); 0 when paid up. {@code dueReason} only for OWES. {@code cardLast4} for the waiting states.
     */
    public record Banner(BannerState state, long amountCents, MyCoverService.DueReason dueReason, String cardLast4) {}

    /** Where a payment or refund row stands. Null for charges, credits and reversals. */
    public enum MoneyStatus {
        /** The bank confirmed the payment. */
        PAID,
        /** The bank said no: a $0.00 line, no money moved. */
        DECLINED,
        /** The payment is with the bank (a pending row). */
        WAITING,
        /** Stripe confirmed the refund. */
        REFUND_SENT,
        /** The refund is recorded but not settled (a pending row), including while a failed one waits for a retry. */
        REFUND_ON_ITS_WAY
    }

    /**
     * One statement row. A ledger line has its {@code id} (LE-…) and {@code balanceAfterCents}; a pending row has
     * {@code pending = true}, the id of the payment or refund it shows, and the balance unchanged from the row before.
     * {@code undoneOn}: the day a later reversal undid this line, else null. {@code correctionId}: the correcting
     * change (PTX-…) this row belongs to, as its original, reversal or corrected line; else null.
     */
    public record Row(String id, EntryType kind, LocalDate date, String title, long amountCents,
            long balanceAfterCents, boolean pending, MoneyStatus status, LocalDate undoneOn, String correctionId,
            Trace trace) {}

    /**
     * One CORRECT change as a single card. {@code dayDelta} is positive when cover now starts earlier (more days).
     * {@code perDayCents} is the annual price ÷ days in the term, only when that is a whole number of cents.
     * {@code balanceAfterCents} is the running balance after the card's last line (negative is a credit), so the
     * card shows one balance instead of one per line.
     */
    public record Correction(String transactionDisplayId, LocalDate recordedOn, LocalDate oldStart,
            LocalDate newStart, long dayDelta, long netCents, Long perDayCents, String reason, String actorName,
            String originalLineId, String reversalLineId, String rebillLineId, long balanceAfterCents) {}

    /** P4 "How we got this" for one row. */
    public record Trace(EntryType kind, Maths maths, Cause cause, Money money, List<Ref> refs) {}

    /**
     * The numbers behind the amount. A charge, credit or reversal: annual price × days ÷ days in term over
     * {@code from}–{@code to} (end not counted); a reversal carries the undone line's numbers with {@code undoes}
     * set. A payment or refund: the balance before and after it.
     */
    public record Maths(Long annualCents, Integer days, Integer termDays, LocalDate from, LocalDate to,
            long resultCents, boolean undoes, Long balanceBeforeCents, Long balanceAfterCents) {}

    /**
     * What caused the row: the policy change (type, effective date, recorded on, who, the broker's reason for a
     * correction or cancellation, and old → new start for a correction), or for a payment the person who paid.
     */
    public record Cause(String transactionType, LocalDate effectiveDate, LocalDate recordedOn, String actorName,
            String reason, LocalDate oldStart, LocalDate newStart) {}

    /** The payment or refund behind a row: card, date and where it stands. */
    public record Money(String cardLast4, LocalDate date, MoneyStatus status) {}

    public enum RefKind { CHANGE, LINE, UNDOES, STRIPE_PAYMENT, REFUND, STRIPE_REFUND, STRIPE_EVENT }

    public record Ref(RefKind kind, String id) {}
}
