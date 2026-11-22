package app.plainly.billing;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL on {@code payments}. Status changes ({@code mark…}) are called only from {@link StripeWebhookService}:
 * a payment's status follows Stripe's signed events, never the browser.
 */
@Repository
public class PaymentRepository {

    private final JdbcClient jdbc;

    public PaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** An earlier payment nobody finished paying (OPEN, or declined) whose intent may still be live at Stripe. */
    public record Unfinished(long id, String intentId, long amountCents, String status) {}

    /** A payment together with its policy, as the owner's pay page reads it. */
    public record Owned(long id, long policyId, String status, String intentId, long amountCents, String cardLast4,
            Instant createdAt) {}

    /** The card that paid last time, kept on the customer's Stripe customer. */
    public record CardOnFile(String paymentMethodId, String brand, String last4) {}

    /** A payment held for a webhook to settle. */
    public record Locked(long id, long policyId, String status) {}

    public boolean anyProcessing(long policyId) {
        return jdbc.sql("select exists (select 1 from payments where policy_id = ? and status = 'PROCESSING')")
                .param(policyId).query(Boolean.class).single();
    }

    /** Newest first. */
    public List<Unfinished> unfinished(long policyId) {
        return jdbc.sql("""
                select id, stripe_payment_intent_id, amount_cents, status from payments
                where policy_id = ? and status in ('OPEN', 'FAILED') and stripe_canceled_at is null
                order by id desc""")
                .param(policyId)
                .query((rs, n) -> new Unfinished(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4)))
                .list();
    }

    public void markStripeCanceled(long paymentId) {
        jdbc.sql("update payments set stripe_canceled_at = now() where id = ?").param(paymentId).update();
    }

    public boolean isStripeCanceled(long paymentId) {
        return jdbc.sql("select stripe_canceled_at is not null from payments where id = ?")
                .param(paymentId).query(Boolean.class).single();
    }

    /** Reserves the next payment id, so it can go to Stripe as the idempotency key before the row exists. */
    public long nextId() {
        return jdbc.sql("select nextval(pg_get_serial_sequence('payments', 'id'))").query(Long.class).single();
    }

    public void insert(long id, long policyId, long createdBy, String intentId, long amountCents) {
        jdbc.sql("""
                insert into payments (id, policy_id, created_by, stripe_payment_intent_id, amount_cents)
                values (?, ?, ?, ?, ?)""")
                .params(id, policyId, createdBy, intentId, amountCents).update();
    }

    /** The latest payment sent to the bank (not OPEN). */
    public Optional<PaymentService.PaymentSummary> latestSubmitted(long policyId) {
        return jdbc.sql("""
                select status, amount_cents, card_last4, created_at from payments
                where policy_id = ? and status <> 'OPEN' order by id desc limit 1""")
                .param(policyId)
                .query((rs, n) -> new PaymentService.PaymentSummary(rs.getString(1), rs.getLong(2), rs.getString(3),
                        rs.getObject(4, OffsetDateTime.class).toInstant()))
                .optional();
    }

    /** The payment if it is on one of this customer's policies. */
    public Optional<Owned> findOwned(long paymentId, long customerId) {
        return jdbc.sql("""
                select p.id, p.policy_id, p.status, p.stripe_payment_intent_id, p.amount_cents, p.card_last4, p.created_at
                from payments p join policies pol on pol.id = p.policy_id
                where p.id = ? and pol.customer_id = ?""")
                .params(paymentId, customerId)
                .query((rs, n) -> new Owned(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getLong(5), rs.getString(6), rs.getObject(7, OffsetDateTime.class).toInstant()))
                .optional();
    }

    /** The card from the customer's latest successful payment. */
    public Optional<CardOnFile> latestCardOnFile(long customerId) {
        return jdbc.sql("""
                select p.payment_method_id, p.card_brand, p.card_last4
                from payments p join policies pol on pol.id = p.policy_id
                where pol.customer_id = ? and p.status = 'SUCCEEDED' and p.payment_method_id is not null
                order by p.settled_at desc, p.id desc limit 1""")
                .param(customerId)
                .query((rs, n) -> new CardOnFile(rs.getString(1), rs.getString(2), rs.getString(3)))
                .optional();
    }

    /** Holds the payment row until the caller's transaction ends. */
    public Optional<Locked> lockByIntent(String intentId) {
        return jdbc.sql("select id, policy_id, status from payments where stripe_payment_intent_id = ? for update")
                .param(intentId)
                .query((rs, n) -> new Locked(rs.getLong("id"), rs.getLong("policy_id"), rs.getString("status")))
                .optional();
    }

    /** OPEN → PROCESSING; any other status is left alone. */
    public void markProcessing(String intentId) {
        jdbc.sql("update payments set status = 'PROCESSING' where stripe_payment_intent_id = ? and status = 'OPEN'")
                .param(intentId).update();
    }

    /** PROCESSING → FAILED, for an intent that ended without money moving. */
    public void markFailedIfProcessing(String intentId) {
        jdbc.sql("""
                update payments set status = 'FAILED', settled_at = now()
                where stripe_payment_intent_id = ? and status = 'PROCESSING'""")
                .param(intentId).update();
    }

    public void markSucceeded(long id, String paymentMethodId, String cardBrand, String cardLast4) {
        jdbc.sql("""
                update payments set status = 'SUCCEEDED', payment_method_id = ?, card_brand = ?, card_last4 = ?,
                    failure_reason = null, settled_at = now()
                where id = ?""")
                .params(paymentMethodId, cardBrand, cardLast4, id).update();
    }

    public void markFailed(long id, String cardBrand, String cardLast4, String failureReason) {
        jdbc.sql("""
                update payments set status = 'FAILED', card_brand = ?, card_last4 = ?, failure_reason = ?, settled_at = now()
                where id = ?""")
                .params(cardBrand, cardLast4, failureReason, id).update();
    }
}
