package app.plainly.billing;

import app.plainly.auth.SignedInUser;
import app.plainly.common.NotFoundException;
import app.plainly.ledger.LedgerQueryService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A policyholder paying what they owe. The amount is always the ledger's balance due, never one the browser
 * sends, and nothing here marks a payment paid or failed: only {@link WebhookHandler} does that.
 */
@Service
public class PaymentService {

    private final JdbcClient jdbc;
    private final StripeGateway stripe;
    private final LedgerQueryService ledger;

    public PaymentService(JdbcClient jdbc, StripeGateway stripe, LedgerQueryService ledger) {
        this.jdbc = jdbc;
        this.stripe = stripe;
        this.ledger = ledger;
    }

    public record SavedCard(String brand, String last4) {}

    public record Started(long paymentId, long amountCents, String clientSecret, String publishableKey,
            SavedCard savedCard) {}

    public record PaymentView(long paymentId, String status, long amountCents, String cardLast4, Instant createdAt,
            long amountDueCents) {}

    public record SavedCardConfirm(String status, String clientSecret) {}

    /** A payment as P1 shows it. */
    public record PaymentSummary(String status, long amountCents, String cardLast4, Instant createdAt) {}

    private record OpenPayment(long id, String intentId, long amountCents) {}

    private record OwnedPayment(long id, long policyId, String status, String intentId, long amountCents,
            String cardLast4, Instant createdAt) {}

    private record CardOnFile(String paymentMethodId, String brand, String last4) {}

    /**
     * Opens (or reopens) a payment for the balance due. The policy row is locked so two tabs can't open two
     * intents at once. Reopening the pay page reuses the open intent while the amount is unchanged.
     */
    @Transactional
    public Started start(SignedInUser me) {
        if (!stripe.enabled()) {
            throw StripeJavaGateway.paymentsOff();
        }
        long policyId = jdbc.sql("select id from policies where customer_id = ? order by id desc limit 1 for update")
                .param(me.customerId()).query(Long.class).optional()
                .orElseThrow(() -> new NotFoundException("You don't have a policy yet."));
        boolean inFlight = jdbc.sql("select exists (select 1 from payments where policy_id = ? and status = 'PROCESSING')")
                .param(policyId).query(Boolean.class).single();
        if (inFlight) {
            throw new PaymentProblem(409, "payment_in_flight", "Your last payment is still on its way.");
        }
        long due = Math.max(0, ledger.balance(policyId));
        if (due == 0) {
            throw new PaymentProblem(409, "nothing_due", "You're all paid up. There's nothing to pay right now.");
        }
        SavedCard saved = cardOnFile(me.customerId()).map(c -> new SavedCard(c.brand(), c.last4())).orElse(null);

        Optional<OpenPayment> open = jdbc.sql("""
                select id, stripe_payment_intent_id, amount_cents from payments
                where policy_id = ? and status = 'OPEN' order by id desc limit 1""")
                .param(policyId)
                .query((rs, n) -> new OpenPayment(rs.getLong(1), rs.getString(2), rs.getLong(3)))
                .optional();
        if (open.isPresent() && open.get().amountCents() == due) {
            OpenPayment p = open.get();
            return new Started(p.id(), due, stripe.clientSecret(p.intentId()), stripe.publishableKey(), saved);
        }

        String customer = stripeCustomer(me.customerId());
        long paymentId = jdbc.sql("select nextval(pg_get_serial_sequence('payments', 'id'))").query(Long.class).single();
        StripeGateway.CreatedIntent intent = stripe.createPaymentIntent(customer, due, "pay-" + paymentId,
                Map.of("payment_id", String.valueOf(paymentId), "policy_id", String.valueOf(policyId)));
        jdbc.sql("""
                insert into payments (id, policy_id, created_by, stripe_payment_intent_id, amount_cents)
                values (?, ?, ?, ?, ?)""")
                .params(paymentId, policyId, me.id(), intent.id(), due).update();
        return new Started(paymentId, due, intent.clientSecret(), stripe.publishableKey(), saved);
    }

    /** Pays an open payment with the card that paid last time. Stripe's answer is passed on, not acted on. */
    @Transactional
    public SavedCardConfirm payWithSavedCard(SignedInUser me, long paymentId) {
        OwnedPayment payment = owned(me, paymentId);
        if (!payment.status().equals("OPEN")) {
            throw new PaymentProblem(409, "payment_not_open", "This payment has already been sent to your bank.");
        }
        CardOnFile card = cardOnFile(me.customerId()).orElseThrow(() -> new PaymentProblem(409, "no_saved_card",
                "There's no card saved from a past payment. Enter your card details instead."));
        StripeGateway.ConfirmedIntent confirmed = stripe.confirmWithSavedCard(payment.intentId(), card.paymentMethodId());
        return new SavedCardConfirm(confirmed.status(), confirmed.clientSecret());
    }

    public PaymentView view(SignedInUser me, long paymentId) {
        OwnedPayment p = owned(me, paymentId);
        long due = Math.max(0, ledger.balance(p.policyId()));
        return new PaymentView(p.id(), p.status(), p.amountCents(), p.cardLast4(), p.createdAt(), due);
    }

    /**
     * The latest payment the owner actually sent to their bank. An OPEN one only means the pay page was opened,
     * so it isn't shown.
     */
    public Optional<PaymentSummary> latestSubmitted(long policyId) {
        return jdbc.sql("""
                select status, amount_cents, card_last4, created_at from payments
                where policy_id = ? and status <> 'OPEN' order by id desc limit 1""")
                .param(policyId)
                .query((rs, n) -> new PaymentSummary(rs.getString(1), rs.getLong(2), rs.getString(3),
                        rs.getObject(4, OffsetDateTime.class).toInstant()))
                .optional();
    }

    /** A payment on the signed-in owner's own policy; anyone else's is "not found", never "forbidden". */
    private OwnedPayment owned(SignedInUser me, long paymentId) {
        return jdbc.sql("""
                select p.id, p.policy_id, p.status, p.stripe_payment_intent_id, p.amount_cents, p.card_last4, p.created_at
                from payments p join policies pol on pol.id = p.policy_id
                where p.id = ? and pol.customer_id = ?""")
                .params(paymentId, me.customerId())
                .query((rs, n) -> new OwnedPayment(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getLong(5), rs.getString(6), rs.getObject(7, OffsetDateTime.class).toInstant()))
                .optional()
                .orElseThrow(() -> new NotFoundException("We can't find that payment."));
    }

    /** The card from the customer's latest successful payment, which Stripe kept on their Stripe customer. */
    private Optional<CardOnFile> cardOnFile(long customerId) {
        return jdbc.sql("""
                select p.payment_method_id, p.card_brand, p.card_last4
                from payments p join policies pol on pol.id = p.policy_id
                where pol.customer_id = ? and p.status = 'SUCCEEDED' and p.payment_method_id is not null
                order by p.settled_at desc, p.id desc limit 1""")
                .param(customerId)
                .query((rs, n) -> new CardOnFile(rs.getString(1), rs.getString(2), rs.getString(3)))
                .optional();
    }

    /** Our customer's Stripe customer, created on their first payment. */
    private String stripeCustomer(long customerId) {
        record Row(String stripeId, String name, String email) {}
        Row row = jdbc.sql("select stripe_customer_id, legal_name, owner_email from customers where id = ? for update")
                .param(customerId)
                .query((rs, n) -> new Row(rs.getString(1), rs.getString(2), rs.getString(3)))
                .single();
        if (row.stripeId() != null) {
            return row.stripeId();
        }
        String created = stripe.createCustomer(customerId, row.name(), row.email());
        jdbc.sql("update customers set stripe_customer_id = ? where id = ?").params(created, customerId).update();
        return created;
    }
}
