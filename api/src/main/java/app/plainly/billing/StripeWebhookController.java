package app.plainly.billing;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Receives Stripe webhooks. The signature is checked against the raw request body, so the body is taken
 * as a String and never parsed before verification.
 *
 * <p>Spike (collect-premium T1): this only verifies and logs the event. T8 replaces the body with the
 * idempotent handler that changes payment status and posts ledger lines.
 */
@RestController
class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final String webhookSecret;

    StripeWebhookController(@Value("${plainly.stripe.webhook-secret}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
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
        log.info("Stripe event {} ({}) verified", event.getId(), event.getType());
        return ResponseEntity.ok(Map.of("received", event.getId()));
    }

    private static ResponseEntity<Map<String, String>> badSignature() {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "bad_signature", "message", "This request isn't signed by Stripe."));
    }
}
