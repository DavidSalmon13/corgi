package app.plainly.billing;

/** A refund recorded with a policy change. It is sent to Stripe after commit and settles only by webhook. */
public record RefundStarted(String displayId, long paymentId, long amountCents) {}
