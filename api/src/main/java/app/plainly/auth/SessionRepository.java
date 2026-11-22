package app.plainly.auth;

import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL on {@code sessions}. Only token hashes are stored; expiry uses the database clock. */
@Repository
public class SessionRepository {

    private final JdbcClient jdbc;

    public SessionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Clears sessions that ended over a day ago. */
    public void deleteLongExpired() {
        jdbc.sql("delete from sessions where expires_at < now() - interval '1 day'").update();
    }

    public void insert(byte[] tokenHash, long userId, long lifetimeSeconds) {
        jdbc.sql("insert into sessions (token_hash, user_id, expires_at) values (?, ?, now() + make_interval(secs => ?))")
                .params(tokenHash, userId, lifetimeSeconds).update();
    }

    public Optional<Long> liveUserId(byte[] tokenHash) {
        return jdbc.sql("select user_id from sessions where token_hash = ? and expires_at > now()")
                .param(tokenHash).query(Long.class).optional();
    }

    public void delete(byte[] tokenHash) {
        jdbc.sql("delete from sessions where token_hash = ?").param(tokenHash).update();
    }
}
