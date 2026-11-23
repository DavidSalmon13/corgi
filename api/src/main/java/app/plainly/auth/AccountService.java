package app.plainly.auth;

import app.plainly.common.ValidationException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accounts for both roles (real-sign-in spec). Brokers are seeded. A policyholder's account is
 * created, with their email and no password, when their broker issues the policy, and the owner
 * claims it by signing up with that email.
 */
@Service
public class AccountService {

    public static final int MIN_PASSWORD = 10;

    static final String NO_POLICY = "We can't find a policy for that email. Check with your broker that they used this address.";
    static final String ALREADY_SIGNED_UP = "This email already has an account. Sign in instead.";

    private static final Pattern EDGE_SPACE = Pattern.compile("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$");

    private final AccountRepository accounts;
    private final Passwords passwords;

    public AccountService(AccountRepository accounts, Passwords passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
    }

    /** Removes spaces of any kind (tabs, non-breaking spaces) from both ends, keeping the case. */
    public static String trim(String email) {
        return email == null ? "" : EDGE_SPACE.matcher(email).replaceAll("");
    }

    /** Emails are compared trimmed and lower-case, the way they're stored. */
    public static String normalize(String email) {
        return trim(email).toLowerCase(Locale.ROOT);
    }

    public Optional<SignedInUser> find(long id) {
        return accounts.find(id);
    }

    /** The account when the email and password match. Unknown emails and wrong passwords look the same. */
    public Optional<SignedInUser> signIn(String email, String password) {
        Optional<AccountRepository.WithHash> row = accounts.findWithHash(normalize(email));
        String hash = row.map(AccountRepository.WithHash::passwordHash).orElse(null);
        return passwords.matches(password, hash) ? row.map(AccountRepository.WithHash::user) : Optional.empty();
    }

    /** True when any account (a broker's or a policyholder's) already uses this email. */
    public boolean emailInUse(String email) {
        return accounts.emailExists(normalize(email));
    }

    /** True when this email has an account someone can sign in to; false for none, or one still waiting. */
    public boolean hasSignedUp(String email) {
        return accounts.signedUp(normalize(email));
    }

    /**
     * The owner's account, waiting with their email and no password until they sign up (real-sign-in AC4).
     * Joins the caller's transaction; throws DuplicateKeyException if another account took the email.
     */
    public void createWaitingOwner(String ownerName, String legalName, long customerId, String email) {
        accounts.insertWaitingOwner(ownerName, "Owner, " + legalName, customerId, normalize(email));
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

        Optional<AccountRepository.Claimable> account = normalized.isEmpty() ? Optional.empty()
                : accounts.lockByEmail(normalized);
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
        if (accounts.claim(id, passwords.hash(password)) != 1) {
            throw new ValidationException(Map.of("email", ALREADY_SIGNED_UP));
        }
        return find(id).orElseThrow();
    }
}
