package app.plainly.billing;

import static app.plainly.CorrectionTestData.OCT_22;
import static app.plainly.CorrectionTestData.REASON;
import static app.plainly.PaymentTestData.balance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.CorrectionTestData;
import app.plainly.CorrectionTestData.Paid;
import app.plainly.FakeStripeGateway;
import app.plainly.IntegrationTest;
import app.plainly.StripeTestSupport;
import app.plainly.common.NotFoundException;
import app.plainly.common.ValidationException;
import app.plainly.policy.AddCoverageService;
import app.plainly.policy.CorrectCoverageCommand;
import app.plainly.policy.CorrectCoverageService;
import app.plainly.policy.IssuePolicyService;
import app.plainly.policy.PolicyService;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The refund rules without HTTP: retry starts a new attempt only for a failed refund, and an event about an attempt
 * a retry has replaced changes nothing. Scenario as in {@link RefundWebhookTest}: $14.00 back to card 4242.
 */
// covers AC10, AC11, T9, T17, T18
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

    /** The refund as a Stripe event would describe it. */
    static RefundService.SeenRefund seen(RefundRepository.Locked r) {
        return new RefundService.SeenRefund(r.stripeRefundId(), r.intentId(), r.amountCents());
    }

    RefundRepository.Locked correctedButTimedOut(String business, FakeStripeGateway.RefundTimeout how) throws Exception {
        stripe.timeOutRefunds(how);
        try {
            return corrected(business, false);
        } finally {
            stripe.timeOutRefunds(null);
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
        stripe.setRefundStatus(first.stripeRefundId(), "failed");
        refunds.fail(first.id(), seen(first), "expired_or_canceled_card");
        refunds.retry(first.id());

        // A late "succeeded" for attempt 1's Stripe refund must not settle attempt 2.
        refunds.settle(first.id(), seen(first), "evt_stale_" + first.id());

        assertThat(repository.find(first.id()).orElseThrow().status()).isEqualTo("PENDING");
        assertThat(refundLines(first.policyId())).isZero();
        assertThat(balance(jdbc, first.policyId())).isEqualTo(-1_400);
    }

    @Test
    void settlingTwicePostsOneRefundLine() throws Exception {
        RefundRepository.Locked r = corrected("Refund Service Settle Twice LLC", false);

        refunds.settle(r.id(), seen(r), "evt_one_" + r.id());
        refunds.settle(r.id(), seen(r), "evt_two_" + r.id());

        assertThat(refundLines(r.policyId())).isEqualTo(1);
        assertThat(balance(jdbc, r.policyId())).isZero();
    }

    // --- review fixes: a send we never heard back from is never sent twice ---

    @Test
    void aSendWhoseAnswerNeverCameBackStaysPendingInsteadOfFailed() throws Exception {
        RefundRepository.Locked r = correctedButTimedOut("Refund Timeout Pending LLC",
                FakeStripeGateway.RefundTimeout.MADE_ANYWAY);

        // Not FAILED, so B3 offers no Retry that would send the money a second time under a new key.
        assertThat(r.status()).isEqualTo("PENDING");
        assertThat(r.attempt()).isEqualTo(1);
        assertThat(r.stripeRefundId()).isNull();
        assertThatThrownBy(() -> refunds.retry(r.id())).isInstanceOf(ValidationException.class);
    }

    @Test
    void resendingARefundStripeMadeAdoptsItInsteadOfMakingAnother() throws Exception {
        RefundRepository.Locked r = correctedButTimedOut("Refund Timeout Adopt LLC",
                FakeStripeGateway.RefundTimeout.MADE_ANYWAY);

        refunds.resendUnsent(Duration.ZERO);

        RefundRepository.Locked now = repository.find(r.id()).orElseThrow();
        assertThat(stripe.refundsMadeOn(r.intentId())).singleElement().isEqualTo(now.stripeRefundId());
        assertThat(stripe.refundsFor(r.intentId())).hasSize(1);
        assertThat(now.status()).isEqualTo("PENDING");

        refunds.settle(r.id(), seen(now), "evt_adopted_" + r.id());
        assertThat(refundLines(r.policyId())).isEqualTo(1);
        assertThat(balance(jdbc, r.policyId())).isZero();
    }

    @Test
    void resendingARefundStripeNeverGotUsesTheSameKey() throws Exception {
        RefundRepository.Locked r = correctedButTimedOut("Refund Timeout Same Key LLC",
                FakeStripeGateway.RefundTimeout.NOT_MADE);

        refunds.resendUnsent(Duration.ZERO);

        assertThat(stripe.refundsFor(r.intentId())).extracting(FakeStripeGateway.RefundCall::idempotencyKey)
                .containsExactly("refund-" + r.id() + "-1", "refund-" + r.id() + "-1");
        assertThat(stripe.refundsMadeOn(r.intentId())).hasSize(1);
        assertThat(repository.find(r.id()).orElseThrow().stripeRefundId()).isNotNull();
    }

    @Test
    void whenStripeCantBeAskedTheRefundWaitsPendingForTheNextSweep() throws Exception {
        stripe.refundLookupsDown(true);
        RefundRepository.Locked r;
        try {
            r = corrected("Refund Lookup Down LLC", false);
        } finally {
            stripe.refundLookupsDown(false);
        }

        assertThat(r.status()).isEqualTo("PENDING");
        assertThat(r.stripeRefundId()).isNull();
        assertThat(stripe.refundsFor(r.intentId())).isEmpty();

        refunds.resendUnsent(Duration.ZERO);
        assertThat(repository.find(r.id()).orElseThrow().stripeRefundId()).isNotNull();
    }

    @Test
    void aRetryAdoptsAnEarlierAttemptStripeStillHolds() throws Exception {
        RefundRepository.Locked r = corrected("Refund Retry Adopt LLC", false);
        // Our side failed it, but Stripe still has the refund on its way: a retry must not make a second one.
        jdbc.sql("update refunds set status = 'FAILED' where id = ?").param(r.id()).update();

        refunds.retry(r.id());

        assertThat(stripe.refundsMadeOn(r.intentId())).containsExactly(r.stripeRefundId());
        assertThat(repository.find(r.id()).orElseThrow().stripeRefundId()).isEqualTo(r.stripeRefundId());
    }

    @Test
    void anEventWhoseAmountOrPaymentDoesntMatchIsIgnored() throws Exception {
        RefundRepository.Locked r = corrected("Refund Event Mismatch LLC", false);

        refunds.settle(r.id(), new RefundService.SeenRefund(r.stripeRefundId(), r.intentId(), 99_900),
                "evt_amount_" + r.id());
        refunds.settle(r.id(), new RefundService.SeenRefund(r.stripeRefundId(), "pi_someone_else", r.amountCents()),
                "evt_intent_" + r.id());
        refunds.fail(r.id(), new RefundService.SeenRefund(r.stripeRefundId(), r.intentId(), 1), "nope");

        assertThat(repository.find(r.id()).orElseThrow().status()).isEqualTo("PENDING");
        assertThat(refundLines(r.policyId())).isZero();
    }

    // --- review fixes: settling waits for a correction holding the policy ---

    @Autowired PolicyService policies;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void aRefundWebhookWaitsWhileACorrectionHoldsThePolicy() throws Exception {
        RefundRepository.Locked r = corrected("Refund Settle Waits LLC", false);
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> correction = pool.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                policies.lock(r.policyId());
                held.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
            String event = StripeTestSupport.refundSucceeded(StripeTestSupport.newEventId(), r.stripeRefundId(), r.id(),
                    r.intentId(), r.amountCents());
            Future<?> webhook = pool.submit(() -> {
                mvc.perform(StripeTestSupport.delivery(event)).andExpect(status().isOk());
                return null;
            });

            Thread.sleep(500);
            assertThat(webhook.isDone()).as("settle must queue behind the policy lock").isFalse();
            assertThat(refundLines(r.policyId())).isZero();

            release.countDown();
            correction.get(10, TimeUnit.SECONDS);
            webhook.get(10, TimeUnit.SECONDS);
            assertThat(refundLines(r.policyId())).isEqualTo(1);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
