package app.plainly.billing;

import static app.plainly.CorrectionTestData.OCT_22;
import static app.plainly.CorrectionTestData.REASON;
import static app.plainly.PaymentTestData.balance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.plainly.CorrectionTestData;
import app.plainly.CorrectionTestData.Paid;
import app.plainly.FakeStripeGateway;
import app.plainly.IntegrationTest;
import app.plainly.common.NotFoundException;
import app.plainly.common.ValidationException;
import app.plainly.policy.AddCoverageService;
import app.plainly.policy.CorrectCoverageCommand;
import app.plainly.policy.CorrectCoverageService;
import app.plainly.policy.IssuePolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The refund rules without HTTP: retry starts a new attempt only for a failed refund, and an event about an attempt
 * a retry has replaced changes nothing. Scenario as in {@link RefundWebhookTest}: $14.00 back to card 4242.
 */
// covers AC10, AC11, T9
@IntegrationTest
@AutoConfigureMockMvc
class RefundServiceTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issuer;
    @Autowired AddCoverageService adder;
    @Autowired CorrectCoverageService corrector;
    @Autowired FakeStripeGateway stripe;
    @Autowired RefundService refunds;
    @Autowired RefundRepository repository;

    RefundRepository.Locked corrected(String business, boolean refused) throws Exception {
        if (refused) {
            stripe.failRefunds("The card can't take refunds.");
        }
        try {
            Paid paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issuer, adder, business);
            CorrectCoverageService.Corrected saved = corrector.correct(paid.owner().policyId(),
                    new CorrectCoverageCommand(OCT_22, REASON), CorrectionTestData.brokerId(jdbc));
            long id = jdbc.sql("select id from refunds where display_id = ?")
                    .param(saved.refunds().getFirst().displayId()).query(Long.class).single();
            return repository.find(id).orElseThrow();
        } finally {
            stripe.failRefunds(null);
        }
    }

    long refundLines(long policyId) {
        return jdbc.sql("select count(*) from ledger_entries where policy_id = ? and entry_type = 'REFUND'")
                .param(policyId).query(Long.class).single();
    }

    @Test
    void retryingAFailedRefundSendsTheNextAttemptWithItsOwnKey() throws Exception {
        RefundRepository.Locked r = corrected("Refund Service Retry LLC", true);

        RefundService.Attempted now = refunds.retry(r.id());

        assertThat(now.status()).isEqualTo("PENDING");
        assertThat(now.attempt()).isEqualTo(2);
        assertThat(stripe.refundsFor(r.intentId())).extracting(FakeStripeGateway.RefundCall::idempotencyKey)
                .containsExactly("refund-" + r.id() + "-1", "refund-" + r.id() + "-2");
    }

    @Test
    void aRetryStripeRefusesAgainStaysFailedWithTheReason() throws Exception {
        RefundRepository.Locked r = corrected("Refund Service Refused Twice LLC", true);
        stripe.failRefunds("Still refused.");
        RefundService.Attempted now;
        try {
            now = refunds.retry(r.id());
        } finally {
            stripe.failRefunds(null);
        }

        assertThat(now.status()).isEqualTo("FAILED");
        assertThat(now.attempt()).isEqualTo(2);
        assertThat(jdbc.sql("select failure_reason from refunds where id = ?").param(r.id()).query(String.class)
                .single()).isEqualTo("Still refused.");
    }

    @Test
    void onlyAFailedRefundCanBeRetried() throws Exception {
        RefundRepository.Locked r = corrected("Refund Service Not Failed LLC", false);

        assertThatThrownBy(() -> refunds.retry(r.id())).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.fields()).containsKey("refund"));
        assertThat(repository.find(r.id()).orElseThrow().attempt()).isEqualTo(1);
    }

    @Test
    void retryingAnUnknownRefundIsNotFound() {
        assertThatThrownBy(() -> refunds.retry(999_999_999L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anEventForAnAttemptARetryReplacedChangesNothing() throws Exception {
        RefundRepository.Locked first = corrected("Refund Service Stale LLC", false);
        refunds.fail(first.id(), first.stripeRefundId(), "expired_or_canceled_card");
        refunds.retry(first.id());

        // A late "succeeded" for attempt 1's Stripe refund must not settle attempt 2.
        refunds.settle(first.id(), first.stripeRefundId(), "evt_stale_" + first.id());

        assertThat(repository.find(first.id()).orElseThrow().status()).isEqualTo("PENDING");
        assertThat(refundLines(first.policyId())).isZero();
        assertThat(balance(jdbc, first.policyId())).isEqualTo(-1_400);
    }

    @Test
    void settlingTwicePostsOneRefundLine() throws Exception {
        RefundRepository.Locked r = corrected("Refund Service Settle Twice LLC", false);

        refunds.settle(r.id(), r.stripeRefundId(), "evt_one_" + r.id());
        refunds.settle(r.id(), r.stripeRefundId(), "evt_two_" + r.id());

        assertThat(refundLines(r.policyId())).isEqualTo(1);
        assertThat(balance(jdbc, r.policyId())).isZero();
    }
}
