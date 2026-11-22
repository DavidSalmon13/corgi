package app.plainly.billing;

/**
 * A refund as B3 lists it and P1 reads it: the card it goes back to, the correction that owed it, and where it
 * stands with Stripe. {@code stripeRefundId} is null until Stripe accepts an attempt; {@code failureReason} only
 * while it is FAILED.
 */
public record RefundListing(long id, String displayId, String status, long amountCents, String cardLast4, int attempt,
        String policyTransactionDisplayId, String stripeRefundId, String failureReason) {}
