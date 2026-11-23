package app.plainly.auth;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL on {@code app_users}. Emails are passed in already normalized by {@link AccountService}. */
@Repository
public class AccountRepository {

    private static final String SELECT = "select id, role, display_name, subtitle, customer_id, email from app_users";

    private final JdbcClient jdbc;

    public AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** An account with its password hash (null until a policyholder signs up). */
    public record WithHash(SignedInUser user, String passwordHash) {}

    /** A policyholder account waiting to be claimed, as sign-up needs it. */
    public record Claimable(long id, Role role, boolean signedUp) {}

    public Optional<SignedInUser> find(long id) {
        return jdbc.sql(SELECT + " where id = ?").param(id).query(AccountRepository::map).optional();
    }

    public Optional<WithHash> findWithHash(String email) {
        return jdbc.sql("""
                select id, role, display_name, subtitle, customer_id, email, password_hash
                from app_users where email = ?""")
                .param(email)
                .query((rs, n) -> new WithHash(map(rs, n), rs.getString("password_hash")))
                .optional();
    }

    /** Locks the account row for the rest of the caller's transaction. */
    public Optional<Claimable> lockByEmail(String email) {
        return jdbc.sql("select id, role, password_hash is not null as signed_up from app_users where email = ? for update")
                .param(email)
                .query((rs, n) -> new Claimable(rs.getLong("id"), Role.valueOf(rs.getString("role")), rs.getBoolean("signed_up")))
                .optional();
    }

    public Optional<Long> idByEmail(String email) {
        return jdbc.sql("select id from app_users where email = ?").param(email).query(Long.class).optional();
    }

    /** True when an account under this email has set its password (a waiting owner hasn't). */
    public boolean signedUp(String email) {
        return jdbc.sql("select exists (select 1 from app_users where email = ? and password_hash is not null)")
                .param(email).query(Boolean.class).single();
    }

    public boolean emailExists(String email) {
        return jdbc.sql("select exists (select 1 from app_users where email = ?)").param(email).query(Boolean.class).single();
    }

    /** Sets the password of an unclaimed policyholder account by id. Returns the rows changed (0 or 1). */
    public int claim(long id, String passwordHash) {
        return jdbc.sql("""
                update app_users set password_hash = ?, signed_up_at = now()
                where id = ? and role = 'POLICYHOLDER' and password_hash is null""")
                .params(passwordHash, id).update();
    }

    /** Sets the password of any account under this email that has none yet. Returns the rows changed. */
    public int setPasswordIfUnset(String email, String passwordHash) {
        return jdbc.sql("""
                update app_users set password_hash = ?, signed_up_at = now()
                where email = ? and password_hash is null""")
                .params(passwordHash, email).update();
    }

    /** A policyholder account with no password yet. Throws DuplicateKeyException if the email is taken. */
    public void insertWaitingOwner(String displayName, String subtitle, long customerId, String email) {
        jdbc.sql("""
                insert into app_users (role, display_name, subtitle, customer_id, email)
                values ('POLICYHOLDER', ?, ?, ?, ?)""")
                .params(displayName, subtitle, customerId, email).update();
    }

    private static SignedInUser map(ResultSet rs, int row) throws SQLException {
        return new SignedInUser(rs.getLong("id"), Role.valueOf(rs.getString("role")), rs.getString("display_name"),
                rs.getString("subtitle"), rs.getObject("customer_id", Long.class), rs.getString("email"));
    }
}
