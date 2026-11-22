package app.plainly.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Who is signed in, and what they may reach (real-sign-in spec). The {@value #COOKIE} cookie holds a
 * session token, looked up through {@link Sessions}. /api/broker/** needs a broker, /api/me/** needs a
 * policyholder, and every other /api route except /api/health and /api/auth/** needs someone signed
 * in (deny by default).
 *
 * <p>The guard reads the decoded path with ";params" stripped, the same path Spring MVC routes on.
 * The raw request URI would let "/api/broker;x/policies" or "/api/%62roker/policies" reach a
 * broker controller while slipping past a prefix check.
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    public static final String COOKIE = "plainly_session";
    /** Request attribute holding the {@link SignedInUser}; read with {@code @RequestAttribute}. */
    public static final String CURRENT_USER = "currentUser";

    /** Decodes %xx, strips ";params" and collapses "//", matching how Spring MVC sees the path. */
    private static final UrlPathHelper PATHS = UrlPathHelper.defaultInstance;

    private final Sessions sessions;
    private final Accounts accounts;

    public AuthFilter(Sessions sessions, Accounts accounts) {
        this.sessions = sessions;
        this.accounts = accounts;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = PATHS.getPathWithinApplication(request);
        Optional<SignedInUser> user = token(request).flatMap(sessions::userFor).flatMap(accounts::find);
        user.ifPresent(u -> request.setAttribute(CURRENT_USER, u));
        if (under(path, "/api")) {
            // Answers carry personal and money details: never let a browser or proxy keep a copy.
            response.setHeader("Cache-Control", "no-store");
        }
        if (under(path, "/api") && !under(path, "/api/health") && !under(path, "/api/auth")) {
            if (user.isEmpty()) {
                reject(response, 401, "signed_out", "Sign in to see this page.");
                return;
            }
            Role needed = under(path, "/api/broker") ? Role.BROKER : under(path, "/api/me") ? Role.POLICYHOLDER : null;
            if (needed != null && user.get().role() != needed) {
                reject(response, 403, "forbidden", "This page is for the other role.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /** The session token from the request's cookie, if it sent one. */
    static Optional<String> token(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies).filter(c -> COOKIE.equals(c.getName())).map(Cookie::getValue).findFirst();
    }

    private static boolean under(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static void reject(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}
