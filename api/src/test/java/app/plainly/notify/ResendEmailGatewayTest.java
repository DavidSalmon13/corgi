package app.plainly.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// covers AC1, AC5, AC6, T4
/**
 * Resend's sandbox, behind {@link EmailGateway}: off without a key or a test inbox, every email delivered to the
 * test inbox, the idempotency key passed on, and a failure reported as an {@link EmailProblem}.
 */
class ResendEmailGatewayTest {

    private static final EmailMessage MESSAGE = new EmailMessage("maya@example.test", "We got your payment of $642.00",
            "<p>Thanks</p>", "Thanks");

    @Test
    void withoutAKeyEmailIsOff() {
        assertThat(new ResendEmailGateway("", "Plainly <onboarding@resend.dev>", "inbox@example.test").enabled())
                .isFalse();
    }

    @Test
    void withoutATestInboxEmailIsOffBecauseTheSandboxCouldNotDeliverIt() {
        assertThat(new ResendEmailGateway("re_test_key", "Plainly <onboarding@resend.dev>", "").enabled()).isFalse();
    }

    @Test
    void withAKeyAndATestInboxEmailIsOn() {
        assertThat(new ResendEmailGateway("re_test_key", "Plainly <onboarding@resend.dev>", "inbox@example.test")
                .enabled()).isTrue();
    }

    @Test
    void sendsToTheTestInboxWithTheIdempotencyKeyAndReturnsResendsId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer resend = MockRestServiceServer.bindTo(builder).build();
        resend.expect(requestTo("https://api.resend.com/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer re_test_key"))
                .andExpect(header("Idempotency-Key", "PAYMENT_RECEIPT/42"))
                .andExpect(content().json("""
                        {"from":"Plainly <onboarding@resend.dev>","to":["inbox@example.test"],
                         "subject":"We got your payment of $642.00","html":"<p>Thanks</p>","text":"Thanks"}"""))
                .andRespond(withSuccess("{\"id\":\"re_msg_1\"}", MediaType.APPLICATION_JSON));
        var gateway = new ResendEmailGateway("re_test_key", "Plainly <onboarding@resend.dev>", "inbox@example.test",
                builder);

        assertThat(gateway.send(MESSAGE, "PAYMENT_RECEIPT/42")).isEqualTo("re_msg_1");
        resend.verify();
    }

    @Test
    void aResendErrorIsAnEmailProblem() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer resend = MockRestServiceServer.bindTo(builder).build();
        resend.expect(requestTo("https://api.resend.com/emails")).andRespond(withServerError());
        var gateway = new ResendEmailGateway("re_test_key", "Plainly <onboarding@resend.dev>", "inbox@example.test",
                builder);

        assertThatThrownBy(() -> gateway.send(MESSAGE, "PAYMENT_RECEIPT/43")).isInstanceOf(EmailProblem.class)
                .hasMessageContaining("500");
    }

    // covers AC6 (security review): a long error page from Resend or a proxy isn't stored or logged whole
    @Test
    void aLongResendErrorIsCutShort() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer resend = MockRestServiceServer.bindTo(builder).build();
        resend.expect(requestTo("https://api.resend.com/emails"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("x".repeat(5_000)));
        var gateway = new ResendEmailGateway("re_test_key", "Plainly <onboarding@resend.dev>", "inbox@example.test",
                builder);

        assertThatThrownBy(() -> gateway.send(MESSAGE, "PAYMENT_RECEIPT/44")).isInstanceOf(EmailProblem.class)
                .hasMessageContaining("502").satisfies(e -> assertThat(e.getMessage()).hasSizeLessThan(400));
    }

    @Test
    void sendingWhileOffIsAnEmailProblem() {
        var gateway = new ResendEmailGateway("", "Plainly <onboarding@resend.dev>", "");

        assertThatThrownBy(() -> gateway.send(MESSAGE, "k")).isInstanceOf(EmailProblem.class);
    }
}
