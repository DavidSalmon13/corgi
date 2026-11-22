package app.plainly.billing;

import app.plainly.common.Today;
import app.plainly.ledger.LedgerPostingService;
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
import org.springframework.stereotype.Service;
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
 *   <li>{@code payment_intent.payment_failed} / {@code payment_intent.canceled}: a payment stuck at PROCESSING
 *       becomes FAILED, so the owner can pay again. No money moved, so nothing is posted.
 * </ul>
 *
 * <p>An event we can't read is not recorded: the transaction rolls back and Stripe sends it again.
 */
@Service
class StripeWebhookService {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);

    private final ProcessedEventRepository events;
    private final PaymentRepository payments;
    private final LedgerPostingService ledger;
    private final Today today;

    StripeWebhookService(ProcessedEventRepository events, PaymentRepository payments, LedgerPostingService ledger, Today today) {
        this.events = events;
        this.payments = payments;
        this.ledger = ledger;
        this.today = today;
    }

    @Transactional
    public void handle(Event event) {
        // Recording the event id first, in the same transaction as its effects, is what makes a replay (or a
        // second copy arriving at the same moment, which waits on this row) a no-op.
        if (!events.recordIfNew(event.getId(), event.getType())) {
            log.info("Stripe event {} ({}) already handled; ignoring the repeat", event.getId(), event.getType());
            return;
        }
        switch (event.getType()) {
            case "payment_intent.processing" -> object(event, PaymentIntent.class).ifPresent(this::processing);
            case "charge.succeeded" -> object(event, Charge.class).ifPresent(c -> succeeded(event.getId(), c));
            case "charge.failed" -> object(event, Charge.class).ifPresent(c -> failed(event.getId(), c));
            case "payment_intent.payment_failed", "payment_intent.canceled" ->
                    object(event, PaymentIntent.class).ifPresent(this::endedWithoutPaying);
            default -> log.info("Stripe event {} ({}) recorded; nothing to do", event.getId(), event.getType());
        }
    }

    private void processing(PaymentIntent intent) {
        payments.markProcessing(intent.getId());
    }

    private void endedWithoutPaying(PaymentIntent intent) {
        payments.markFailedIfProcessing(intent.getId());
    }

    private void succeeded(String eventId, Charge charge) {
        Optional<PaymentRepository.Locked> found = lockPayment(charge.getPaymentIntent());
        if (found.isEmpty()) {
            return;
        }
        PaymentRepository.Locked payment = found.get();
        if (payment.status().equals("SUCCEEDED")) {
            log.warn("Payment {} already succeeded; not crediting charge {} twice", payment.id(), charge.getId());
            return;
        }
        Card card = Card.of(charge);
        payments.markSucceeded(payment.id(), charge.getPaymentMethod(), card.brand(), card.last4());
        // The credit is what the bank actually took, which is the intent's amount for a card payment.
        long received = charge.getAmountCaptured() != null && charge.getAmountCaptured() > 0
                ? charge.getAmountCaptured() : charge.getAmount();
        ledger.post(payment.policyId(), null, List.of(NewEntry.payment(today.date(),
                "Payment, " + card.describe(), -received, payment.id(), eventId)));
    }

    private void failed(String eventId, Charge charge) {
        Optional<PaymentRepository.Locked> found = lockPayment(charge.getPaymentIntent());
        if (found.isEmpty()) {
            return;
        }
        PaymentRepository.Locked payment = found.get();
        if (payment.status().equals("SUCCEEDED")) {
            log.warn("Ignoring charge.failed for payment {}: it already succeeded", payment.id());
            return;
        }
        Card card = Card.of(charge);
        payments.markFailed(payment.id(), card.brand(), card.last4(), charge.getFailureMessage());
        ledger.post(payment.policyId(), null, List.of(NewEntry.payment(today.date(),
                "Payment attempt, " + card.describe(), 0, payment.id(), eventId)));
    }

    private Optional<PaymentRepository.Locked> lockPayment(String intentId) {
        Optional<PaymentRepository.Locked> row = intentId == null ? Optional.empty() : payments.lockByIntent(intentId);
        if (row.isEmpty()) {
            log.info("Stripe intent {} isn't one of our payments; ignoring", intentId);
        }
        return row;
    }

    /**
     * The event's object, read even if its API version differs from the library's (both are pinned today). If it
     * can't be read at all, this throws, so the event isn't recorded as handled and Stripe's retry gets another go.
     */
    private static <T extends StripeObject> Optional<T> object(Event event, Class<T> type) {
        var data = event.getDataObjectDeserializer();
        StripeObject object;
        try {
            object = data.getObject().isPresent() ? data.getObject().get() : data.deserializeUnsafe();
        } catch (EventDataObjectDeserializationException | RuntimeException e) {
            log.error("Couldn't read Stripe event {} ({}); answering 500 so Stripe sends it again",
                    event.getId(), event.getType());
            throw new UnreadableEvent(event.getId(), e);
        }
        return type.isInstance(object) ? Optional.of(type.cast(object)) : Optional.empty();
    }

    /** A verified event whose object we couldn't read. Rolls back the handling, including the event id. */
    static class UnreadableEvent extends RuntimeException {
        UnreadableEvent(String eventId, Throwable cause) {
            super("Couldn't read Stripe event " + eventId, cause);
        }
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
