package app.plainly;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Builds Stripe webhook deliveries the way Stripe does: an event JSON body plus a {@code Stripe-Signature}
 * header of {@code t=<unix seconds>,v1=<HMAC-SHA256 of "t.body" with the signing secret>}. The API checks the
 * signature for real, so tests exercise the same verification as production.
 */
public final class StripeTestSupport {

    /** Test-only values, set on every {@link IntegrationTest} context. */
    public static final String WEBHOOK_SECRET = "whsec_plainly_test_only";
    public static final String PUBLISHABLE_KEY = "pk_test_plainly_test_only";
    /** The API version the account and stripe-java agree on (DECISIONS, Stripe webhook spike). */
    public static final String API_VERSION = "2026-11-18.endive";

    private StripeTestSupport() {}

    /** Registers the fake gateway in place of the real one. */
    @TestConfiguration(proxyBeanMethods = false)
    public static class Config {
        @Bean
        @Primary
        FakeStripeGateway fakeStripeGateway() {
            return new FakeStripeGateway();
        }
    }

    public static String newEventId() {
        return "evt_test_" + UUID.randomUUID().toString().replace("-", "");
    }

    /** A POST to the webhook carrying {@code body}, signed now with the test secret. */
    public static MockHttpServletRequestBuilder delivery(String body) {
        return delivery(body, signature(body, WEBHOOK_SECRET, Instant.now().getEpochSecond()));
    }

    public static MockHttpServletRequestBuilder delivery(String body, String signatureHeader) {
        MockHttpServletRequestBuilder request = post("/api/stripe/webhook")
                .contentType(MediaType.APPLICATION_JSON).content(body);
        return signatureHeader == null ? request : request.header("Stripe-Signature", signatureHeader);
    }

    public static String signature(String body, String secret, long unixSeconds) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((unixSeconds + "." + body).getBytes(StandardCharsets.UTF_8));
            return "t=" + unixSeconds + ",v1=" + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@code charge.succeeded}: the bank took the money. */
    public static String chargeSucceeded(String eventId, String intentId, long amountCents, String last4,
            String paymentMethodId) {
        return event(eventId, "charge.succeeded", charge(intentId, amountCents, "succeeded", last4, paymentMethodId, null));
    }

    /** {@code charge.failed}: the bank declined, so no money moved. */
    public static String chargeFailed(String eventId, String intentId, long amountCents, String last4,
            String failureMessage) {
        return event(eventId, "charge.failed",
                charge(intentId, amountCents, "failed", last4, "pm_fake_" + last4, failureMessage));
    }

    /** {@code payment_intent.processing}: submitted, the bank hasn't answered yet. */
    public static String intentProcessing(String eventId, String intentId, long amountCents) {
        return event(eventId, "payment_intent.processing", """
                {"id":"%s","object":"payment_intent","amount":%d,"currency":"usd","status":"processing","livemode":false}"""
                .formatted(intentId, amountCents));
    }

    /** {@code payment_intent.payment_failed}: the bank said no, possibly with no {@code charge.failed} to follow. */
    public static String intentPaymentFailed(String eventId, String intentId, long amountCents) {
        return event(eventId, "payment_intent.payment_failed", """
                {"id":"%s","object":"payment_intent","amount":%d,"currency":"usd","status":"requires_payment_method","livemode":false}"""
                .formatted(intentId, amountCents));
    }

    /** {@code payment_intent.canceled}: the intent can no longer be paid. */
    public static String intentCanceled(String eventId, String intentId, long amountCents) {
        return event(eventId, "payment_intent.canceled", """
                {"id":"%s","object":"payment_intent","amount":%d,"currency":"usd","status":"canceled","livemode":false}"""
                .formatted(intentId, amountCents));
    }

    /**
     * A signed {@code charge.succeeded} we can't read: another API version, so stripe-java won't map it for us,
     * and an amount that isn't a number, so reading it anyway fails too.
     */
    public static String unreadableChargeSucceeded(String eventId, String intentId) {
        return """
                {"id":"%s","object":"event","api_version":"2020-08-27","created":%d,"livemode":false,"pending_webhooks":1,
                 "type":"charge.succeeded","data":{"object":{"id":"ch_bad","object":"charge","amount":"lots",
                 "payment_intent":"%s"}}}"""
                .formatted(eventId, Instant.now().getEpochSecond(), intentId);
    }

    /**
     * A {@code refund.*} event about one of our refunds. Stripe sends {@code refund.created} and
     * {@code refund.updated} with the refund's current status (pending, succeeded, failed), and {@code refund.failed}
     * when the money couldn't be sent. {@code refundId} is our row id, echoed back in the metadata we sent.
     */
    public static String refundEvent(String eventId, String type, String stripeRefundId, long refundId,
            String intentId, long amountCents, String status, String failureReason) {
        String failure = failureReason == null ? "null" : "\"" + failureReason + "\"";
        return event(eventId, type, """
                {"id":"%s","object":"refund","amount":%d,"currency":"usd","status":"%s","payment_intent":"%s",
                 "failure_reason":%s,"metadata":{"refund_id":"%d"}}"""
                .formatted(stripeRefundId, amountCents, status, intentId, failure, refundId));
    }

    /** {@code refund.updated} with status succeeded: the money is on its way back to the card. */
    public static String refundSucceeded(String eventId, String stripeRefundId, long refundId, String intentId,
            long amountCents) {
        return refundEvent(eventId, "refund.updated", stripeRefundId, refundId, intentId, amountCents, "succeeded", null);
    }

    /** {@code refund.failed}: Stripe couldn't send the money back. */
    public static String refundFailed(String eventId, String stripeRefundId, long refundId, String intentId,
            long amountCents, String failureReason) {
        return refundEvent(eventId, "refund.failed", stripeRefundId, refundId, intentId, amountCents, "failed",
                failureReason);
    }

    /** Any event type Plainly doesn't act on, e.g. {@code customer.created}. */
    public static String otherEvent(String eventId, String type) {
        return event(eventId, type, """
                {"id":"cus_other","object":"customer","livemode":false}""");
    }

    private static String charge(String intentId, long amountCents, String status, String last4,
            String paymentMethodId, String failureMessage) {
        String failure = failureMessage == null ? "null" : "\"" + failureMessage + "\"";
        return """
                {"id":"ch_%s","object":"charge","amount":%d,"amount_captured":%d,"currency":"usd","livemode":false,
                 "paid":%s,"status":"%s","payment_intent":"%s","payment_method":"%s","failure_message":%s,
                 "payment_method_details":{"type":"card","card":{"brand":"visa","last4":"%s"}}}"""
                .formatted(UUID.randomUUID().toString().replace("-", ""), amountCents,
                        "succeeded".equals(status) ? amountCents : 0, "succeeded".equals(status), status, intentId,
                        paymentMethodId, failure, last4);
    }

    private static String event(String eventId, String type, String object) {
        return """
                {"id":"%s","object":"event","api_version":"%s","created":%d,"livemode":false,"pending_webhooks":1,
                 "type":"%s","data":{"object":%s}}"""
                .formatted(eventId, API_VERSION, Instant.now().getEpochSecond(), type, object);
    }
}
