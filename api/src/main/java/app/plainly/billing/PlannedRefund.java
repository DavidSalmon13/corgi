package app.plainly.billing;

/**
 * One refund a change will owe, against one earlier payment, never more than that payment has left to give back.
 * Priced by {@link RefundService#plan} for a preview, recorded by {@link RefundService#recordAndSendAfterCommit}.
 */
public record PlannedRefund(long paymentId, String paymentDisplayId, String cardLast4, long amountCents) {}
