package app.plainly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The test helpers must build deliveries that Stripe's own library accepts and can read, or every webhook test lies. */
// covers T2
class StripeTestSupportTest {

    private static final String SECRET = StripeTestSupport.WEBHOOK_SECRET;

    @Test
    void aSignedChargeEventVerifiesAndItsChargeCanBeRead() throws Exception {
        String body = StripeTestSupport.chargeSucceeded("evt_t2_ok", "pi_t2", 1400, "4242", "pm_t2");
        Event event = Webhook.constructEvent(body, StripeTestSupport.signature(body, SECRET, Instant.now().getEpochSecond()), SECRET);

        assertThat(event.getId()).isEqualTo("evt_t2_ok");
        assertThat(event.getType()).isEqualTo("charge.succeeded");
        Charge charge = (Charge) event.getDataObjectDeserializer().getObject().orElseThrow();
        assertThat(charge.getPaymentIntent()).isEqualTo("pi_t2");
        assertThat(charge.getAmount()).isEqualTo(1400L);
        assertThat(charge.getPaymentMethod()).isEqualTo("pm_t2");
        assertThat(charge.getPaymentMethodDetails().getCard().getLast4()).isEqualTo("4242");
    }

    @Test
    void aFailedChargeCarriesTheBanksReason() throws Exception {
        String body = StripeTestSupport.chargeFailed("evt_t2_fail", "pi_t2f", 1400, "0002", "Your card was declined.");
        Event event = Webhook.constructEvent(body, StripeTestSupport.signature(body, SECRET, Instant.now().getEpochSecond()), SECRET);

        Charge charge = (Charge) event.getDataObjectDeserializer().getObject().orElseThrow();
        assertThat(charge.getStatus()).isEqualTo("failed");
        assertThat(charge.getFailureMessage()).isEqualTo("Your card was declined.");
    }

    @Test
    void aProcessingIntentEventCanBeRead() throws Exception {
        String body = StripeTestSupport.intentProcessing("evt_t2_proc", "pi_t2p", 1400);
        Event event = Webhook.constructEvent(body, StripeTestSupport.signature(body, SECRET, Instant.now().getEpochSecond()), SECRET);

        PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElseThrow();
        assertThat(intent.getId()).isEqualTo("pi_t2p");
        assertThat(intent.getStatus()).isEqualTo("processing");
    }

    @Test
    void aWrongSecretOrAnEditedBodyIsRejected() {
        String body = StripeTestSupport.chargeSucceeded("evt_t2_bad", "pi_t2b", 1400, "4242", "pm_t2b");
        String header = StripeTestSupport.signature(body, SECRET, Instant.now().getEpochSecond());

        assertThatThrownBy(() -> Webhook.constructEvent(body, header, "whsec_someone_else"))
                .isInstanceOf(SignatureVerificationException.class);
        assertThatThrownBy(() -> Webhook.constructEvent(body.replace("1400", "1"), header, SECRET))
                .isInstanceOf(SignatureVerificationException.class);
    }
}
