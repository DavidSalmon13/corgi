package app.plainly.billing;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL on {@code refunds}. A refund is inserted PENDING with the correction that owes it; its status then follows
 * what Stripe says (a send that fails, or a signed webhook), never the browser.
 */
@Repository
public class RefundRepository {

    private final JdbcClient jdbc;

    public RefundRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * A refund held for a send or a webhook, with what's needed to act on it. {@code causeType}: the type of the
     * policy transaction that owed it (CORRECT or CANCEL).
     */
    public record Locked(long id, String displayId, long policyId, long paymentId, String intentId, String cardLast4,
            long amountCents, String status, int attempt, String stripeRefundId, String causeType) {}

    /** How much has gone, or is on its way, back to one payment's card (failed refunds don't count). */
    public record Refunded(long paymentId, long amountCents) {}

    /** Records a PENDING refund (attempt 1) and returns its id and display id. */
    public Inserted insert(long policyId, long paymentId, long policyTransactionId, long amountCents) {
        return jdbc.sql("""
                insert into refunds (policy_id, payment_id, policy_transaction_id, amount_cents)
                values (?, ?, ?, ?) returning id, display_id""")
                .params(policyId, paymentId, policyTransactionId, amountCents)
                .query((rs, n) -> new Inserted(rs.getLong(1), rs.getString(2)))
                .single();
    }

    public record Inserted(long id, String displayId) {}

    /** Our refund that Stripe knows as {@code stripeRefundId}, if one of our sends created it. */
    public Optional<Long> idByStripeRefundId(String stripeRefundId) {
        return jdbc.sql("select id from refunds where stripe_refund_id = ?")
                .param(stripeRefundId).query(Long.class).optional();
    }

    /** The refund as it stands now, without holding it. */
    public Optional<Locked> find(long id) {
        return select(id, "");
    }

    /** Holds the refund row until the caller's transaction ends. */
    public Optional<Locked> lockById(long id) {
        return select(id, " for update of r");
    }

    private Optional<Locked> select(long id, String lock) {
        return jdbc.sql("""
                select r.id, r.display_id, r.policy_id, r.payment_id, p.stripe_payment_intent_id, p.card_last4,
                       r.amount_cents, r.status, r.attempt, r.stripe_refund_id, t.type
                from refunds r
                join payments p on p.id = r.payment_id
                join policy_transactions t on t.id = r.policy_transaction_id
                where r.id = ?""" + lock)
                .param(id)
                .query((rs, n) -> new Locked(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getLong(4),
                        rs.getString(5), rs.getString(6), rs.getLong(7), rs.getString(8), rs.getInt(9),
                        rs.getString(10), rs.getString(11)))
                .optional();
    }

    /** Stripe accepted this attempt; keeps the refund id Stripe gave it. The status stays PENDING. */
    public void markSent(long id, String stripeRefundId) {
        jdbc.sql("update refunds set stripe_refund_id = ?, sent_at = now() where id = ?")
                .params(stripeRefundId, id).update();
    }

    /** PENDING → SUCCEEDED; false if it wasn't PENDING (already settled), so the caller posts the line only once. */
    public boolean markSucceeded(long id) {
        return jdbc.sql("""
                update refunds set status = 'SUCCEEDED', failure_reason = null, settled_at = now()
                where id = ? and status = 'PENDING'""")
                .param(id).update() == 1;
    }

    /** PENDING → FAILED with Stripe's reason; a refund that already succeeded is left alone. */
    public boolean markFailed(long id, String failureReason) {
        return jdbc.sql("""
                update refunds set status = 'FAILED', failure_reason = ?, settled_at = now()
                where id = ? and status = 'PENDING'""")
                .params(failureReason, id).update() == 1;
    }

    /** FAILED → PENDING as the next attempt, which gets its own idempotency key; returns the new attempt number. */
    public Optional<Integer> startNextAttempt(long id) {
        return jdbc.sql("""
                update refunds set status = 'PENDING', attempt = attempt + 1, failure_reason = null,
                    stripe_refund_id = null, sent_at = null, settled_at = null
                where id = ? and status = 'FAILED' returning attempt""")
                .param(id).query(Integer.class).optional();
    }

    /**
     * PENDING refunds whose current attempt Stripe never confirmed (no Stripe id yet), recorded more than
     * {@code olderThan} ago, oldest first: a send that never ran, or one whose answer never came back.
     */
    public List<Long> findUnsent(Duration olderThan) {
        return jdbc.sql("""
                select id from refunds
                where status = 'PENDING' and stripe_refund_id is null
                  and created_at <= now() - make_interval(secs => ?)
                order by id""")
                .param((double) olderThan.toSeconds())
                .query(Long.class).list();
    }

    /** Oldest first. */
    public List<RefundListing> findByPolicy(long policyId) {
        return jdbc.sql("""
                select r.id, r.display_id, r.status, r.amount_cents, p.card_last4, r.attempt, t.display_id,
                       r.stripe_refund_id, r.failure_reason
                from refunds r
                join payments p on p.id = r.payment_id
                join policy_transactions t on t.id = r.policy_transaction_id
                where r.policy_id = ? order by r.id""")
                .param(policyId)
                .query((rs, n) -> new RefundListing(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getString(5), rs.getInt(6), rs.getString(7), rs.getString(8), rs.getString(9)))
                .list();
    }

    /**
     * What refunds on the policy have promised but the ledger doesn't show yet: PENDING ones not yet settled, and
     * FAILED ones waiting for a retry. A SUCCEEDED refund is already a REFUND line.
     */
    public long unsettledTotal(long policyId) {
        return jdbc.sql("select coalesce(sum(amount_cents), 0) from refunds where policy_id = ? and status <> 'SUCCEEDED'")
                .param(policyId).query(Long.class).single();
    }

    /** Per payment on the policy, the total refunded or on its way (PENDING or SUCCEEDED). */
    public List<Refunded> refundedPerPayment(long policyId) {
        return jdbc.sql("""
                select payment_id, sum(amount_cents) from refunds
                where policy_id = ? and status <> 'FAILED' group by payment_id order by payment_id""")
                .param(policyId)
                .query((rs, n) -> new Refunded(rs.getLong(1), rs.getLong(2)))
                .list();
    }
}
