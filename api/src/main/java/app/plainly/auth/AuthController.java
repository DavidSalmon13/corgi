package app.plainly.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sign in, sign up, sign out, and who is signed in (real-sign-in spec). */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    /** One answer for every wrong combination, so it never says which part was wrong (AC2). */
    static final Map<String, String> WRONG_CREDENTIALS = Map.of("error", "wrong_credentials",
            "message", "That email and password don't match. Check them and try again.");

    private final Accounts accounts;
    private final Sessions sessions;
    private final boolean secureCookie;

    AuthController(Accounts accounts, Sessions sessions, @Value("${plainly.session.secure-cookie:false}") boolean secureCookie) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.secureCookie = secureCookie;
    }

    record SignInRequest(String email, String password) {}

    record SignUpRequest(String email, String password, String confirmPassword) {}

    @PostMapping("/signin")
    ResponseEntity<?> signIn(@RequestBody SignInRequest body, HttpServletRequest request) {
        return accounts.signIn(body.email(), body.password())
                .<ResponseEntity<?>>map(user -> startSession(user, request))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(WRONG_CREDENTIALS));
    }

    /** Validation problems come back as 422 field errors through ApiErrorHandler. */
    @PostMapping("/signup")
    ResponseEntity<SignedInUser> signUp(@RequestBody SignUpRequest body, HttpServletRequest request) {
        return startSession(accounts.signUp(body.email(), body.password(), body.confirmPassword()), request);
    }

    @GetMapping("/session")
    ResponseEntity<SignedInUser> session(@RequestAttribute(name = AuthFilter.CURRENT_USER, required = false) SignedInUser user) {
        return user == null ? ResponseEntity.status(HttpStatus.UNAUTHORIZED).build() : ResponseEntity.ok(user);
    }

    @DeleteMapping("/session")
    ResponseEntity<Void> signOut(HttpServletRequest request) {
        AuthFilter.token(request).ifPresent(sessions::end);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", 0)).build();
    }

    /** Ends any session the browser already had (no session fixation), then starts a fresh one. */
    private ResponseEntity<SignedInUser> startSession(SignedInUser user, HttpServletRequest request) {
        AuthFilter.token(request).ifPresent(sessions::end);
        String token = sessions.create(user.id());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie(token, Sessions.LIFETIME.toSeconds())).body(user);
    }

    private String cookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(AuthFilter.COOKIE, value)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
    }
}
