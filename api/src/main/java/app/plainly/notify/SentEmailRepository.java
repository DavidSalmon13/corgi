package app.plainly.notify;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL on {@code sent_emails}: one row per email, so each is sent once, and how its send ended. */
@Repository
public class SentEmailRepository {

    public record SentEmail(long id, EmailKind kind, long sourceId, String toAddress, String subject, String status,
            String resendId, String error, OffsetDateTime sentAt) {}

    private final JdbcClient jdbc;

    public SentEmailRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Records that this email is to be sent, PENDING. Empty if it was already claimed: a second claim at the same
     * moment waits on the first one's row and then gets nothing, so claiming in the caller's transaction makes a
     * replay send nothing.
     */
    public Optional<Claimed> claim(EmailKind kind, long sourceId, String toAddress, String subject) {
        return jdbc.sql("""
                insert into sent_emails (kind, source_id, to_address, subject) values (?, ?, ?, ?)
                on conflict on constraint sent_emails_once do nothing returning id, send_key""")
                .params(kind.name(), sourceId, toAddress, subject)
                .query((rs, n) -> new Claimed(rs.getLong("id"), rs.getObject("send_key", UUID.class))).optional();
    }

    /** A claimed email: its row, and the random key that makes it one email to Resend whichever database sent it. */
    public record Claimed(long id, UUID sendKey) {}

    public void markSent(long id, String resendId) {
        jdbc.sql("update sent_emails set status = 'SENT', resend_id = ?, error = null, sent_at = now() where id = ?")
                .params(resendId, id).update();
    }

    public void markFailed(long id, String error) {
        jdbc.sql("update sent_emails set status = 'FAILED', error = ? where id = ?").params(error, id).update();
    }

    public void markSkipped(long id) {
        jdbc.sql("update sent_emails set status = 'SKIPPED' where id = ?").param(id).update();
    }

    public Optional<SentEmail> find(long id) {
        return jdbc.sql("""
                select id, kind, source_id, to_address, subject, status, resend_id, error, sent_at
                from sent_emails where id = ?""").param(id)
                .query((rs, n) -> new SentEmail(rs.getLong("id"), EmailKind.valueOf(rs.getString("kind")),
                        rs.getLong("source_id"), rs.getString("to_address"), rs.getString("subject"),
                        rs.getString("status"), rs.getString("resend_id"), rs.getString("error"),
                        rs.getObject("sent_at", OffsetDateTime.class)))
                .optional();
    }
}
