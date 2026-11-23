package app.plainly.notify;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * The real email, through Resend's REST API. Without a verified domain Resend's sandbox only delivers to the
 * account owner's own address, so every email goes to one test inbox ({@code RESEND_TEST_TO}) and names the
 * owner's address in its body (DECISIONS, emails spike). Without an API key or a test inbox, email is off and
 * the app runs as before.
 */
@Component
class ResendEmailGateway implements EmailGateway {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailGateway.class);
    private static final String BASE_URL = "https://api.resend.com";
    private static final int ERROR_LIMIT = 300;

    private final RestClient client;
    private final String from;
    private final String testInbox;
    private final String keyPrefix;

    @Autowired
    ResendEmailGateway(@Value("${plainly.email.api-key}") String apiKey, @Value("${plainly.email.from}") String from,
            @Value("${plainly.email.test-to}") String testInbox,
            @Value("${plainly.idempotency-prefix}") String keyPrefix) {
        this(apiKey, from, testInbox, RestClient.builder().requestFactory(timeouts()), keyPrefix);
    }

    ResendEmailGateway(String apiKey, String from, String testInbox) {
        this(apiKey, from, testInbox, RestClient.builder().requestFactory(timeouts()), "");
    }

    ResendEmailGateway(String apiKey, String from, String testInbox, RestClient.Builder builder) {
        this(apiKey, from, testInbox, builder, "");
    }

    ResendEmailGateway(String apiKey, String from, String testInbox, RestClient.Builder builder, String keyPrefix) {
        this.from = from;
        this.testInbox = testInbox;
        this.keyPrefix = keyPrefix;
        if (apiKey.isBlank() || testInbox.isBlank()) {
            log.warn("Resend isn't set up (RESEND_API_KEY and RESEND_TEST_TO are both needed): emails are off");
            this.client = null;
        } else {
            this.client = builder.baseUrl(BASE_URL).defaultHeader("Authorization", "Bearer " + apiKey).build();
        }
    }

    @Override
    public boolean enabled() {
        return client != null;
    }

    @Override
    public String send(EmailMessage message, String idempotencyKey) {
        if (client == null) {
            throw new EmailProblem("Emails are off: RESEND_API_KEY or RESEND_TEST_TO isn't set");
        }
        try {
            Map<?, ?> sent = client.post().uri("/emails").contentType(MediaType.APPLICATION_JSON)
                    // Prefixed per environment: local runs and Railway share one Resend account (see StripeJavaGateway.key).
                    .header("Idempotency-Key", keyPrefix.isBlank() ? idempotencyKey : keyPrefix + ":" + idempotencyKey)
                    .body(Map.of("from", from, "to", List.of(testInbox), "subject", message.subject(),
                            "html", message.html(), "text", message.text()))
                    .retrieve().body(Map.class);
            Object id = sent == null ? null : sent.get("id");
            if (id == null) {
                throw new EmailProblem("Resend answered without a message id");
            }
            return id.toString();
        } catch (RestClientResponseException e) {
            throw new EmailProblem("Resend refused the email (" + e.getStatusCode().value() + "): "
                    + brief(e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new EmailProblem("Couldn't reach Resend: " + e.getMessage(), e);
        }
    }

    /** Resend's errors are short JSON; anything longer (a proxy's error page) is cut before it's stored or logged. */
    static String brief(String body) {
        return body.length() <= ERROR_LIMIT ? body : body.substring(0, ERROR_LIMIT) + "…";
    }

    private static SimpleClientHttpRequestFactory timeouts() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
    }
}
