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

    static PaymentProblem stripeUnreachable() {
        return new PaymentProblem(502, "stripe_unavailable",
                "We couldn't reach our card processor. Nothing was charged. Try again in a moment.");
    }
}
