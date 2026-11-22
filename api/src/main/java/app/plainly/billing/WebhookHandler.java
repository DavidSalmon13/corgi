package app.plainly.billing;

import app.plainly.common.Today;
import app.plainly.ledger.LedgerPoster;
import app.plainly.ledger.NewEntry;
import com.stripe.exception.EventDataObjectDeserializationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Acts on a verified Stripe event, at most once per event id. This is the only code that changes a payment's
 * status (DECISIONS, 2026-11-22): the browser's own confirm result is never trusted.
 *
 * <ul>
 *   <li>{@code payment_intent.processing}: the bank is deciding. OPEN becomes PROCESSING; nothing is posted.
 *   <li>{@code charge.succeeded}: money moved. The payment becomes SUCCEEDED and a PAYMENT credit is posted.
 *   <li>{@code charge.failed}: the bank declined. The payment becomes FAILED and a $0.00 line keeps the attempt
 *       visible. A failure never undoes a payment that already succeeded.
 * </ul>
 */
@Component
class WebhookHandler {

    private static final Logger log = LoggerFactory.getLogger(WebhookHandler.class);

    private final JdbcClient jdbc;
    private final LedgerPoster ledger;
    private final Today today;

    WebhookHandler(JdbcClient jdbc, LedgerPoster ledger, Today today) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.today = today;
    }

    private record PaymentRow(long id, long policyId, String status) {}

    @Transactional
    public void handle(Event event) {
        // Recording the event id first, in the same transaction as its effects, is what makes a replay (or a
        // second copy arriving at the same moment, which waits on this row) a no-op.
        int fresh = jdbc.sql("insert into processed_stripe_events (event_id, type) values (?, ?) on conflict do nothing")
                .params(event.getId(), event.getType()).update();
        if (fresh == 0) {
            log.info("Stripe event {} ({}) already handled; ignoring the repeat", event.getId(), event.getType());
            return;
        }
        switch (event.getType()) {
            case "payment_intent.processing" -> object(event, PaymentIntent.class).ifPresent(this::processing);
            case "charge.succeeded" -> object(event, Charge.class).ifPresent(c -> succeeded(event.getId(), c));
            case "charge.failed" -> object(event, Charge.class).ifPresent(c -> failed(event.getId(), c));
            default -> log.info("Stripe event {} ({}) recorded; nothing to do", event.getId(), event.getType());
        }
    }

    private void processing(PaymentIntent intent) {
        jdbc.sql("update payments set status = 'PROCESSING' where stripe_payment_intent_id = ? and status = 'OPEN'")
                .param(intent.getId()).update();
    }

    private void succeeded(String eventId, Charge charge) {
        Optional<PaymentRow> found = lockPayment(charge.getPaymentIntent());
        if (found.isEmpty()) {
            return;
        }
        PaymentRow payment = found.get();
        if (payment.status().equals("SUCCEEDED")) {
            log.warn("Payment {} already succeeded; not crediting charge {} twice", payment.id(), charge.getId());
            return;
        }
        Card card = Card.of(charge);
        jdbc.sql("""
                update payments set status = 'SUCCEEDED', payment_method_id = ?, card_brand = ?, card_last4 = ?,
                    failure_reason = null, settled_at = now()
                where id = ?""")
                .params(charge.getPaymentMethod(), card.brand(), card.last4(), payment.id()).update();
        // The credit is what the bank actually took, which is the intent's amount for a card payment.
        long received = charge.getAmountCaptured() != null && charge.getAmountCaptured() > 0
                ? charge.getAmountCaptured() : charge.getAmount();
        ledger.post(payment.policyId(), null, List.of(NewEntry.payment(today.date(),
                "Payment, " + card.describe(), -received, payment.id(), eventId)));
    }

    private void failed(String eventId, Charge charge) {
        Optional<PaymentRow> found = lockPayment(charge.getPaymentIntent());
        if (found.isEmpty()) {
            return;
        }
        PaymentRow payment = found.get();
        if (payment.status().equals("SUCCEEDED")) {
            log.warn("Ignoring charge.failed for payment {}: it already succeeded", payment.id());
            return;
        }
        Card card = Card.of(charge);
        jdbc.sql("""
                update payments set status = 'FAILED', card_brand = ?, card_last4 = ?, failure_reason = ?, settled_at = now()
                where id = ?""")
                .params(card.brand(), card.last4(), charge.getFailureMessage(), payment.id()).update();
        ledger.post(payment.policyId(), null, List.of(NewEntry.payment(today.date(),
                "Payment attempt, " + card.describe(), 0, payment.id(), eventId)));
    }

    private Optional<PaymentRow> lockPayment(String intentId) {
        Optional<PaymentRow> row = intentId == null ? Optional.empty() : jdbc.sql("""
                select id, policy_id, status from payments where stripe_payment_intent_id = ? for update""")
                .param(intentId)
                .query((rs, n) -> new PaymentRow(rs.getLong("id"), rs.getLong("policy_id"), rs.getString("status")))
                .optional();
        if (row.isEmpty()) {
            log.info("Stripe intent {} isn't one of our payments; ignoring", intentId);
        }
        return row;
    }

    /** The event's object, read even if its API version differs from the library's (both are pinned today). */
    private static <T extends StripeObject> Optional<T> object(Event event, Class<T> type) {
        var data = event.getDataObjectDeserializer();
        StripeObject object = data.getObject().orElseGet(() -> {
            try {
                return data.deserializeUnsafe();
            } catch (EventDataObjectDeserializationException e) {
                log.warn("Couldn't read Stripe event {} ({})", event.getId(), event.getType());
                return null;
            }
        });
        return type.isInstance(object) ? Optional.of(type.cast(object)) : Optional.empty();
    }

    private record Card(String brand, String last4) {
        static Card of(Charge charge) {
            var details = charge.getPaymentMethodDetails();
            var card = details == null ? null : details.getCard();
            return card == null ? new Card(null, null) : new Card(card.getBrand(), card.getLast4());
        }

        String describe() {
            return last4 == null ? "card" : "card ending " + last4;
        }
    }
}
