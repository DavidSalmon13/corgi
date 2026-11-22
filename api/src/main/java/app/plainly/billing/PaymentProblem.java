package app.plainly.billing;

/** A payment request we can't carry out, answered as {@code {"error", "message"}} with {@code status}. */
public class PaymentProblem extends RuntimeException {

    private final int status;
    private final String error;

    public PaymentProblem(int status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public int status() {
        return status;
    }

    public String error() {
        return error;
    }

    /** A payment the bank already has, or has paid, so asking for the money again would charge twice. */
    static PaymentProblem inFlight() {
        return new PaymentProblem(409, "payment_in_flight", "Your last payment is still on its way.");
    }

    /** Only for calls that can't move money (creating, reading or cancelling an intent), so "nothing was charged" is true. */
    static PaymentProblem stripeUnreachable() {
        return new PaymentProblem(502, "stripe_unavailable",
                "We couldn't reach our card processor. Nothing was charged. Try again in a moment.");
    }
}
