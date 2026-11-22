package app.plainly.policy;

import app.plainly.ledger.LedgerLine;
import java.time.LocalDate;
import java.util.List;

/** Read models for the broker's policy screens (B1 list, B3 workspace) and the policyholder home. */
public final class PolicyViews {

    private PolicyViews() {}

    public record LastAction(String type, LocalDate recordedOn) {}

    /** One row on B1. */
    public record ListRow(long policyId, String customerName, String ownerName, String policyNumber, String status,
            long balanceCents, boolean owesMoney, LastAction lastAction) {}

    public record Customer(String legalName, String businessDescription, String ownerName, String ownerEmail) {}

    public record Coverage(long id, CoverKind kind, String label, long annualPremiumCents, LocalDate from, LocalDate to) {}

    public record Transaction(String displayId, String type, LocalDate effectiveDate, LocalDate recordedOn) {}

    /** Everything B3 shows: header, covers for the timeline, and the ledger with where each line came from. */
    public record Detail(long policyId, String policyNumber, String status, LocalDate start, LocalDate end, LocalDate today,
            Customer customer, long balanceCents, List<Coverage> coverages, List<LedgerLine> ledger,
            List<Transaction> transactions) {}
}
