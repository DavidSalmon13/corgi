package app.plainly.billing;

import static app.plainly.CorrectionTestData.OCT_22;
import static app.plainly.CorrectionTestData.REASON;
import static app.plainly.PaymentTestData.balance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.AuthTestSupport;
import app.plainly.CorrectionTestData;
import app.plainly.CorrectionTestData.Paid;
import app.plainly.FakeStripeGateway;
import app.plainly.IntegrationTest;
import app.plainly.StripeTestSupport;
import app.plainly.policy.AddCoverageService;
import app.plainly.policy.CorrectCoverageCommand;
import app.plainly.policy.CorrectCoverageService;
import app.plainly.policy.IssuePolicyService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Money we owe goes back through Stripe, and only a signed webhook says it arrived. The scenario: extra cover from
 * Oct 15 was paid ($642.00, card 4242), then corrected to Oct 22, so $14.00 is refunded to that payment.
 */
// covers AC10, AC11, T2
@IntegrationTest
@AutoConfigureMockMvc
class RefundWebhookTest {

    static final String DECLINED = "The card can't take refunds.";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issuer;
    @Autowired AddCoverageService adder;
    @Autowired CorrectCoverageService corrector;
    @Autowired FakeStripeGateway stripe;

    /** One refund as stored, plus the payment it goes back to. */
    record Refund(long id, String displayId, String status, int attempt, String stripeRefundId, String failureReason,
            long amountCents, long policyId, String intentId) {}

    /** Saves the Oct 15 → Oct 22 correction, which records one $14.00 refund and sends it after commit. */
    Refund correctedWithARefund(String business) throws Exception {
        Paid paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issuer, adder, business);
        CorrectCoverageService.Corrected saved = corrector.correct(paid.owner().policyId(),
                new CorrectCoverageCommand(OCT_22, REASON), CorrectionTestData.brokerId(jdbc));
        assertThat(saved.refunds()).hasSize(1);
        return refund(saved.refunds().getFirst().displayId());
    }

    Refund refund(String displayId) {
        return jdbc.sql("""
                select r.id, r.display_id, r.status, r.attempt, r.stripe_refund_id, r.failure_reason, r.amount_cents,
                       r.policy_id, p.stripe_payment_intent_id
                from refunds r join payments p on p.id = r.payment_id where r.display_id = ?""")
                .param(displayId)
                .query((rs, n) -> new Refund(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getString(5), rs.getString(6), rs.getLong(7), rs.getLong(8), rs.getString(9)))
                .single();
    }

    Refund reread(Refund r) {
        return refund(r.displayId());
    }

    List<Map<String, Object>> refundLines(long policyId) {
        return jdbc.sql("""
                select amount_cents, description, refund_id, stripe_event_id, payment_id, policy_transaction_id
                from ledger_entries where policy_id = ? and entry_type = 'REFUND' order by id""")
                .param(policyId).query().listOfRows();
    }

    void deliver(String body) throws Exception {
        mvc.perform(StripeTestSupport.delivery(body)).andExpect(status().isOk());
    }

    String succeeded(Refund r, String eventId) {
        return StripeTestSupport.refundSucceeded(eventId, r.stripeRefundId(), r.id(), r.intentId(), r.amountCents());
    }

    // --- recorded with the correction, sent after commit (AC10) ---

    @Test
    void theRefundIsRecordedPendingAndSentToStripeWithAKeyForThisAttempt() throws Exception {
        Refund r = correctedWithARefund("Refund Sent Bakery LLC");

        assertThat(r.displayId()).matches("RF-\\d{4,}");
        assertThat(r.status()).isEqualTo("PENDING");
        assertThat(r.attempt()).isEqualTo(1);
        assertThat(r.amountCents()).isEqualTo(1_400);
        assertThat(r.stripeRefundId()).isNotBlank();
        assertThat(stripe.refundsFor(r.intentId())).singleElement().satisfies(call -> {
            assertThat(call.amountCents()).isEqualTo(1_400);
            assertThat(call.idempotencyKey()).isEqualTo("refund-" + r.id() + "-1");
            assertThat(call.metadata()).containsEntry("refund_id", String.valueOf(r.id()));
        });
        // Nothing is posted until Stripe says the money went: the customer is owed $14.00 meanwhile.
        assertThat(refundLines(r.policyId())).isEmpty();
        assertThat(balance(jdbc, r.policyId())).isEqualTo(-1_400);
    }

    // --- settled only by a signed webhook, once (AC10) ---

    @Test
    void aSucceededRefundPostsOneRefundLineTracedToTheRefundAndTheEvent() throws Exception {
        Refund r = correctedWithARefund("Refund Settled Bakery LLC");
        String eventId = StripeTestSupport.newEventId();

        deliver(succeeded(r, eventId));

        assertThat(reread(r).status()).isEqualTo("SUCCEEDED");
        List<Map<String, Object>> lines = refundLines(r.policyId());
        assertThat(lines).singleElement().satisfies(line -> {
            assertThat(line.get("amount_cents")).isEqualTo(1_400L);
            assertThat(line.get("description")).isEqualTo("Refund to card ending 4242");
            assertThat(((Number) line.get("refund_id")).longValue()).isEqualTo(r.id());
            assertThat(line.get("stripe_event_id")).isEqualTo(eventId);
        });
        assertThat(balance(jdbc, r.policyId())).isZero();
    }

    @Test
    void replayingTheEventPostsNothingNew() throws Exception {
        Refund r = correctedWithARefund("Refund Replay Bakery LLC");
        String body = succeeded(r, StripeTestSupport.newEventId());

        deliver(body);
        deliver(body);

        assertThat(refundLines(r.policyId())).hasSize(1);
        assertThat(balance(jdbc, r.policyId())).isZero();
    }

    @Test
    void aSecondSucceededEventForTheSameRefundPostsNothingNew() throws Exception {
        Refund r = correctedWithARefund("Refund Twice Bakery LLC");

        deliver(succeeded(r, StripeTestSupport.newEventId()));
        deliver(StripeTestSupport.refundEvent(StripeTestSupport.newEventId(), "refund.created", r.stripeRefundId(),
                r.id(), r.intentId(), r.amountCents(), "succeeded", null));

        assertThat(refundLines(r.policyId())).hasSize(1);
    }

    @Test
    void aPendingRefundEventChangesNothing() throws Exception {
        Refund r = correctedWithARefund("Refund Pending Bakery LLC");

        deliver(StripeTestSupport.refundEvent(StripeTestSupport.newEventId(), "refund.created", r.stripeRefundId(),
                r.id(), r.intentId(), r.amountCents(), "pending", null));

        assertThat(reread(r).status()).isEqualTo("PENDING");
        assertThat(refundLines(r.policyId())).isEmpty();
    }

    @Test
    void aRefundWeNeverStartedIsRecordedAndIgnored() throws Exception {
        String eventId = StripeTestSupport.newEventId();

        deliver(StripeTestSupport.refundSucceeded(eventId, "re_not_ours", 999_999_999L, "pi_not_ours", 1_400));

        assertThat(jdbc.sql("select count(*) from processed_stripe_events where event_id = ?").param(eventId)
                .query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from ledger_entries where stripe_event_id = ?").param(eventId)
                .query(Long.class).single()).isZero();
    }

    // --- failures are visible, with the reason (AC10, AC11) ---

    @Test
    void aFailedRefundEventMarksTheRefundFailedWithTheReason() throws Exception {
        Refund r = correctedWithARefund("Refund Failed Event Bakery LLC");

        deliver(StripeTestSupport.refundFailed(StripeTestSupport.newEventId(), r.stripeRefundId(), r.id(), r.intentId(),
                r.amountCents(), "expired_or_canceled_card"));

        Refund now = reread(r);
        assertThat(now.status()).isEqualTo("FAILED");
        assertThat(now.failureReason()).isEqualTo("expired_or_canceled_card");
        assertThat(refundLines(r.policyId())).isEmpty();
        assertThat(balance(jdbc, r.policyId())).isEqualTo(-1_400);
    }

    @Test
    void aRefundStripeRefusesToSendIsFailedButTheCorrectionStaysSaved() throws Exception {
        stripe.failRefunds(DECLINED);
        Refund r;
        try {
            r = correctedWithARefund("Refund Refused Bakery LLC");
        } finally {
            stripe.failRefunds(null);
        }

        assertThat(r.status()).isEqualTo("FAILED");
        assertThat(r.failureReason()).isEqualTo(DECLINED);
        assertThat(r.stripeRefundId()).isNull();
        assertThat(jdbc.sql("select count(*) from policy_transactions where policy_id = ? and type = 'CORRECT'")
                .param(r.policyId()).query(Long.class).single()).isEqualTo(1);
        assertThat(balance(jdbc, r.policyId())).isEqualTo(-1_400);
    }

    // --- retry: a new attempt for the same refund, broker only (AC11) ---

    @Test
    void retryingAFailedRefundSendsAttemptTwoWithANewKey() throws Exception {
        stripe.failRefunds(DECLINED);
        Refund r;
        try {
            r = correctedWithARefund("Refund Retry Bakery LLC");
        } finally {
            stripe.failRefunds(null);
        }

        mvc.perform(post("/api/broker/refunds/" + r.id() + "/retry").cookie(AuthTestSupport.signInAsBroker(mvc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayId").value(r.displayId()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempt").value(2));

        Refund now = reread(r);
        assertThat(now.status()).isEqualTo("PENDING");
        assertThat(now.attempt()).isEqualTo(2);
        assertThat(now.failureReason()).isNull();
        assertThat(now.stripeRefundId()).isNotBlank();
        assertThat(stripe.refundsFor(r.intentId())).extracting(FakeStripeGateway.RefundCall::idempotencyKey)
                .containsExactly("refund-" + r.id() + "-1", "refund-" + r.id() + "-2");

        deliver(succeeded(now, StripeTestSupport.newEventId()));
        assertThat(refundLines(r.policyId())).hasSize(1);
        assertThat(balance(jdbc, r.policyId())).isZero();
    }

    @Test
    void onlyAFailedRefundCanBeRetried() throws Exception {
        Refund r = correctedWithARefund("Refund No Retry Bakery LLC");

        mvc.perform(post("/api/broker/refunds/" + r.id() + "/retry").cookie(AuthTestSupport.signInAsBroker(mvc)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.refund").isNotEmpty());

        assertThat(reread(r).attempt()).isEqualTo(1);
        assertThat(stripe.refundsFor(r.intentId())).hasSize(1);
    }

    @Test
    void aPolicyholderCannotRetryARefund() throws Exception {
        stripe.failRefunds(DECLINED);
        Paid paid;
        Refund r;
        try {
            paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issuer, adder, "Refund Owner Retry LLC");
            CorrectCoverageService.Corrected saved = corrector.correct(paid.owner().policyId(),
                    new CorrectCoverageCommand(OCT_22, REASON), CorrectionTestData.brokerId(jdbc));
            r = refund(saved.refunds().getFirst().displayId());
        } finally {
            stripe.failRefunds(null);
        }

        mvc.perform(post("/api/broker/refunds/" + r.id() + "/retry").cookie(paid.owner().session()))
                .andExpect(status().isForbidden());

        assertThat(reread(r).status()).isEqualTo("FAILED");
        assertThat(reread(r).attempt()).isEqualTo(1);
    }

    @Test
    void retryingAnUnknownRefundIsNotFound() throws Exception {
        mvc.perform(post("/api/broker/refunds/999999999/retry").cookie(AuthTestSupport.signInAsBroker(mvc)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }
}
