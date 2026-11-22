package app.plainly;

import app.plainly.billing.StripeGateway;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stands in for Stripe in tests: no network, predictable ids, and a record of every call so a test can
 * check what would have been sent. Shared by every test in the context, so tests look up their own calls.
 */
public class FakeStripeGateway implements StripeGateway {

    public record IntentCall(String intentId, String stripeCustomerId, long amountCents, String idempotencyKey,
            Map<String, String> metadata) {}

    public record ConfirmCall(String intentId, String paymentMethodId) {}

    private final AtomicInteger counter = new AtomicInteger();
    private final List<IntentCall> intents = new CopyOnWriteArrayList<>();
    private final List<ConfirmCall> confirms = new CopyOnWriteArrayList<>();
    private volatile boolean enabled = true;

    @Override
    public boolean enabled() {
        return enabled;
    }

    /** Turns payments off, as when the Stripe keys are missing. Always turn them back on in a finally block. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String publishableKey() {
        return StripeTestSupport.PUBLISHABLE_KEY;
    }

    @Override
    public String createCustomer(long customerId, String name, String email) {
        return "cus_fake_" + customerId + "_" + counter.incrementAndGet();
    }

    @Override
    public CreatedIntent createPaymentIntent(String stripeCustomerId, long amountCents, String idempotencyKey,
            Map<String, String> metadata) {
        String id = "pi_fake_" + counter.incrementAndGet();
        intents.add(new IntentCall(id, stripeCustomerId, amountCents, idempotencyKey, Map.copyOf(metadata)));
        return new CreatedIntent(id, id + "_secret_fake");
    }

    @Override
    public ConfirmedIntent confirmWithSavedCard(String paymentIntentId, String paymentMethodId) {
        confirms.add(new ConfirmCall(paymentIntentId, paymentMethodId));
        return new ConfirmedIntent("processing", paymentIntentId + "_secret_fake");
    }

    public List<IntentCall> intentsFor(String stripeCustomerId) {
        return intents.stream().filter(c -> c.stripeCustomerId().equals(stripeCustomerId)).toList();
    }

    public List<ConfirmCall> confirmsFor(String intentId) {
        return confirms.stream().filter(c -> c.intentId().equals(intentId)).toList();
    }
}
