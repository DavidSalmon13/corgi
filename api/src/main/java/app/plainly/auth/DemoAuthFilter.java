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
 * Simulated sign-in (DECISIONS.md, "Demo sign-in is simulated"): a cookie holds the id of the
 * demo user picked on the role switcher. It is NOT real security; anyone can be anyone.
 * It still keeps the two roles apart, so each side only sees its own screens and data:
 * /api/broker/** needs a broker, /api/me/** needs a policyholder, and every other /api route
 * except /api/health and /api/demo/** needs someone signed in (deny by default).
 *
 * <p>The guard reads the decoded path with ";params" stripped, the same path Spring MVC routes on.
 * The raw request URI would let "/api/broker;x/policies" or "/api/%62roker/policies" reach a
 * broker controller while slipping past a prefix check.
 */
@Component
public class DemoAuthFilter extends OncePerRequestFilter {

    public static final String COOKIE = "plainly_demo_user";
    /** Request attribute holding the signed-in {@link DemoUser}; read with {@code @RequestAttribute}. */
    public static final String CURRENT_USER = "currentUser";

    private final DemoUsers users;

    public DemoAuthFilter(DemoUsers users) {
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = PATHS.getPathWithinApplication(request);
        Optional<DemoUser> user = signedIn(request);
        user.ifPresent(u -> request.setAttribute(CURRENT_USER, u));
        if (under(path, "/api") && !under(path, "/api/health") && !under(path, "/api/demo")) {
            if (user.isEmpty()) {
                reject(response, 401, "signed_out", "Choose who to be on the sign-in page.");
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

    /** Decodes %xx, strips ";params" and collapses "//", matching how Spring MVC sees the path. */
    private static final UrlPathHelper PATHS = UrlPathHelper.defaultInstance;

    private static boolean under(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    Optional<DemoUser> signedIn(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> COOKIE.equals(c.getName()))
                .findFirst()
                .flatMap(c -> parse(c.getValue()))
                .flatMap(users::find);
    }

    private static Optional<Long> parse(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static void reject(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}
