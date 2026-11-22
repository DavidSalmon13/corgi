package app.plainly.billing;

import static app.plainly.PaymentTestData.BASE_DUE_CENTS;
import static app.plainly.PaymentTestData.balance;
import static app.plainly.PaymentTestData.paymentStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.IntegrationTest;
import app.plainly.PaymentTestData;
import app.plainly.PaymentTestData.Owner;
import app.plainly.PaymentTestData.Started;
import app.plainly.StripeTestSupport;
import app.plainly.policy.IssuePolicyService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** Stripe tells us what happened to a payment; only a correctly signed, never-seen event may change anything. */
// covers AC5, AC6, AC8, AC9, AC10, T3
@IntegrationTest
@AutoConfigureMockMvc
class StripeWebhookTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issue;

    private Owner owner(String business) throws Exception {
        return PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issue, business);
    }

    private Started started(Owner owner) throws Exception {
        return PaymentTestData.startPayment(mvc, jdbc, owner);
    }

    private void deliver(String body) throws Exception {
        mvc.perform(StripeTestSupport.delivery(body)).andExpect(status().isOk());
    }

    private List<Map<String, Object>> paymentLines(long policyId) {
        return jdbc.sql("""
                select amount_cents, description, payment_id, stripe_event_id, policy_transaction_id, entry_date
                from ledger_entries where policy_id = ? and entry_type = 'PAYMENT' order by id""")
                .param(policyId).query().listOfRows();
    }

    private boolean recorded(String eventId) {
        return jdbc.sql("select count(*) from processed_stripe_events where event_id = ?").param(eventId)
                .query(Long.class).single() == 1;
    }

    // --- AC8: only Stripe's signature gets in ---

    @Test
    void aDeliveryWithNoSignatureIsRefusedAndChangesNothing() throws Exception {
        Owner owner = owner("No Signature Bakery LLC");
        Started p = started(owner);
        String eventId = StripeTestSupport.newEventId();
        String body = StripeTestSupport.chargeSucceeded(eventId, p.intentId(), p.amountCents(), "4242", "pm_nosig");

        mvc.perform(StripeTestSupport.delivery(body, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_signature"));

        assertThat(recorded(eventId)).isFalse();
        assertThat(paymentStatus(jdbc, p.paymentId())).isEqualTo("OPEN");
        assertThat(paymentLines(owner.policyId())).isEmpty();
    }

    @Test
    void aDeliverySignedWithAnotherSecretIsRefused() throws Exception {
        Owner owner = owner("Wrong Secret Bakery LLC");
        Started p = started(owner);
        String body = StripeTestSupport.chargeSucceeded(StripeTestSupport.newEventId(), p.intentId(), p.amountCents(), "4242", "pm_x");
        String forged = StripeTestSupport.signature(body, "whsec_someone_else", Instant.now().getEpochSecond());

        mvc.perform(StripeTestSupport.delivery(body, forged)).andExpect(status().isBadRequest());

        assertThat(paymentLines(owner.policyId())).isEmpty();
    }

    @Test
    void anEditedBodyIsRefused() throws Exception {
        Owner owner = owner("Edited Body Bakery LLC");
        Started p = started(owner);
        String body = StripeTestSupport.chargeSucceeded(StripeTestSupport.newEventId(), p.intentId(), p.amountCents(), "4242", "pm_x");
        String header = StripeTestSupport.signature(body, StripeTestSupport.WEBHOOK_SECRET, Instant.now().getEpochSecond());

        mvc.perform(StripeTestSupport.delivery(body.replace("\"amount\":" + p.amountCents(), "\"amount\":1"), header))
                .andExpect(status().isBadRequest());

        assertThat(paymentLines(owner.policyId())).isEmpty();
    }

    @Test
    void anOldSignatureIsRefusedSoACapturedDeliveryCantBeReplayedLater() throws Exception {
        Owner owner = owner("Old Signature Bakery LLC");
        Started p = started(owner);
        String body = StripeTestSupport.chargeSucceeded(StripeTestSupport.newEventId(), p.intentId(), p.amountCents(), "4242", "pm_x");
        String stale = StripeTestSupport.signature(body, StripeTestSupport.WEBHOOK_SECRET, Instant.now().minusSeconds(3600).getEpochSecond());

        mvc.perform(StripeTestSupport.delivery(body, stale)).andExpect(status().isBadRequest());

        assertThat(paymentLines(owner.policyId())).isEmpty();
    }

    // --- AC5: paid ---

    @Test
    void aSucceededChargeMarksThePaymentPaidAndPostsOnePaymentLine() throws Exception {
        Owner owner = owner("Paid Up Bakery LLC");
        Started p = started(owner);
        String eventId = StripeTestSupport.newEventId();

        deliver(StripeTestSupport.chargeSucceeded(eventId, p.intentId(), p.amountCents(), "4242", "pm_paid"));

        assertThat(paymentStatus(jdbc, p.paymentId())).isEqualTo("SUCCEEDED");
        Map<String, Object> payment = jdbc.sql("select card_last4, card_brand, payment_method_id, settled_at from payments where id = ?")
                .param(p.paymentId()).query().singleRow();
        assertThat(payment.get("card_last4")).isEqualTo("4242");
        assertThat(payment.get("card_brand")).isEqualTo("visa");
        assertThat(payment.get("payment_method_id")).isEqualTo("pm_paid");
        assertThat(payment.get("settled_at")).isNotNull();

        List<Map<String, Object>> lines = paymentLines(owner.policyId());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).get("amount_cents")).isEqualTo(-BASE_DUE_CENTS);
        assertThat(lines.get(0).get("description")).isEqualTo("Payment, card ending 4242");
        assertThat(((Number) lines.get(0).get("payment_id")).longValue()).isEqualTo(p.paymentId());
        assertThat(lines.get(0).get("stripe_event_id")).isEqualTo(eventId);
        assertThat(lines.get(0).get("policy_transaction_id")).isNull();
        assertThat(balance(jdbc, owner.policyId())).isZero();
        assertThat(recorded(eventId)).isTrue();
    }

    // --- AC6: didn't go through ---

    @Test
    void aFailedChargeMarksThePaymentFailedAndPostsANoMoneyMovedLine() throws Exception {
        Owner owner = owner("Declined Card Bakery LLC");
        Started p = started(owner);
        String eventId = StripeTestSupport.newEventId();

        deliver(StripeTestSupport.chargeFailed(eventId, p.intentId(), p.amountCents(), "0002", "Your card was declined."));

        assertThat(paymentStatus(jdbc, p.paymentId())).isEqualTo("FAILED");
        Map<String, Object> payment = jdbc.sql("select card_last4, failure_reason from payments where id = ?")
                .param(p.paymentId()).query().singleRow();
        assertThat(payment.get("card_last4")).isEqualTo("0002");
        assertThat(payment.get("failure_reason")).isEqualTo("Your card was declined.");

        List<Map<String, Object>> lines = paymentLines(owner.policyId());
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).get("amount_cents")).isEqualTo(0L);
        assertThat(lines.get(0).get("description")).isEqualTo("Payment attempt, card ending 0002");
        assertThat(lines.get(0).get("stripe_event_id")).isEqualTo(eventId);
        assertThat(balance(jdbc, owner.policyId())).isEqualTo(BASE_DUE_CENTS);
    }

    // --- AC7: waiting for the bank ---

    @Test
    void aProcessingIntentMarksThePaymentProcessingWithoutTouchingTheLedger() throws Exception {
        Owner owner = owner("Waiting Bank Bakery LLC");
        Started p = started(owner);

        deliver(StripeTestSupport.intentProcessing(StripeTestSupport.newEventId(), p.intentId(), p.amountCents()));

        assertThat(paymentStatus(jdbc, p.paymentId())).isEqualTo("PROCESSING");
        assertThat(paymentLines(owner.policyId())).isEmpty();
        assertThat(balance(jdbc, owner.policyId())).isEqualTo(BASE_DUE_CENTS);
    }

    // --- AC9: each event once ---

    @Test
    void replayingAnEventIsAnsweredOkButPostsNothingNew() throws Exception {
        Owner owner = owner("Replay Bakery LLC");
        Started p = started(owner);
        String body = StripeTestSupport.chargeSucceeded(StripeTestSupport.newEventId(), p.intentId(), p.amountCents(), "4242", "pm_r");

        deliver(body);
        deliver(body);
        deliver(body);

        assertThat(paymentLines(owner.policyId())).hasSize(1);
        assertThat(balance(jdbc, owner.policyId())).isZero();
    }

    @Test
    void twoCopiesOfAnEventArrivingTogetherPostOneLine() throws Exception {
        Owner owner = owner("Twin Delivery Bakery LLC");
        Started p = started(owner);
        String body = StripeTestSupport.chargeSucceeded(StripeTestSupport.newEventId(), p.intentId(), p.amountCents(), "4242", "pm_t");

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            Callable<Integer> send = () -> mvc.perform(StripeTestSupport.delivery(body)).andReturn().getResponse().getStatus();
            List<Future<Integer>> answers = pool.invokeAll(List.of(send, send, send, send));
            for (Future<Integer> answer : answers) {
                assertThat(answer.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(paymentLines(owner.policyId())).hasSize(1);
        assertThat(balance(jdbc, owner.policyId())).isZero();
    }

    // --- AC10: events we don't act on, and events out of order ---

    @Test
    void anEventTypeWeDontUseIsRecordedAndIgnored() throws Exception {
        String eventId = StripeTestSupport.newEventId();

        deliver(StripeTestSupport.otherEvent(eventId, "customer.created"));

        assertThat(recorded(eventId)).isTrue();
    }

    @Test
    void aChargeForAPaymentWeNeverStartedIsIgnored() throws Exception {
        String eventId = StripeTestSupport.newEventId();
        deliver(StripeTestSupport.chargeSucceeded(eventId, "pi_not_ours", 5000, "4242", "pm_x"));

        assertThat(jdbc.sql("select count(*) from ledger_entries where stripe_event_id = ?").param(eventId)
                .query(Long.class).single()).isZero();
        assertThat(recorded(eventId)).isTrue();
    }

    @Test
    void moneyThatMovesAfterAFailureIsStillRecorded() throws Exception {
        Owner owner = owner("Late Success Bakery LLC");
        Started p = started(owner);

        deliver(StripeTestSupport.chargeFailed(StripeTestSupport.newEventId(), p.intentId(), p.amountCents(), "0002", "Your card was declined."));
        deliver(StripeTestSupport.chargeSucceeded(StripeTestSupport.newEventId(), p.intentId(), p.amountCents(), "4242", "pm_late"));

        assertThat(paymentStatus(jdbc, p.paymentId())).isEqualTo("SUCCEEDED");
        assertThat(paymentLines(owner.policyId())).extracting(row -> row.get("amount_cents"))
                .containsExactly(0L, -BASE_DUE_CENTS);
        assertThat(balance(jdbc, owner.policyId())).isZero();
    }

    @Test
    void aLateProcessingEventNeverUndoesAPayment() throws Exception {
        Owner owner = owner("Late Processing Bakery LLC");
        Started p = started(owner);

        deliver(StripeTestSupport.chargeSucceeded(StripeTestSupport.newEventId(), p.intentId(), p.amountCents(), "4242", "pm_lp"));
        deliver(StripeTestSupport.intentProcessing(StripeTestSupport.newEventId(), p.intentId(), p.amountCents()));

        assertThat(paymentStatus(jdbc, p.paymentId())).isEqualTo("SUCCEEDED");
        assertThat(paymentLines(owner.policyId())).hasSize(1);
    }
}
