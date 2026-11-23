package app.plainly.notify;

import app.plainly.common.Today;
import app.plainly.ledger.LedgerQueryService;
import app.plainly.notify.EmailTemplates.PaymentReceipt;
import app.plainly.notify.EmailTemplates.RefundReason;
import app.plainly.notify.EmailTemplates.RefundReceipt;
import app.plainly.notify.NotifyEvents.PaymentSettled;
import app.plainly.notify.NotifyEvents.PolicyIssued;
import app.plainly.notify.NotifyEvents.RefundSettled;
import app.plainly.policy.PolicyService;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Emails the policyholder about their money: the policy issued, a payment taken, a refund sent. Each email is
 * written and claimed in the transaction of the change that caused it, so it says exactly what was committed, and
 * sent through the {@link EmailGateway} only once that transaction commits. So a change that rolls back sends
 * nothing, a replayed webhook finds the email already claimed and sends nothing more, and a failed send never undoes
 * the change: its row is just marked FAILED. If the process dies between commit and send, the row stays PENDING;
 * that loss is accepted for the demo (DECISIONS).
 *
 * <p>Writing and claiming run under a savepoint, so an email that can't be written (a missing policy, a database
 * error on its row) is logged and dropped without rolling back the payment or policy behind it. Sending runs on a
 * small background pool, so a slow or unreachable Resend never holds the broker's request or Stripe's webhook.
 */
@Service
public class NotifyService implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);

    private final SentEmailRepository emails;
    private final EmailGateway gateway;
    private final EmailTemplates templates;
    private final PolicyService policies;
    private final LedgerQueryService ledger;
    private final Today today;
    private final TransactionTemplate ownTransaction;
    /** A savepoint inside the caller's transaction: rolling it back leaves the caller's change intact. */
    private final TransactionTemplate savepoint;
    /** Sends after commit off the request thread; null when sending in line (tests). */
    private final ThreadPoolTaskExecutor sender;

    public NotifyService(SentEmailRepository emails, EmailGateway gateway, EmailTemplates templates,
            PolicyService policies, LedgerQueryService ledger, Today today, PlatformTransactionManager transactions,
            @Value("${plainly.email.send-async:true}") boolean sendAsync) {
        this.emails = emails;
        this.gateway = gateway;
        this.templates = templates;
        this.policies = policies;
        this.ledger = ledger;
        this.today = today;
        this.ownTransaction = new TransactionTemplate(transactions);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.savepoint = new TransactionTemplate(transactions);
        this.savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        this.sender = sendAsync ? pool() : null;
    }

    private static ThreadPoolTaskExecutor pool() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setThreadNamePrefix("email-");
        pool.setCorePoolSize(2);
        pool.setMaxPoolSize(2);
        pool.setQueueCapacity(200);
        // On shutdown, finish what was handed over rather than leave it PENDING.
        pool.setWaitForTasksToCompleteOnShutdown(true);
        pool.setAwaitTerminationSeconds(20);
        pool.initialize();
        return pool;
    }

    @Override
    public void destroy() {
        if (sender != null) {
            sender.shutdown();
        }
    }

    @EventListener
    public void on(PolicyIssued e) {
        queue(EmailKind.POLICY_ISSUED, e.policyId(), () -> {
            PolicyService.Contact c = policies.contact(e.policyId());
            String broker = policies.issuedBy(e.policyId()).orElse(null);
            return templates.policyIssued(new EmailTemplates.PolicyIssued(c.ownerName(), c.ownerEmail(),
                    c.legalName(), c.policyNumber(), c.start(), c.end(), e.priceForTheYearCents(), broker));
        });
    }

    @EventListener
    public void on(PaymentSettled e) {
        queue(EmailKind.PAYMENT_RECEIPT, e.paymentId(), () -> {
            PolicyService.Contact c = policies.contact(e.policyId());
            return templates.paymentReceipt(new PaymentReceipt(c.ownerName(), c.ownerEmail(), e.amountCents(),
                    e.cardLast4(), today.date(), ledger.balance(e.policyId())));
        });
    }

    @EventListener
    public void on(RefundSettled e) {
        queue(EmailKind.REFUND_RECEIPT, e.refundId(), () -> {
            PolicyService.Contact c = policies.contact(e.policyId());
            RefundReason reason = "CANCEL".equals(e.causeType())
                    ? RefundReason.CANCELLATION : RefundReason.CORRECTION;
            return templates.refundReceipt(new RefundReceipt(c.ownerName(), c.ownerEmail(), e.amountCents(),
                    e.cardLast4(), reason));
        });
    }

    /** An email written and claimed in the caller's transaction, waiting for it to commit. */
    private record Claimed(long id, UUID sendKey, EmailMessage message) {}

    /**
     * Writes and claims the email under a savepoint in the caller's transaction and, if this is its first claim,
     * sends it after commit. If writing or claiming fails, only the savepoint rolls back: the change is saved
     * without its email.
     */
    private void queue(EmailKind kind, long sourceId, Supplier<EmailMessage> write) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("An email must be claimed in the transaction of the change behind it");
        }
        Optional<Claimed> claimed;
        try {
            claimed = savepoint.execute(s -> {
                EmailMessage message = write.get();
                return emails.claim(kind, sourceId, message.to(), message.subject())
                        .map(c -> new Claimed(c.id(), c.sendKey(), message));
            });
        } catch (RuntimeException e) {
            log.warn("Couldn't write {} for {}; the change is saved without it", kind, sourceId, e);
            return;
        }
        if (claimed == null || claimed.isEmpty()) {
            log.info("{} for {} was already claimed; not sending it again", kind, sourceId);
            return;
        }
        Claimed c = claimed.get();
        // Resend remembers keys per account for 24 hours, and other databases reuse our source ids (DECISIONS).
        String idempotencyKey = kind + "/" + sourceId + "/" + c.sendKey();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendLater(c.id(), idempotencyKey, c.message());
            }
        });
    }

    /** Hands the send to the background pool; if the pool is full, the row stays PENDING like a lost send. */
    private void sendLater(long id, String idempotencyKey, EmailMessage message) {
        if (sender == null) {
            deliver(id, idempotencyKey, message);
            return;
        }
        try {
            sender.execute(() -> deliver(id, idempotencyKey, message));
        } catch (TaskRejectedException e) {
            log.warn("Email {} wasn't sent: too many emails waiting; it stays PENDING", idempotencyKey);
        }
    }

    /** Sends one claimed email and records how it ended. Never throws: the change behind it is already saved. */
    private void deliver(long id, String idempotencyKey, EmailMessage message) {
        try {
            if (!gateway.enabled()) {
                ownTransaction.executeWithoutResult(s -> emails.markSkipped(id));
                return;
            }
            String resendId;
            try {
                resendId = gateway.send(message, idempotencyKey);
            } catch (RuntimeException e) {
                log.warn("Email {} wasn't sent: {}", idempotencyKey, e.getMessage());
                ownTransaction.executeWithoutResult(s -> emails.markFailed(id, e.getMessage()));
                return;
            }
            ownTransaction.executeWithoutResult(s -> emails.markSent(id, resendId));
        } catch (RuntimeException e) {
            log.warn("Couldn't record how email {} ended", idempotencyKey, e);
        }
    }
}
