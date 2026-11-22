package app.plainly.billing;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives Stripe webhooks. Stripe can't sign in, so this is the one {@code /api} path with no session; the
 * {@code Stripe-Signature} header is its proof instead. The signature is checked against the raw body, which is
 * why the body is taken as a String and never parsed first. Stripe's default 5-minute tolerance refuses an old
 * signature, so a captured delivery can't be replayed later.
 */
@RestController
class StripeWebhookController {

    private final String webhookSecret;
    private final StripeWebhookService webhooks;

    StripeWebhookController(@Value("${plainly.stripe.webhook-secret}") String webhookSecret, StripeWebhookService webhooks) {
        this.webhookSecret = webhookSecret;
        this.webhooks = webhooks;
    }

    @PostMapping(path = "/api/stripe/webhook", consumes = "*/*")
    ResponseEntity<Map<String, String>> receive(
            @RequestBody String payload,
            @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        if (webhookSecret.isBlank() || signature == null) {
            return badSignature();
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            return badSignature();
        }
        try {
            webhooks.handle(event);
        } catch (StripeWebhookService.UnreadableEvent e) {
            // Nothing was recorded, so Stripe's retry will be handled as new.
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "unreadable_event", "message", "We couldn't read this event yet."));
        }
        // 200 for every verified event, including repeats and ones we ignore, so Stripe stops retrying.
        return ResponseEntity.ok(Map.of("received", event.getId()));
    }

    private static ResponseEntity<Map<String, String>> badSignature() {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "bad_signature", "message", "This request isn't signed by Stripe."));
    }
}
