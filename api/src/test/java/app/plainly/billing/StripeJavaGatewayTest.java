package app.plainly.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Without keys the app still starts and says payments are off, instead of failing on the first click. */
// covers AC13, T7
class StripeJavaGatewayTest {

    @Test
    void withNoKeysPaymentsAreOffAndCallsExplainWhy() {
        StripeJavaGateway gateway = new StripeJavaGateway("", "");

        assertThat(gateway.enabled()).isFalse();
        assertThatThrownBy(() -> gateway.createPaymentIntent("cus_x", 100, "pay-1", Map.of()))
                .isInstanceOfSatisfying(PaymentProblem.class, p -> {
                    assertThat(p.status()).isEqualTo(503);
                    assertThat(p.error()).isEqualTo("payments_off");
                });
    }

    @Test
    void aSecretKeyWithoutAPublishableKeyIsAlsoOff() {
        assertThat(new StripeJavaGateway("sk_test_x", "").enabled()).isFalse();
    }

    @Test
    void withBothKeysPaymentsAreOnAndThePublishableKeyIsServed() {
        StripeJavaGateway gateway = new StripeJavaGateway("sk_test_x", "pk_test_y");

        assertThat(gateway.enabled()).isTrue();
        assertThat(gateway.publishableKey()).isEqualTo("pk_test_y");
    }
}
