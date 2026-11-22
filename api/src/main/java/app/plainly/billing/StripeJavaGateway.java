package app.plainly.billing;

import com.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The real Stripe, through stripe-java (API version 2026-11-18.endive). Payments are switched on only when the
 * secret key, the publishable key and the webhook signing secret are all set and the keys are test-mode keys.
 * Otherwise the app still starts and says payments are off: without the webhook secret no payment could ever be
 * recorded, and this demo must never take real money.
 */
@Component
class StripeJavaGateway implements StripeGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeJavaGateway.class);

    private final StripeClient client;
    private final String publishableKey;

    StripeJavaGateway(@Value("${plainly.stripe.secret-key}") String secretKey,
            @Value("${plainly.stripe.publishable-key}") String publishableKey,
            @Value("${plainly.stripe.webhook-secret}") String webhookSecret) {
        this.publishableKey = publishableKey;
        if (secretKey.isBlank() || publishableKey.isBlank() || webhookSecret.isBlank()) {
            log.warn("Stripe isn't fully set up (STRIPE_SECRET_KEY, STRIPE_PUBLISHABLE_KEY and STRIPE_WEBHOOK_SECRET "
                    + "are all needed): card payments are off");
            this.client = null;
        } else if (!secretKey.startsWith("sk_test_") || !publishableKey.startsWith("pk_test_")) {
            log.error("The Stripe keys aren't test-mode keys (sk_test_…, pk_test_…). This demo only takes test "
                    + "payments, so card payments are off");
            this.client = null;
        } else {
            this.client = new StripeClient(secretKey);
        }
    }

    @Override
    public boolean enabled() {
        return client != null;
    }

    @Override
    public String publishableKey() {
        return publishableKey;
    }

    @Override
    public String createCustomer(long customerId, String name, String email) {
        try {
            Customer customer = stripe().v1().customers().create(
                    CustomerCreateParams.builder().setName(name).setEmail(email)
                            .putMetadata("plainly_customer_id", String.valueOf(customerId)).build(),
                    RequestOptions.builder().setIdempotencyKey("customer-" + customerId).build());
            return customer.getId();
        } catch (StripeException e) {
            throw unreachable("create customer", e);
        }
    }

    @Override
    public CreatedIntent createPaymentIntent(String stripeCustomerId, long amountCents, String idempotencyKey,
            Map<String, String> metadata) {
        try {
            PaymentIntent intent = stripe().v1().paymentIntents().create(
                    PaymentIntentCreateParams.builder()
                            .setAmount(amountCents)
                            .setCurrency("usd")
                            .setCustomer(stripeCustomerId)
                            .setDescription("Plainly insurance premium")
                            // Keep the card on the customer so the pay page can offer it next time.
                            .setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.ON_SESSION)
                            // Cards only, and no redirect-based methods, so paying never leaves the page.
                            .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                    .build())
                            .putAllMetadata(metadata)
                            .build(),
                    RequestOptions.builder().setIdempotencyKey(idempotencyKey).build());
            return new CreatedIntent(intent.getId(), intent.getClientSecret());
        } catch (StripeException e) {
            throw unreachable("create payment intent", e);
        }
    }

    @Override
    public IntentState intentState(String paymentIntentId) {
        try {
            PaymentIntent intent = stripe().v1().paymentIntents().retrieve(paymentIntentId);
            return new IntentState(intent.getStatus(), intent.getClientSecret());
        } catch (StripeException e) {
            throw unreachable("retrieve payment intent", e);
        }
    }

    @Override
    public IntentState cancelIfUnpaid(String paymentIntentId) {
        IntentState now = intentState(paymentIntentId);
        if (now.moneyMoving() || now.status().equals("canceled")) {
            return now;
        }
        try {
            PaymentIntent canceled = stripe().v1().paymentIntents().cancel(paymentIntentId,
                    PaymentIntentCancelParams.builder()
                            .setCancellationReason(PaymentIntentCancelParams.CancellationReason.ABANDONED).build(),
                    RequestOptions.builder().setIdempotencyKey("cancel-" + paymentIntentId).build());
            return new IntentState(canceled.getStatus(), canceled.getClientSecret());
        } catch (StripeException e) {
            // The bank may have taken it between our read and the cancel: read again rather than guess.
            log.info("Couldn't cancel Stripe intent {}: {}", paymentIntentId, e.getMessage());
            return intentState(paymentIntentId);
        }
    }

    @Override
    public ConfirmedIntent confirmWithSavedCard(String paymentIntentId, String paymentMethodId) {
        try {
            PaymentIntent intent = stripe().v1().paymentIntents().confirm(paymentIntentId,
                    PaymentIntentConfirmParams.builder().setPaymentMethod(paymentMethodId).build());
            return new ConfirmedIntent(intent.getStatus(), intent.getClientSecret());
        } catch (com.stripe.exception.CardException e) {
            // A decline is not an outage: Stripe also sends charge.failed, which settles the payment as FAILED.
            return new ConfirmedIntent("requires_payment_method", null);
        } catch (StripeException e) {
            throw confirmProblem(e);
        }
    }

    @Override
    public CreatedRefund createRefund(String paymentIntentId, long amountCents, String idempotencyKey,
            Map<String, String> metadata) {
        if (client == null) {
            throw new RefundRefused("Card payments aren't set up on this demo, so Stripe couldn't be asked to send it.");
        }
        try {
            Refund refund = client.v1().refunds().create(
                    RefundCreateParams.builder()
                            .setPaymentIntent(paymentIntentId)
                            .setAmount(amountCents)
                            .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
                            .putAllMetadata(metadata)
                            .build(),
                    RequestOptions.builder().setIdempotencyKey(idempotencyKey).build());
            return new CreatedRefund(refund.getId(), refund.getStatus());
        } catch (StripeException e) {
            log.warn("Stripe refund failed: {} [request {}]", e.getMessage(), e.getRequestId());
            String reason = e.getStripeError() != null && e.getStripeError().getMessage() != null
                    ? e.getStripeError().getMessage()
                    : "We couldn't reach Stripe.";
            throw new RefundRefused(reason);
        }
    }

    private StripeClient stripe() {
        if (client == null) {
            throw paymentsOff();
        }
        return client;
    }

    static PaymentProblem paymentsOff() {
        return new PaymentProblem(503, "payments_off",
                "Card payments aren't set up on this demo yet, so you can't pay here right now.");
    }

    /**
     * Why a saved-card confirm didn't go through. Stripe refusing it because the intent was already paid means an
     * earlier payment is on its way. Any other failure may have happened after the bank took the money, so it
     * never claims that nothing was charged.
     */
    static PaymentProblem confirmProblem(StripeException e) {
        if (e instanceof InvalidRequestException && "payment_intent_unexpected_state".equals(e.getCode())) {
            return PaymentProblem.inFlight();
        }
        log.warn("Stripe confirm failed: {} [request {}]", e.getMessage(), e.getRequestId());
        return new PaymentProblem(502, "confirm_unknown",
                "We couldn't hear back from your bank. Check your cover in a minute before you try again.");
    }

    private static PaymentProblem unreachable(String what, StripeException e) {
        log.warn("Stripe call failed ({}): {} [request {}]", what, e.getMessage(), e.getRequestId());
        return PaymentProblem.stripeUnreachable();
    }
}
