package app.plainly;

import app.plainly.billing.StripeGateway;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** A refund as Stripe holds it: only refunds Stripe actually made are here. */
    private record HeldRefund(String id, String intentId, Map<String, String> metadata) {}

    /** How the next refund calls go wrong, as a test sets it. */
    public enum RefundTimeout {
        /** Stripe never got the request. */
        NOT_MADE,
        /** Stripe made the refund, but the answer never came back. */
        MADE_ANYWAY
    }

    private final List<RefundCall> refunds = new CopyOnWriteArrayList<>();
    private final List<HeldRefund> held = new CopyOnWriteArrayList<>();
    private final Map<String, String> refundStatuses = new ConcurrentHashMap<>();
    private final Map<String, String> byKey = new ConcurrentHashMap<>();
    private volatile String refundFailure;
    private volatile RefundTimeout refundTimeout;
    private volatile boolean lookupsDown;

    /** Makes every refund call fail with this message, as when Stripe refuses it. Pass null to stop. */
    public void failRefunds(String message) {
        this.refundFailure = message;
    }

    /** Makes every refund call time out, as {@code how} says. Pass null to stop. */
    public void timeOutRefunds(RefundTimeout how) {
        this.refundTimeout = how;
    }

    /** Makes every refund lookup fail as if Stripe were unreachable. */
    public void refundLookupsDown(boolean down) {
        this.lookupsDown = down;
    }

    /** Moves a refund on Stripe's side, as when Stripe says it failed. */
    public void setRefundStatus(String stripeRefundId, String status) {
        refundStatuses.put(stripeRefundId, status);
    }

    /**
     * Records every call, refused or not, so a test can see each attempt's idempotency key. Like Stripe, a key it
     * has already made a refund for returns that refund instead of making another.
     */
    @Override
    public CreatedRefund createRefund(String paymentIntentId, long amountCents, String idempotencyKey,
            Map<String, String> metadata) {
        String id = byKey.getOrDefault(idempotencyKey, "re_fake_" + counter.incrementAndGet());
        refunds.add(new RefundCall(id, paymentIntentId, amountCents, idempotencyKey, Map.copyOf(metadata)));
        String failure = refundFailure;
        if (failure != null) {
            throw new RefundRefused(failure);
        }
        RefundTimeout timeout = refundTimeout;
        if (timeout == RefundTimeout.NOT_MADE) {
            throw new RefundOutcomeUnknown("read timed out");
        }
        if (byKey.putIfAbsent(idempotencyKey, id) == null) {
            held.add(new HeldRefund(id, paymentIntentId, Map.copyOf(metadata)));
            refundStatuses.put(id, "pending");
        }
        if (timeout == RefundTimeout.MADE_ANYWAY) {
            throw new RefundOutcomeUnknown("read timed out");
        }
        return new CreatedRefund(id, refundStatuses.get(id));
    }

    @Override
    public Optional<CreatedRefund> findRefund(String paymentIntentId, long refundId) {
        if (lookupsDown) {
            throw new RefundOutcomeUnknown("couldn't reach Stripe");
        }
        return held.stream()
                .filter(h -> h.intentId().equals(paymentIntentId)
                        && String.valueOf(refundId).equals(h.metadata().get("refund_id")))
                .map(h -> new CreatedRefund(h.id(), refundStatuses.get(h.id())))
                .filter(r -> !r.status().equals("failed") && !r.status().equals("canceled"))
                .findFirst();
    }

    /** Every createRefund call for the intent, made or not. */
    public List<RefundCall> refundsFor(String intentId) {
        return refunds.stream().filter(c -> c.intentId().equals(intentId)).toList();
    }

    /** The refunds Stripe actually made on the intent. */
    public List<String> refundsMadeOn(String intentId) {
        return held.stream().filter(h -> h.intentId().equals(intentId)).map(HeldRefund::id).toList();
    }
}
