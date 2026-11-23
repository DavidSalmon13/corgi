package app.plainly.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.plainly.FakeEmailGateway;
import app.plainly.IntegrationTest;
import app.plainly.PaymentTestData;
import app.plainly.TestData;
import app.plainly.notify.NotifyEvents.PaymentSettled;
import app.plainly.notify.NotifyEvents.PolicyIssued;
import app.plainly.notify.NotifyEvents.RefundSettled;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// covers AC2, AC3, AC4, AC5, AC6, T6, T6.1
/**
 * An email is claimed in the transaction of the change that causes it and sent only after that transaction
 * commits: never for a change that rolled back, once per source, and a failed or switched-off send never undoes
 * the change.
 */
@IntegrationTest
class NotifyServiceTest {

    /** Payment and refund ids no other test uses; the rows are never cleaned up. */
    private static final AtomicLong SOURCE = new AtomicLong(2_000_000_000L + System.nanoTime() % 1_000_000_000L);

    @Autowired JdbcClient jdbc;
    @Autowired ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager txManager;
    @Autowired FakeEmailGateway email;

    TransactionTemplate tx;
    long policyId;
    String owner;

    @BeforeEach
    void aPolicy() {
        tx = new TransactionTemplate(txManager);
        policyId = TestData.policy(jdbc, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 9, 1));
        owner = jdbc.sql("select c.owner_email from policies p join customers c on c.id = p.customer_id where p.id = ?")
                .param(policyId).query(String.class).single();
    }

    @Test
    void theIssueEmailIsSentOnceTheTransactionCommits() {
        tx.executeWithoutResult(s -> {
            events.publishEvent(new PolicyIssued(policyId, 120_000));
            assertThat(email.sentTo(owner)).as("nothing before commit").isEmpty();
        });

        assertThat(email.sentTo(owner)).singleElement().satisfies(sent -> {
            assertThat(sent.message().subject()).isEqualTo("Your cover with Plainly starts Sep 1, 2026");
            assertThat(sent.message().text()).contains("Test Owner", "Test Shop LLC", "$1,200.00 for the year");
            assertThat(sent.idempotencyKey())
                    .isEqualTo("POLICY_ISSUED/" + policyId + "/" + sendKey(EmailKind.POLICY_ISSUED, policyId));
        });
        assertThat(row(EmailKind.POLICY_ISSUED, policyId)).isEqualTo("SENT");
    }

    @Test
    void aChangeThatRollsBackSendsNothing() {
        tx.executeWithoutResult(s -> {
            events.publishEvent(new PolicyIssued(policyId, 120_000));
            s.setRollbackOnly();
        });

        assertThat(email.sentTo(owner)).isEmpty();
        assertThat(row(EmailKind.POLICY_ISSUED, policyId)).isNull();
    }

    @Test
    void theSameEmailIsSentOnlyOnce() {
        long paymentId = SOURCE.incrementAndGet();
        for (int i = 0; i < 2; i++) {
            tx.executeWithoutResult(s -> events.publishEvent(new PaymentSettled(paymentId, policyId, 64_200, "4242")));
        }

        assertThat(email.sentTo(owner)).hasSize(1);
    }

    @Test
    void thePaymentReceiptSaysWhatIsLeftToPayAfterIt() {
        PaymentTestData.addCharge(jdbc, policyId, 1_400);
        long paymentId = SOURCE.incrementAndGet();

        tx.executeWithoutResult(s -> events.publishEvent(new PaymentSettled(paymentId, policyId, 64_200, "4242")));

        assertThat(email.sentTo(owner)).singleElement().satisfies(sent -> {
            assertThat(sent.message().subject()).isEqualTo("We got your payment of $642.00");
            assertThat(sent.message().text()).contains("card ending 4242", "Nov 22, 2026", "Left to pay: $14.00");
        });
    }

    @Test
    void aRefundReceiptSaysWhyFromTheChangeThatCausedIt() {
        tx.executeWithoutResult(s -> events.publishEvent(
                new RefundSettled(SOURCE.incrementAndGet(), policyId, 1_400, "4242", "CORRECT")));
        tx.executeWithoutResult(s -> events.publishEvent(
                new RefundSettled(SOURCE.incrementAndGet(), policyId, 30_000, "4242", "CANCEL")));

        assertThat(email.sentTo(owner)).extracting(sent -> sent.message().text())
                .satisfiesExactly(t -> assertThat(t).contains("$14.00", "moved your cover's start date"),
                        t -> assertThat(t).contains("$300.00", "your cover ended early"));
    }

    @Test
    void whenResendFailsTheChangeStandsAndTheEmailIsMarkedFailed() {
        long paymentId = SOURCE.incrementAndGet();
        email.setFailing(true);
        try {
            tx.executeWithoutResult(s -> {
                PaymentTestData.addCharge(jdbc, policyId, 500);
                events.publishEvent(new PaymentSettled(paymentId, policyId, 500, "4242"));
            });
        } finally {
            email.setFailing(false);
        }

        assertThat(PaymentTestData.balance(jdbc, policyId)).as("the change committed").isEqualTo(500);
        assertThat(row(EmailKind.PAYMENT_RECEIPT, paymentId)).isEqualTo("FAILED");
        assertThat(jdbc.sql("select error from sent_emails where kind = 'PAYMENT_RECEIPT' and source_id = ?")
                .param(paymentId).query(String.class).single()).contains("503");
    }

    @Test
    void withEmailOffTheEmailIsRecordedAsSkipped() {
        long paymentId = SOURCE.incrementAndGet();
        email.setEnabled(false);
        try {
            tx.executeWithoutResult(s -> events.publishEvent(new PaymentSettled(paymentId, policyId, 500, "4242")));
        } finally {
            email.setEnabled(true);
        }

        assertThat(email.sentTo(owner)).isEmpty();
        assertThat(row(EmailKind.PAYMENT_RECEIPT, paymentId)).isEqualTo("SKIPPED");
    }

    @Test
    void anEmailOutsideATransactionIsAMistake() {
        assertThatThrownBy(() -> events.publishEvent(new PolicyIssued(policyId, 120_000)))
                .isInstanceOf(IllegalStateException.class);
    }

    // covers AC6 (code review): an email that can't be written never undoes the money change behind it
    @Test
    void anEmailThatCantBeWrittenNeverUndoesTheChange() {
        long paymentId = SOURCE.incrementAndGet();
        long noSuchPolicy = 999_999_999L;

        tx.executeWithoutResult(s -> {
            PaymentTestData.addCharge(jdbc, policyId, 700);
            events.publishEvent(new PaymentSettled(paymentId, noSuchPolicy, 700, "4242"));
        });

        assertThat(PaymentTestData.balance(jdbc, policyId)).as("the change committed").isEqualTo(700);
        assertThat(row(EmailKind.PAYMENT_RECEIPT, paymentId)).isNull();
    }

    // covers AC5 (code review): Resend's key is unique to this database's claim, not just to the source id, so a
    // second database (local and Railway share one Resend account) never reuses a key Resend still remembers
    @Test
    void resendsKeyCarriesTheClaimsOwnSendKey() {
        long paymentId = SOURCE.incrementAndGet();

        tx.executeWithoutResult(s -> events.publishEvent(new PaymentSettled(paymentId, policyId, 500, "4242")));

        String key = sendKey(EmailKind.PAYMENT_RECEIPT, paymentId);
        assertThat(key).matches("[0-9a-f-]{36}");
        assertThat(email.sentTo(owner)).singleElement().extracting(FakeEmailGateway.Sent::idempotencyKey)
                .isEqualTo("PAYMENT_RECEIPT/" + paymentId + "/" + key);
    }

    private String sendKey(EmailKind kind, long sourceId) {
        return jdbc.sql("select send_key::text from sent_emails where kind = ? and source_id = ?")
                .params(kind.name(), sourceId).query(String.class).single();
    }

    private String row(EmailKind kind, long sourceId) {
        return jdbc.sql("select status from sent_emails where kind = ? and source_id = ?")
                .params(kind.name(), sourceId).query(String.class).optional().orElse(null);
    }
}
