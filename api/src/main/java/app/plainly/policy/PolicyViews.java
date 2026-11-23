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

    /** Another policy this one points at: the cancelled policy a rewrite replaces, or the rewrite that replaced it. */
    public record PolicyLink(long policyId, String policyNumber) {}

    /**
     * One row on B1. {@code paymentWaiting}: a payment is with the bank and not settled yet. {@code replaces} and
     * {@code replacedBy} link a rewrite and the cancelled policy it replaced; null when there is none.
     */
    public record ListRow(long policyId, String customerName, String ownerName, String policyNumber, String status,
            long balanceCents, boolean owesMoney, boolean paymentWaiting, LastAction lastAction, long customerId,
            PolicyLink replaces, PolicyLink replacedBy) {}

    /**
     * One of a customer's policies, for the policyholder's "Your policies". {@code balanceCents} is the ledger balance
     * (negative while a refund is on its way); {@code refunds} are its refunds, so the caller can say what is pending.
     */
    public record OwnedPolicy(long policyId, String policyNumber, LocalDate start, LocalDate end, String status,
            LocalDate cancelledOn, long balanceCents, boolean paymentWaiting, List<RefundListing> refunds,
            PolicyLink replaces, PolicyLink replacedBy) {}

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
     * {@code customerId} opens "New policy for this customer"; {@code replaces}/{@code replacedBy} are the rewrite links.
     */
    public record Detail(long policyId, String policyNumber, String status, LocalDate start, LocalDate end,
            LocalDate cancelledOn, LocalDate today,
            Customer customer, long balanceCents, boolean paymentWaiting, List<Coverage> coverages, List<LedgerLine> ledger,
            List<Transaction> transactions, List<RefundListing> refunds, long customerId, PolicyLink replaces,
            PolicyLink replacedBy) {}
}
