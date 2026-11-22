package app.plainly;

import app.plainly.billing.StripeGateway;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stands in for Stripe in tests: no network, predictable ids, and a record of every call so a test can
 * check what would have been sent. Shared by every test in the context, so tests look up their own calls.
 * Each intent has a Stripe-side status, which a test can move (say, to "succeeded" before the webhook lands).
 */
public class FakeStripeGateway implements StripeGateway {

    public record IntentCall(String intentId, String stripeCustomerId, long amountCents, String idempotencyKey,
            Map<String, String> metadata) {}

    public record ConfirmCall(String intentId, String paymentMethodId) {}

    private final AtomicInteger counter = new AtomicInteger();
    private final List<IntentCall> intents = new CopyOnWriteArrayList<>();
    private final List<ConfirmCall> confirms = new CopyOnWriteArrayList<>();
    private final Map<String, String> statuses = new ConcurrentHashMap<>();
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
        statuses.put(id, "requires_payment_method");
        return new CreatedIntent(id, id + "_secret_fake");
    }

    @Override
    public IntentState intentState(String paymentIntentId) {
        return new IntentState(statusOf(paymentIntentId), paymentIntentId + "_secret_fake");
    }

    @Override
    public IntentState cancelIfUnpaid(String paymentIntentId) {
        IntentState now = intentState(paymentIntentId);
        if (now.moneyMoving() || now.status().equals("canceled")) {
            return now;
        }
        statuses.put(paymentIntentId, "canceled");
        return intentState(paymentIntentId);
    }

    @Override
    public ConfirmedIntent confirmWithSavedCard(String paymentIntentId, String paymentMethodId) {
        confirms.add(new ConfirmCall(paymentIntentId, paymentMethodId));
        return new ConfirmedIntent("processing", paymentIntentId + "_secret_fake");
    }

    /** What Stripe would say about the intent now. */
    public String statusOf(String intentId) {
        return statuses.getOrDefault(intentId, "requires_payment_method");
    }

    /** Moves the intent on Stripe's side only, as if the bank answered and the webhook hasn't arrived yet. */
    public void setStatus(String intentId, String status) {
        statuses.put(intentId, status);
    }

    public List<IntentCall> intentsFor(String stripeCustomerId) {
        return intents.stream().filter(c -> c.stripeCustomerId().equals(stripeCustomerId)).toList();
    }

    public List<ConfirmCall> confirmsFor(String intentId) {
        return confirms.stream().filter(c -> c.intentId().equals(intentId)).toList();
    }

    // --- refunds ---

    public record RefundCall(String refundId, String intentId, long amountCents, String idempotencyKey,
            Map<String, String> metadata) {}

    private final List<RefundCall> refunds = new CopyOnWriteArrayList<>();
    private volatile String refundFailure;

    /** Makes every refund call fail with this message, as when Stripe refuses it. Pass null to stop. */
    public void failRefunds(String message) {
        this.refundFailure = message;
    }

    /** Records every call, refused or not, so a test can see each attempt's idempotency key. */
    @Override
    public CreatedRefund createRefund(String paymentIntentId, long amountCents, String idempotencyKey,
            Map<String, String> metadata) {
        String id = "re_fake_" + counter.incrementAndGet();
        refunds.add(new RefundCall(id, paymentIntentId, amountCents, idempotencyKey, Map.copyOf(metadata)));
        String failure = refundFailure;
        if (failure != null) {
            throw new RefundRefused(failure);
        }
        return new CreatedRefund(id, "pending");
    }

    public List<RefundCall> refundsFor(String intentId) {
        return refunds.stream().filter(c -> c.intentId().equals(intentId)).toList();
    }
}
