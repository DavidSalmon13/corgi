package app.plainly.billing;

import app.plainly.common.NotFoundException;
import app.plainly.common.Today;
import app.plainly.common.ValidationException;
import app.plainly.ledger.LedgerPostingService;
import app.plainly.ledger.NewEntry;
import app.plainly.notify.NotifyEvents;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Money going back to a card. A refund is recorded PENDING with the change that owes it, sent to Stripe after that
 * change commits, and settled only by a signed webhook.
 */
@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);
    static final String NOT_SENT = "Stripe couldn't send the money back to the card.";
    static final String ONLY_FAILED = "Only a refund that didn't go through can be retried.";

    private final RefundRepository refunds;
    private final PaymentRepository payments;
    private final StripeGateway stripe;
    private final LedgerPostingService ledger;
    private final Today today;
    /** Each send runs in its own transaction, so the refund row stays locked while Stripe is asked, then commits. */
    private final TransactionTemplate ownTransaction;
    private final ApplicationEventPublisher notify;

    public RefundService(RefundRepository refunds, PaymentRepository payments, StripeGateway stripe,
            LedgerPostingService ledger, Today today, PlatformTransactionManager transactions,
            ApplicationEventPublisher notify) {
        this.refunds = refunds;
        this.notify = notify;
        this.payments = payments;
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

    /**
     * What goes back to cards once a change leaves the policy at {@code balanceAfterCents}. A credit pays off the open
     * balance first, refunds already promised but not yet settled are spoken for, and only the rest is refunded,
     * latest payment first. Writes nothing, so a preview can call it.
     */
    public List<PlannedRefund> plan(long policyId, long balanceAfterCents) {
        long owed = -balanceAfterCents - refunds.unsettledTotal(policyId);
        return owed > 0
                ? allocate(owed, payments.succeededNewestFirst(policyId), refundedPerPayment(policyId))
                : List.of();
    }

    /**
     * Splits what we owe over the payments that took money, newest first, each capped at what it collected less
     * what has already gone (or is going) back from it. Anything left over stays as a credit on the balance.
     */
    static List<PlannedRefund> allocate(long owedCents, List<PaymentService.Collected> newestFirst,
            Map<Long, Long> alreadyRefunded) {
        List<PlannedRefund> planned = new ArrayList<>();
        long left = owedCents;
        for (PaymentService.Collected p : newestFirst) {
            if (left <= 0) {
                break;
            }
            long room = p.amountCents() - alreadyRefunded.getOrDefault(p.paymentId(), 0L);
            long take = Math.min(left, room);
            if (take > 0) {
                planned.add(new PlannedRefund(p.paymentId(), p.displayId(), p.cardLast4(), take));
                left -= take;
            }
        }
        return List.copyOf(planned);
    }

    /**
     * Records each planned refund as PENDING in the caller's transaction, against the change that owes it, and sends
     * them once that transaction commits, so money never leaves for a change that rolled back.
     */
    public List<RefundStarted> recordAndSendAfterCommit(long policyId, long policyTransactionId,
            List<PlannedRefund> planned) {
        List<RefundStarted> started = new ArrayList<>();
        List<Long> toSend = new ArrayList<>();
        for (PlannedRefund r : planned) {
            Recorded recorded = recordPending(policyId, r.paymentId(), policyTransactionId, r.amountCents());
            started.add(new RefundStarted(recorded.displayId(), r.paymentId(), r.amountCents()));
            toSend.add(recorded.id());
        }
        if (!toSend.isEmpty()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    toSend.forEach(RefundService.this::send);
                }
            });
        }
        return List.copyOf(started);
    }

    /** Records a refund as PENDING in the caller's transaction. Nothing goes to Stripe until {@link #send}. */
    public Recorded recordPending(long policyId, long paymentId, long policyTransactionId, long amountCents) {
        RefundRepository.Inserted r = refunds.insert(policyId, paymentId, policyTransactionId, amountCents);
        return new Recorded(r.id(), r.displayId());
    }

    /**
     * Asks Stripe to send a PENDING refund's current attempt, keyed "refund-&lt;id&gt;-&lt;attempt&gt;" so asking
     * again for the same attempt never sends twice. Stripe is first asked whether it already holds a refund for this
     * one (an earlier attempt we never heard back about); if so, that refund is adopted instead of making another.
     * Only a definite refusal marks the refund FAILED, for the broker to see and retry. If the answer is unclear (a
     * timeout, a 5xx) it stays PENDING with no Stripe id and {@link #resendUnsent} asks again with the same key.
     * Never throws: the change that owed the refund is already saved.
     */
    public void send(long refundId) {
        try {
            ownTransaction.executeWithoutResult(status -> sendLocked(refundId));
        } catch (RuntimeException e) {
            log.warn("Sending refund {} failed; it stays pending for the next sweep", refundId, e);
        }
    }

    /**
     * Sends again every PENDING refund that Stripe hasn't confirmed and was recorded more than {@code olderThan}
     * ago. Safe to run any time: each send locks the refund, looks for an existing Stripe refund first, and reuses
     * the attempt's key.
     */
    public void resendUnsent(Duration olderThan) {
        refunds.findUnsent(olderThan).forEach(this::send);
    }

    private void sendLocked(long refundId) {
        RefundRepository.Locked r = refunds.lockById(refundId).orElse(null);
        if (r == null || !"PENDING".equals(r.status()) || r.stripeRefundId() != null) {
            return;
        }
        try {
            StripeGateway.CreatedRefund created = stripe.findRefund(r.intentId(), r.id())
                    .orElseGet(() -> stripe.createRefund(r.intentId(), r.amountCents(),
                            "refund-" + r.id() + "-" + r.attempt(),
                            Map.of("refund_id", String.valueOf(r.id()), "policy_id", String.valueOf(r.policyId()))));
            refunds.markSent(r.id(), created.id());
        } catch (StripeGateway.RefundRefused e) {
            refunds.markFailed(r.id(), e.getMessage());
        } catch (StripeGateway.RefundOutcomeUnknown e) {
            log.warn("No clear answer from Stripe for refund {} (attempt {}); it stays pending and is asked again",
                    r.displayId(), r.attempt());
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

    /** A Stripe refund as an event describes it: its id, the payment intent it refunds and how much. */
    public record SeenRefund(String id, String intentId, long amountCents) {}

    /** The policy a refund belongs to, so the webhook can take the policy lock before settling it. */
    public Optional<Long> policyOf(long refundId) {
        return refunds.find(refundId).map(RefundRepository.Locked::policyId);
    }

    /**
     * Stripe says refund {@code refundId} reached the card. On the PENDING → SUCCEEDED step only, a REFUND line
     * traced to the refund and to {@code eventId} takes the credit off the balance, so a second "succeeded" event
     * posts nothing, and the owner is emailed one receipt after commit. Joins the webhook's transaction, whose caller
     * must already hold the policy lock so a correction never reads the balance and the unsettled refunds on either
     * side of this.
     */
    @Transactional
    public void settle(long refundId, SeenRefund seen, String eventId) {
        Optional<RefundRepository.Locked> found = lockForEvent(refundId, seen);
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
        // The owner gets a receipt once this commits; a refund that fails never gets here (emails spec AC4).
        notify.publishEvent(new NotifyEvents.RefundSettled(r.id(), r.policyId(), r.amountCents(), r.cardLast4(),
                r.causeType()));
    }

    /** Stripe says refund {@code refundId} didn't reach the card. A PENDING refund becomes FAILED with the reason. */
    @Transactional
    public void fail(long refundId, SeenRefund seen, String failureReason) {
        lockForEvent(refundId, seen).ifPresent(r -> refunds.markFailed(r.id(),
                failureReason == null || failureReason.isBlank() ? NOT_SENT : failureReason));
    }

    /**
     * The refund an event is about, held until the webhook commits. An event for a refund we never started, for an
     * earlier attempt that a retry has replaced, or whose payment or amount isn't ours, is ignored.
     */
    private Optional<RefundRepository.Locked> lockForEvent(long refundId, SeenRefund seen) {
        Optional<RefundRepository.Locked> found = refunds.lockById(refundId);
        if (found.isEmpty()) {
            log.info("Stripe refund {} isn't one of ours (refund id {}); ignoring", seen.id(), refundId);
            return Optional.empty();
        }
        RefundRepository.Locked r = found.get();
        if (r.stripeRefundId() != null && !r.stripeRefundId().equals(seen.id())) {
            log.info("Ignoring Stripe refund {}: refund {} is now on attempt {} ({})", seen.id(), r.displayId(),
                    r.attempt(), r.stripeRefundId());
            return Optional.empty();
        }
        if (!r.intentId().equals(seen.intentId()) || r.amountCents() != seen.amountCents()) {
            log.warn("Ignoring Stripe refund {}: it is {} on {}, but refund {} is {} on {}", seen.id(),
                    seen.amountCents(), seen.intentId(), r.displayId(), r.amountCents(), r.intentId());
            return Optional.empty();
        }
        return found;
    }
}
