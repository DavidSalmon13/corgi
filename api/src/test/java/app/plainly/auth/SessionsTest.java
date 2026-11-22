package app.plainly.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.plainly.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

// covers real-sign-in AC7, AC8, T5
@IntegrationTest
class SessionsTest {

    @Autowired Sessions sessions;
    @Autowired JdbcClient jdbc;

    long brokerId;

    @BeforeEach
    void broker() {
        brokerId = jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
    }

    @Test
    void aNewTokenIsRandomAndLeadsBackToItsUser() {
        String a = sessions.create(brokerId);
        String b = sessions.create(brokerId);
        assertThat(a).hasSize(43).matches("[A-Za-z0-9_-]+").isNotEqualTo(b);
        assertThat(sessions.userFor(a)).contains(brokerId);
    }

    @Test
    void theTableKeepsOnlyTheHashAndASevenDayExpiry() {
        String token = sessions.create(brokerId);
        var row = jdbc.sql("""
                select token_hash = sha256(convert_to(?, 'UTF8')) as hashed,
                       round(extract(epoch from expires_at - created_at) / 3600) as hours
                from sessions where token_hash = sha256(convert_to(?, 'UTF8'))""")
                .params(token, token).query((rs, n) -> new Object[] {rs.getBoolean("hashed"), rs.getInt("hours")}).single();
        assertThat(row).containsExactly(true, 7 * 24);
    }

    @Test
    void malformedUnknownExpiredAndEndedTokensLeadNowhere() {
        assertThat(sessions.userFor(null)).isEmpty();
        assertThat(sessions.userFor("1")).isEmpty();
        assertThat(sessions.userFor("A".repeat(43))).isEmpty();

        String expired = sessions.create(brokerId);
        jdbc.sql("update sessions set expires_at = now() - interval '1 second' where token_hash = ?")
                .param(Sessions.hash(expired)).update();
        assertThat(sessions.userFor(expired)).isEmpty();

        String ended = sessions.create(brokerId);
        sessions.end(ended);
        assertThat(sessions.userFor(ended)).isEmpty();
    }
}
