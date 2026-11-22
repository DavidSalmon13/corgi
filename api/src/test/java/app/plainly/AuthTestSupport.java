package app.plainly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Signs a test in the way the web app does, and hands back the session cookie to send with later requests. */
public final class AuthTestSupport {

    public static final String SESSION_COOKIE = "plainly_session";
    /** The seeded broker (V5 migration) and the demo password every demo account uses. */
    public static final String BROKER_EMAIL = "sam@plainly.test";
    public static final String DEMO_PASSWORD = "plainly-demo-2026";

    private AuthTestSupport() {}

    public static Cookie signIn(MockMvc mvc, String email, String password) throws Exception {
        return session(mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))).andReturn());
    }

    public static Cookie signInAsBroker(MockMvc mvc) throws Exception {
        return signIn(mvc, BROKER_EMAIL, DEMO_PASSWORD);
    }

    /** Signs up the owner of an already-issued policy (their email is the one the broker entered). */
    public static Cookie signUp(MockMvc mvc, String ownerEmail) throws Exception {
        String password = "test-password-123";
        return session(mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"confirmPassword\":\"%s\"}".formatted(ownerEmail, password, password)))
                .andReturn());
    }

    private static Cookie session(MvcResult result) {
        assertThat(result.getResponse().getStatus()).as("auth answer: " + contentOf(result)).isEqualTo(200);
        Cookie cookie = result.getResponse().getCookie(SESSION_COOKIE);
        assertThat(cookie).as("session cookie").isNotNull();
        return cookie;
    }

    private static String contentOf(MvcResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (Exception e) {
            return "?";
        }
    }
}
