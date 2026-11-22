package app.plainly.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Server-side sessions (real-sign-in AC7, AC8). The cookie carries a random token; the database keeps
 * only its SHA-256 hash, so neither guessing nor reading the table gets anyone signed in. Expiry is
 * checked against the database clock.
 */
@Service
public class SessionService {

    public static final Duration LIFETIME = Duration.ofDays(7);

    private static final Base64.Encoder TOKEN_ENCODING = Base64.getUrlEncoder().withoutPadding();
    /** 32 random bytes, base64url without padding. */
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final SessionRepository sessions;
    private final SecureRandom random = new SecureRandom();

    public SessionService(SessionRepository sessions) {
        this.sessions = sessions;
    }

    /** Starts a session and returns the token for the cookie. Also clears sessions that ended over a day ago. */
    public String create(long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = TOKEN_ENCODING.encodeToString(bytes);
        sessions.deleteLongExpired();
        sessions.insert(hash(token), userId, LIFETIME.toSeconds());
        return token;
    }

    /** The user a live session belongs to; empty for a malformed, unknown, ended or expired token. */
    public Optional<Long> userFor(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) {
            return Optional.empty();
        }
        return sessions.liveUserId(hash(token));
    }

    /** Ends the session, so the token stops working even if someone kept a copy. */
    public void end(String token) {
        if (token != null && TOKEN.matcher(token).matches()) {
            sessions.delete(hash(token));
        }
    }

    static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
