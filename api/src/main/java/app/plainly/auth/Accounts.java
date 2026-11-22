package app.plainly.auth;

import app.plainly.common.ValidationException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accounts for both roles (real-sign-in spec). Brokers are seeded. A policyholder's account is
 * created, with their email and no password, when their broker issues the policy, and the owner
 * claims it by signing up with that email.
 */
@Component
public class Accounts {

    public static final int MIN_PASSWORD = 10;

    static final String NO_POLICY = "We can't find a policy for that email. Check with your broker that they used this address.";
    static final String ALREADY_SIGNED_UP = "This email already has an account. Sign in instead.";

    private static final String SELECT = "select id, role, display_name, subtitle, customer_id, email from app_users";

    private final JdbcClient jdbc;
    private final Passwords passwords;

    public Accounts(JdbcClient jdbc, Passwords passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    private static final Pattern EDGE_SPACE = Pattern.compile("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$");

    /** Removes spaces of any kind (tabs, non-breaking spaces) from both ends, keeping the case. */
    public static String trim(String email) {
        return email == null ? "" : EDGE_SPACE.matcher(email).replaceAll("");
    }

    /** Emails are compared trimmed and lower-case, the way they're stored. */
    public static String normalize(String email) {
        return trim(email).toLowerCase(Locale.ROOT);
    }

    public Optional<SignedInUser> find(long id) {
        return jdbc.sql(SELECT + " where id = ?").param(id).query(Accounts::map).optional();
    }

    /** The account when the email and password match. Unknown emails and wrong passwords look the same. */
    public Optional<SignedInUser> signIn(String email, String password) {
        record Row(SignedInUser user, String hash) {}
        Optional<Row> row = jdbc.sql("""
                select id, role, display_name, subtitle, customer_id, email, password_hash
                from app_users where email = ?""")
                .param(normalize(email))
                .query((rs, n) -> new Row(map(rs, n), rs.getString("password_hash"))).optional();
        String hash = row.map(Row::hash).orElse(null);
        return passwords.matches(password, hash) ? row.map(Row::user) : Optional.empty();
    }

    /**
     * Claims the owner account waiting under this email: sets the password and the sign-up time.
     * Refuses, changing nothing, when no broker entered the email, when it already has an account
     * (brokers included), or when the passwords are too short or don't match.
     */
    @Transactional
    public SignedInUser signUp(String email, String password, String confirmPassword) {
        Map<String, String> errors = new LinkedHashMap<>();
        String normalized = normalize(email);
        if (normalized.isEmpty()) {
            errors.put("email", "Enter the email your broker has on file for your business.");
        }
        if (password == null || password.length() < MIN_PASSWORD) {
            errors.put("password", "Use at least " + MIN_PASSWORD + " characters.");
        } else if (Passwords.tooLong(password)) {
            errors.put("password", "Keep it under " + Passwords.MAX_BYTES + " characters.");
        }
        if (password != null && !password.equals(confirmPassword)) {
            errors.put("confirmPassword", "The two passwords don't match.");
        }

        record Waiting(long id, Role role, boolean signedUp) {}
        Optional<Waiting> account = normalized.isEmpty() ? Optional.empty()
                : jdbc.sql("select id, role, password_hash is not null as signed_up from app_users where email = ? for update")
                        .param(normalized)
                        .query((rs, n) -> new Waiting(rs.getLong("id"), Role.valueOf(rs.getString("role")), rs.getBoolean("signed_up")))
                        .optional();
        if (!normalized.isEmpty()) {
            if (account.isEmpty()) {
                errors.put("email", NO_POLICY);
            } else if (account.get().signedUp() || account.get().role() != Role.POLICYHOLDER) {
                errors.put("email", ALREADY_SIGNED_UP);
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        long id = account.orElseThrow().id();
        int claimed = jdbc.sql("""
                update app_users set password_hash = ?, signed_up_at = now()
                where id = ? and role = 'POLICYHOLDER' and password_hash is null""")
                .params(passwords.hash(password), id).update();
        if (claimed != 1) {
            throw new ValidationException(Map.of("email", ALREADY_SIGNED_UP));
        }
        return find(id).orElseThrow();
    }

    private static SignedInUser map(ResultSet rs, int row) throws SQLException {
        return new SignedInUser(rs.getLong("id"), Role.valueOf(rs.getString("role")), rs.getString("display_name"),
                rs.getString("subtitle"), rs.getObject("customer_id", Long.class), rs.getString("email"));
    }
}
