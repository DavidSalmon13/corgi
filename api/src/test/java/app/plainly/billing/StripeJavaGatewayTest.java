package app.plainly.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.RateLimitException;
import com.stripe.exception.InvalidRequestException;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Payments are on only when every Stripe value is set and they are test-mode keys; otherwise the app still
 * starts and says payments are off, instead of taking money it can't record.
 */
// covers AC13, T7; hotfix payment-review-fixes; correct-effective-date T17
class StripeJavaGatewayTest {

    @Test
    void withNoKeysPaymentsAreOffAndCallsExplainWhy() {
        StripeJavaGateway gateway = new StripeJavaGateway("", "", "");

        assertThat(gateway.enabled()).isFalse();
        assertThatThrownBy(() -> gateway.createPaymentIntent("cus_x", 100, "pay-1", Map.of()))
                .isInstanceOfSatisfying(PaymentProblem.class, p -> {
                    assertThat(p.status()).isEqualTo(503);
                    assertThat(p.error()).isEqualTo("payments_off");
                });
    }

    @Test
    void aSecretKeyWithoutAPublishableKeyIsAlsoOff() {
        assertThat(new StripeJavaGateway("sk_test_x", "", "whsec_z").enabled()).isFalse();
    }

    @Test
    void withAllThreeTestValuesPaymentsAreOnAndThePublishableKeyIsServed() {
        StripeJavaGateway gateway = new StripeJavaGateway("sk_test_x", "pk_test_y", "whsec_z");

        assertThat(gateway.enabled()).isTrue();
        assertThat(gateway.publishableKey()).isEqualTo("pk_test_y");
    }

    // --- hotfix: never take money we can't record, and never real money ---

    @Test
    void withoutTheWebhookSecretPaymentsAreOffBecauseNoPaymentCouldBeRecorded() {
        assertThat(new StripeJavaGateway("sk_test_x", "pk_test_y", "").enabled()).isFalse();
    }

    @Test
    void liveKeysAreRefused() {
        assertThat(new StripeJavaGateway("sk_live_x", "pk_live_y", "whsec_z").enabled()).isFalse();
        assertThat(new StripeJavaGateway("sk_test_x", "pk_live_y", "whsec_z").enabled()).isFalse();
        assertThat(new StripeJavaGateway("sk_live_x", "pk_test_y", "whsec_z").enabled()).isFalse();
    }

    // --- hotfix: a saved-card confirm that fails never claims nothing was charged ---

    @Test
    void confirmingAnIntentThatWasAlreadyPaidSaysTheLastPaymentIsOnItsWay() {
        PaymentProblem p = StripeJavaGateway.confirmProblem(new InvalidRequestException(
                "This PaymentIntent's status is succeeded", null, "req_1", "payment_intent_unexpected_state", 400, null));

        assertThat(p.status()).isEqualTo(409);
        assertThat(p.error()).isEqualTo("payment_in_flight");
        assertThat(p.getMessage()).isEqualTo("Your last payment is still on its way.");
    }

    @Test
    void aConfirmWeNeverHeardBackFromDoesntClaimNothingWasCharged() {
        PaymentProblem p = StripeJavaGateway.confirmProblem(new ApiConnectionException("read timed out"));

        assertThat(p.status()).isEqualTo(502);
        assertThat(p.error()).isEqualTo("confirm_unknown");
        assertThat(p.getMessage()).doesNotContainIgnoringCase("nothing was charged");
    }

    // --- review fix: only a definite refusal fails a refund (T17) ---

    @Test
    void aRefundStripeRejectsIsRefused() {
        assertThat(StripeJavaGateway.refundProblem(new InvalidRequestException(
                "Charge has already been refunded.", null, "req_2", "charge_already_refunded", 400, null)))
                .isInstanceOf(StripeGateway.RefundRefused.class);
    }

    @Test
    void aRefundWeNeverHeardBackAboutIsUnknownNotRefused() {
        assertThat(StripeJavaGateway.refundProblem(new ApiConnectionException("read timed out")))
                .isInstanceOf(StripeGateway.RefundOutcomeUnknown.class);
        assertThat(StripeJavaGateway.refundProblem(new ApiException("server error", "req_3", null, 500, null)))
                .isInstanceOf(StripeGateway.RefundOutcomeUnknown.class);
        assertThat(StripeJavaGateway.refundProblem(new RateLimitException("slow down", null, "req_4", null, 429, null)))
                .isInstanceOf(StripeGateway.RefundOutcomeUnknown.class);
    }
}
