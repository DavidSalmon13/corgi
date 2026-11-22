package app.plainly.billing;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Money going back to a card. A refund is recorded PENDING with the change that owes it, sent to Stripe after that
 * change commits, and settled only by a signed webhook.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);
    static final String UNREACHABLE = "We couldn't reach Stripe.";

    private final RefundRepository refunds;
    private final StripeGateway stripe;

    public RefundService(RefundRepository refunds, StripeGateway stripe) {
        this.refunds = refunds;
        this.stripe = stripe;
    }

    /** A refund that has been recorded: our row id and the id people see (RF-0001). */
    public record Recorded(long id, String displayId) {}

    /** Per payment id, what has gone or is on its way back to its card; failed refunds don't count. */
    public Map<Long, Long> refundedPerPayment(long policyId) {
        Map<Long, Long> result = new HashMap<>();
        refunds.refundedPerPayment(policyId).forEach(r -> result.put(r.paymentId(), r.amountCents()));
        return result;
    }

    /** Refunds already promised on the policy that the ledger doesn't show yet (pending, or failed awaiting retry). */
    public long unsettledTotal(long policyId) {
        return refunds.unsettledTotal(policyId);
    }

    /** Records a refund as PENDING in the caller's transaction. Nothing goes to Stripe until {@link #send}. */
    public Recorded recordPending(long policyId, long paymentId, long policyTransactionId, long amountCents) {
        RefundRepository.Inserted r = refunds.insert(policyId, paymentId, policyTransactionId, amountCents);
        return new Recorded(r.id(), r.displayId());
    }

    /**
     * Asks Stripe to send a PENDING refund's current attempt, keyed "refund-&lt;id&gt;-&lt;attempt&gt;" so a repeat of
     * the same attempt never sends twice. Called after the change that owes it has committed. If Stripe won't take
     * it, the refund is marked FAILED with the reason, for the broker to see and retry. Never throws: the change
     * that owed the refund is already saved.
     */
    public void send(long refundId) {
        RefundRepository.Locked r = refunds.lockById(refundId).orElse(null);
        if (r == null || !"PENDING".equals(r.status())) {
            return;
        }
        try {
            StripeGateway.CreatedRefund created = stripe.createRefund(r.intentId(), r.amountCents(),
                    "refund-" + r.id() + "-" + r.attempt(),
                    Map.of("refund_id", String.valueOf(r.id()), "policy_id", String.valueOf(r.policyId())));
            refunds.markSent(r.id(), created.id());
        } catch (StripeGateway.RefundRefused e) {
            refunds.markFailed(r.id(), e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Sending refund {} failed", r.displayId(), e);
            refunds.markFailed(r.id(), UNREACHABLE);
        }
    }
}
