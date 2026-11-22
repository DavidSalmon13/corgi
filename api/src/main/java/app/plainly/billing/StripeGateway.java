package app.plainly.billing;

import java.util.Map;

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

    /** The client secret of an intent created earlier, so a reopened pay page can carry on with the same intent. */
    String clientSecret(String paymentIntentId);

    /** Confirms an intent with a card this customer used before. Stripe answers with the intent's new status. */
    ConfirmedIntent confirmWithSavedCard(String paymentIntentId, String paymentMethodId);

    record CreatedIntent(String id, String clientSecret) {}

    /** {@code status} is Stripe's (processing, succeeded, requires_action…); never trusted to settle a payment. */
    record ConfirmedIntent(String status, String clientSecret) {}
}
