package app.plainly.auth;

import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Password hashing with BCrypt (cost 10). Only hashes are stored, and nothing returns or logs a
 * password (real-sign-in AC9).
 */
@Component
public class Passwords {

    /** BCrypt reads at most 72 bytes; sign-up refuses longer passwords rather than silently cutting them. */
    public static final int MAX_BYTES = 72;

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(10);
    /** Checked against when the email is unknown, so a wrong email takes as long as a wrong password. */
    private static final String DUMMY_HASH = BCRYPT.encode("no-account-has-this-password");

    public String hash(String raw) {
        if (raw == null || tooLong(raw)) {
            throw new IllegalArgumentException("password must be 1 to " + MAX_BYTES + " bytes");
        }
        return BCRYPT.encode(raw);
    }

    /** True only when {@code raw} matches {@code hash}. A missing hash still costs one BCrypt check. */
    public boolean matches(String raw, String hash) {
        if (raw == null || tooLong(raw)) {
            BCRYPT.matches("", DUMMY_HASH);
            return false;
        }
        if (hash == null) {
            BCRYPT.matches(raw, DUMMY_HASH);
            return false;
        }
        return BCRYPT.matches(raw, hash);
    }

    public static boolean tooLong(String raw) {
        return raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }
}
