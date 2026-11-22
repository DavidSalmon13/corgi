package app.plainly.billing;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentCreateParams;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The real Stripe, through stripe-java (API version 2026-11-18.endive). Test-mode keys only. With no secret or
 * publishable key configured, payments are switched off rather than failing at startup.
 */
@Component
class StripeJavaGateway implements StripeGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeJavaGateway.class);

    private final StripeClient client;
    private final String publishableKey;

    StripeJavaGateway(@Value("${plainly.stripe.secret-key}") String secretKey,
            @Value("${plainly.stripe.publishable-key}") String publishableKey) {
        this.client = secretKey.isBlank() || publishableKey.isBlank() ? null : new StripeClient(secretKey);
        this.publishableKey = publishableKey;
        if (client == null) {
            log.warn("Stripe keys aren't set (STRIPE_SECRET_KEY, STRIPE_PUBLISHABLE_KEY): card payments are off");
        } else if (!secretKey.startsWith("sk_test_")) {
            log.warn("STRIPE_SECRET_KEY isn't a test-mode key; this app is only meant for Stripe test mode");
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
    public ConfirmedIntent confirmWithSavedCard(String paymentIntentId, String paymentMethodId) {
        try {
            PaymentIntent intent = stripe().v1().paymentIntents().confirm(paymentIntentId,
                    PaymentIntentConfirmParams.builder().setPaymentMethod(paymentMethodId).build());
            return new ConfirmedIntent(intent.getStatus(), intent.getClientSecret());
        } catch (com.stripe.exception.CardException e) {
            // A decline is not an outage: Stripe also sends charge.failed, which settles the payment as FAILED.
            return new ConfirmedIntent("requires_payment_method", null);
        } catch (StripeException e) {
            throw unreachable("confirm with saved card", e);
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

    private static PaymentProblem unreachable(String what, StripeException e) {
        log.warn("Stripe call failed ({}): {} [request {}]", what, e.getMessage(), e.getRequestId());
        return PaymentProblem.stripeUnreachable();
    }
}
