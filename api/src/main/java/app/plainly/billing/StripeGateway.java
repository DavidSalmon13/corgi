package app.plainly.billing;

import java.util.Map;
import java.util.Optional;

/**
 * The calls Plainly makes to Stripe. Tests swap in a fake so they never reach the network; webhook
 * signatures are checked locally with the signing secret, so they are not part of this seam.
 */
public interface StripeGateway {

    /** False when the Stripe keys aren't configured: payments are switched off and the pay page says so. */
    boolean enabled();

    /** The browser-safe key the pay page needs to load the Stripe card form. */
    String publishableKey();

    /** Creates the Stripe customer for one of our customers, so a card can be kept for next time. Returns its id. */
    String createCustomer(long customerId, String name, String email);

    /**
     * Creates a PaymentIntent for {@code amountCents} that can be paid by card, and keeps the card for next time.
     * The idempotency key makes a retried call return the same intent instead of creating a second one.
     */
    CreatedIntent createPaymentIntent(String stripeCustomerId, long amountCents, String idempotencyKey,
            Map<String, String> metadata);

    /**
     * What Stripe says about an intent created earlier, read fresh. Used to reuse its client secret and to spot money
     * Stripe already took before its webhook arrives. It never settles a payment: only webhooks do.
     */
    IntentState intentState(String paymentIntentId);

    /**
     * Cancels an intent at Stripe unless money is already moving on it, so an old pay page can't be paid any more.
     * Returns the intent's state afterwards: "canceled", or the state that stopped the cancel.
     */
    IntentState cancelIfUnpaid(String paymentIntentId);

    /** Confirms an intent with a card this customer used before. Stripe answers with the intent's new status. */
    ConfirmedIntent confirmWithSavedCard(String paymentIntentId, String paymentMethodId);

    /**
     * Sends {@code amountCents} back to the card that paid {@code paymentIntentId}. The idempotency key is per refund
     * attempt, so a repeated call for the same attempt never sends the money twice. Stripe's answer only says the
     * refund was accepted: it settles through the {@code refund.*} webhooks.
     *
     * @throws RefundRefused when Stripe definitely won't send it (or payments are off); the message says why, for
     *     the broker
     * @throws RefundOutcomeUnknown when we can't tell whether Stripe made the refund (a timeout, a 5xx, a rate
     *     limit): it must be asked again with the same key, never a new one
     */
    CreatedRefund createRefund(String paymentIntentId, long amountCents, String idempotencyKey,
            Map<String, String> metadata);

    /**
     * The refund Stripe already holds on {@code paymentIntentId} for our refund {@code refundId} (matched on the
     * {@code refund_id} metadata we send), unless it failed or was canceled. Asked before every send, so an attempt
     * whose answer we never heard is adopted instead of sent twice.
     *
     * @throws RefundOutcomeUnknown when Stripe can't be asked right now
     */
    Optional<CreatedRefund> findRefund(String paymentIntentId, long refundId);

    record CreatedIntent(String id, String clientSecret) {}

    /** A refund Stripe accepted: its id (re_…) and status (usually pending or succeeded). */
    record CreatedRefund(String id, String status) {}

    /** Stripe didn't take the refund. The message is shown to the broker next to the failed refund. */
    class RefundRefused extends RuntimeException {
        public RefundRefused(String message) {
            super(message);
        }
    }

    /** We couldn't hear whether Stripe made the refund. Nothing is marked failed: the same attempt is asked again. */
    class RefundOutcomeUnknown extends RuntimeException {
        public RefundOutcomeUnknown(String message) {
            super(message);
        }
    }

    /** An intent as Stripe sees it now. */
    record IntentState(String status, String clientSecret) {
        /** The bank has the payment, or already paid it: asking for the money again would charge twice. */
        public boolean moneyMoving() {
            return status.equals("processing") || status.equals("succeeded") || status.equals("requires_capture");
        }
    }

    /** {@code status} is Stripe's (processing, succeeded, requires_action…); never trusted to settle a payment. */
    record ConfirmedIntent(String status, String clientSecret) {}
}
