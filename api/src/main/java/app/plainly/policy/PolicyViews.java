package app.plainly.policy;

import app.plainly.billing.RefundListing;
import app.plainly.ledger.LedgerLine;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Read models for the broker's policy screens (B1 list, B3 workspace) and the policyholder home. */
public final class PolicyViews {

    private PolicyViews() {}

    public record LastAction(String type, LocalDate recordedOn) {}

    /** One row on B1. {@code paymentWaiting}: a payment is with the bank and not settled yet. */
    public record ListRow(long policyId, String customerName, String ownerName, String policyNumber, String status,
            long balanceCents, boolean owesMoney, boolean paymentWaiting, LastAction lastAction) {}

    /** {@code ownerSignedUpAt} is null until the owner creates their account (real-sign-in AC11). */
    public record Customer(String legalName, String businessDescription, String ownerName, String ownerEmail,
            OffsetDateTime ownerSignedUpAt) {}

    /**
     * {@code previousStart}: when a correction replaced this cover, the start of the row it replaced; else null.
     * {@code endedOn}: the day a removal stopped this cover (start of that day); null while it runs.
     */
    public record Coverage(long id, CoverKind kind, String label, long annualPremiumCents, LocalDate from, LocalDate to,
            LocalDate previousStart, LocalDate endedOn) {}

    /** {@code reason}: the broker's words for a correction, shown to the customer as written; null otherwise. */
    public record Transaction(String displayId, String type, LocalDate effectiveDate, LocalDate recordedOn,
            String reason) {}

    /**
     * Everything B3 shows: header, covers for the timeline, and the ledger with where each line came from.
     * {@code end} is always the original term end; {@code cancelledOn} is the day all cover stopped, or null.
     */
    public record Detail(long policyId, String policyNumber, String status, LocalDate start, LocalDate end,
            LocalDate cancelledOn, LocalDate today,
            Customer customer, long balanceCents, boolean paymentWaiting, List<Coverage> coverages, List<LedgerLine> ledger,
            List<Transaction> transactions, List<RefundListing> refunds) {}
}
