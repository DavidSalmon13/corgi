package app.plainly.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.plainly.IntegrationTest;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

// covers AC5, AC6, T2, T3
/** The V10 schema and the SQL on {@code sent_emails}: one row per (kind, source), and how a send ends. */
@IntegrationTest
class SentEmailRepositoryTest {

    /** Source ids no other test uses; the rows are never cleaned up. */
    private static final AtomicLong SOURCE = new AtomicLong(System.nanoTime() % 1_000_000_000L);

    @Autowired JdbcClient jdbc;
    @Autowired SentEmailRepository emails;

    @Test
    void theFirstClaimWinsAndIsPending() {
        long source = SOURCE.incrementAndGet();

        var claimed = emails.claim(EmailKind.PAYMENT_RECEIPT, source, "owner@example.test",
                "We got your payment of $642.00");

        assertThat(claimed).isPresent();
        assertThat(claimed.get().sendKey()).as("a key of its own for Resend").isNotNull();
        assertThat(emails.find(claimed.get().id())).get().satisfies(e -> {
            assertThat(e.kind()).isEqualTo(EmailKind.PAYMENT_RECEIPT);
            assertThat(e.sourceId()).isEqualTo(source);
            assertThat(e.toAddress()).isEqualTo("owner@example.test");
            assertThat(e.subject()).isEqualTo("We got your payment of $642.00");
            assertThat(e.status()).isEqualTo("PENDING");
            assertThat(e.resendId()).isNull();
        });
    }

    @Test
    void aSecondClaimForTheSameKindAndSourceGetsNothing() {
        long source = SOURCE.incrementAndGet();
        emails.claim(EmailKind.REFUND_RECEIPT, source, "a@example.test", "first");

        assertThat(emails.claim(EmailKind.REFUND_RECEIPT, source, "a@example.test", "again")).isEmpty();
        // the same source id under another kind is a different email
        assertThat(emails.claim(EmailKind.PAYMENT_RECEIPT, source, "a@example.test", "other kind")).isPresent();
    }

    @Test
    void aSentEmailKeepsResendsId() {
        long id = emails.claim(EmailKind.POLICY_ISSUED, SOURCE.incrementAndGet(), "a@example.test", "s")
                .orElseThrow().id();

        emails.markSent(id, "re_msg_123");

        assertThat(emails.find(id)).get().satisfies(e -> {
            assertThat(e.status()).isEqualTo("SENT");
            assertThat(e.resendId()).isEqualTo("re_msg_123");
            assertThat(e.sentAt()).isNotNull();
        });
    }

    @Test
    void aFailedEmailKeepsTheError() {
        long id = emails.claim(EmailKind.POLICY_ISSUED, SOURCE.incrementAndGet(), "a@example.test", "s")
                .orElseThrow().id();

        emails.markFailed(id, "Resend said 503");

        assertThat(emails.find(id)).get().satisfies(e -> {
            assertThat(e.status()).isEqualTo("FAILED");
            assertThat(e.error()).isEqualTo("Resend said 503");
        });
    }

    @Test
    void aSkippedEmailIsMarkedSo() {
        long id = emails.claim(EmailKind.POLICY_ISSUED, SOURCE.incrementAndGet(), "a@example.test", "s")
                .orElseThrow().id();

        emails.markSkipped(id);

        assertThat(emails.find(id)).get().satisfies(e -> assertThat(e.status()).isEqualTo("SKIPPED"));
    }

    @Test
    void theSchemaRefusesAnUnknownKindOrStatus() {
        assertThatThrownBy(() -> jdbc.sql("""
                insert into sent_emails (kind, source_id, to_address, subject) values ('SPAM', 1, 'a@b.c', 's')""")
                .update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("""
                insert into sent_emails (kind, source_id, to_address, subject, status)
                values ('POLICY_ISSUED', ?, 'a@b.c', 's', 'LOST')""").param(SOURCE.incrementAndGet())
                .update()).isInstanceOf(DataAccessException.class);
    }
}
