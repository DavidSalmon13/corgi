package app.plainly.billing;

import app.plainly.common.NotFoundException;
import app.plainly.common.Today;
import app.plainly.common.ValidationException;
import app.plainly.ledger.LedgerPostingService;
import app.plainly.ledger.NewEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Money going back to a card. A refund is recorded PENDING with the change that owes it, sent to Stripe after that
 * change commits, and settled only by a signed webhook.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);
    static final String UNREACHABLE = "We couldn't reach Stripe.";
    static final String NOT_SENT = "Stripe couldn't send the money back to the card.";
    static final String ONLY_FAILED = "Only a refund that didn't go through can be retried.";

    private final RefundRepository refunds;
    private final StripeGateway stripe;
    private final LedgerPostingService ledger;
    private final Today today;
    /** Each send runs in its own transaction, so the refund row stays locked while Stripe is asked, then commits. */
    private final TransactionTemplate ownTransaction;

    public RefundService(RefundRepository refunds, StripeGateway stripe, LedgerPostingService ledger, Today today,
            PlatformTransactionManager transactions) {
        this.refunds = refunds;
        this.stripe = stripe;
        this.ledger = ledger;
        this.today = today;
        this.ownTransaction = new TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Where a refund stands after a retry: our id, the id people see, its status and which attempt it is on. */
    public record Attempted(long id, String displayId, String status, int attempt) {}

    /** A refund that has been recorded: our row id and the id people see (RF-0001). */
    public record Recorded(long id, String displayId) {}

    /** Per payment id, what has gone or is on its way back to its card; failed refunds don't count. */
    public Map<Long, Long> refundedPerPayment(long policyId) {
        Map<Long, Long> result = new HashMap<>();
        refunds.refundedPerPayment(policyId).forEach(r -> result.put(r.paymentId(), r.amountCents()));
        return result;
    }

    /** The policy's refunds, oldest first, with the card each goes back to and the correction that owed it. */
    public List<RefundListing> listForPolicy(long policyId) {
        return refunds.findByPolicy(policyId);
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
        ownTransaction.executeWithoutResult(status -> sendLocked(refundId));
    }

    private void sendLocked(long refundId) {
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

    /**
     * Sends a FAILED refund again as its next attempt, with a new idempotency key, and returns where it stands. Two
     * retries at once start one attempt: the other finds it no longer FAILED.
     *
     * @throws NotFoundException when there is no such refund
     * @throws ValidationException when the refund hasn't failed (it is on its way, or already back on the card)
     */
    public Attempted retry(long refundId) {
        if (refunds.startNextAttempt(refundId).isEmpty()) {
            if (refunds.find(refundId).isEmpty()) {
                throw new NotFoundException("No refund " + refundId + ".");
            }
            throw new ValidationException(Map.of("refund", ONLY_FAILED));
        }
        send(refundId);
        RefundRepository.Locked now = refunds.find(refundId).orElseThrow();
        return new Attempted(now.id(), now.displayId(), now.status(), now.attempt());
    }

    /**
     * Which of our refunds a Stripe refund is: the {@code refund_id} we put in its metadata, or else the Stripe id a
     * send stored. Empty for a refund Plainly didn't start.
     */
    public Optional<Long> ourRefund(String metadataRefundId, String stripeRefundId) {
        if (metadataRefundId != null) {
            try {
                return Optional.of(Long.valueOf(metadataRefundId));
            } catch (NumberFormatException e) {
                log.info("Stripe refund {} has an unreadable refund_id {}", stripeRefundId, metadataRefundId);
            }
        }
        return stripeRefundId == null ? Optional.empty() : refunds.idByStripeRefundId(stripeRefundId);
    }

    /**
     * Stripe says refund {@code refundId} reached the card. On the PENDING → SUCCEEDED step only, a REFUND line
     * traced to the refund and to {@code eventId} takes the credit off the balance, so a second "succeeded" event
     * posts nothing. Joins the webhook's transaction.
     */
    @Transactional
    public void settle(long refundId, String stripeRefundId, String eventId) {
        Optional<RefundRepository.Locked> found = lockForEvent(refundId, stripeRefundId);
        if (found.isEmpty()) {
            return;
        }
        RefundRepository.Locked r = found.get();
        if (!refunds.markSucceeded(r.id())) {
            log.info("Refund {} is {}; not settling it again", r.displayId(), r.status());
            return;
        }
        String card = r.cardLast4() == null ? "card" : "card ending " + r.cardLast4();
        ledger.post(r.policyId(), null, List.of(NewEntry.refund(today.date(), "Refund to " + card, r.amountCents(),
                r.id(), eventId)));
    }

    /** Stripe says refund {@code refundId} didn't reach the card. A PENDING refund becomes FAILED with the reason. */
    @Transactional
    public void fail(long refundId, String stripeRefundId, String failureReason) {
        lockForEvent(refundId, stripeRefundId).ifPresent(r -> refunds.markFailed(r.id(),
                failureReason == null || failureReason.isBlank() ? NOT_SENT : failureReason));
    }

    /**
     * The refund an event is about, held until the webhook commits. An event for a refund we never started, or for
     * an earlier attempt that a retry has replaced, is ignored.
     */
    private Optional<RefundRepository.Locked> lockForEvent(long refundId, String stripeRefundId) {
        Optional<RefundRepository.Locked> found = refunds.lockById(refundId);
        if (found.isEmpty()) {
            log.info("Stripe refund {} isn't one of ours (refund id {}); ignoring", stripeRefundId, refundId);
            return Optional.empty();
        }
        RefundRepository.Locked r = found.get();
        if (r.stripeRefundId() != null && !r.stripeRefundId().equals(stripeRefundId)) {
            log.info("Ignoring Stripe refund {}: refund {} is now on attempt {} ({})", stripeRefundId, r.displayId(),
                    r.attempt(), r.stripeRefundId());
            return Optional.empty();
        }
        return found;
    }
}
