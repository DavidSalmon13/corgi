package app.plainly.notify;

/**
 * What happened, published by the service that made it happen, inside its transaction. {@link NotifyService}
 * listens and emails the policyholder once that transaction commits. Publishing an event instead of calling
 * NotifyService keeps billing and policy free of a dependency on it (NotifyService reads from PolicyService, which
 * already depends on RefundService).
 */
public final class NotifyEvents {

    private NotifyEvents() {}

    /** The broker issued a policy. {@code priceForTheYearCents}: what all its covers cost for the term. */
    public record PolicyIssued(long policyId, long priceForTheYearCents) {}

    /** The bank took a payment, and its credit is on the ledger. */
    public record PaymentSettled(long paymentId, long policyId, long amountCents, String cardLast4) {}

    /**
     * A refund reached the card. {@code causeType}: the policy transaction that owed it: CORRECT, CANCEL or REMOVE_COVERAGE.
     */
    public record RefundSettled(long refundId, long policyId, long amountCents, String cardLast4, String causeType) {}
}
