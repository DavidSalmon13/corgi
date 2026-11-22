package app.plainly.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.IntegrationTest;
import app.plainly.policy.IssuePolicyCommand;
import app.plainly.policy.IssuePolicyService;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// covers AC1–AC10, T1 — real email-and-password sign-in with hashed, server-side sessions
@IntegrationTest
@AutoConfigureMockMvc
class AuthTest {

    static final String SESSION = "plainly_session";
    static final String SAM = "sam@plainly.test";
    static final String DEMO_PASSWORD = "plainly-demo-2026";
    static final String OWNER_PASSWORD = "bread-and-butter-42";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issue;

    long brokerId;
    long ownerUserId;
    String ownerEmail;

    @BeforeEach
    void anOwnerWhoseBrokerHasIssuedTheirPolicy() {
        brokerId = jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
        ownerEmail = "owner-" + UUID.randomUUID() + "@example.com";
        long policyId = issue.issue(new IssuePolicyCommand("Auth Test Bakery LLC", "a bakery", "Pat Owner",
                ownerEmail, LocalDate.of(2026, 9, 1), false), brokerId).policyId();
        ownerUserId = jdbc.sql("select u.id from app_users u join policies p on p.customer_id = u.customer_id where p.id = ?")
                .param(policyId).query(Long.class).single();
    }

    static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    MvcResult signIn(String email, String password) throws Exception {
        return mvc.perform(json(post("/api/auth/signin"), """
                {"email":"%s","password":"%s"}""".formatted(email, password))).andReturn();
    }

    MvcResult signUp(String email, String password, String confirm) throws Exception {
        return mvc.perform(json(post("/api/auth/signup"), """
                {"email":"%s","password":"%s","confirmPassword":"%s"}""".formatted(email, password, confirm))).andReturn();
    }

    static Cookie sessionOf(MvcResult result) {
        Cookie c = result.getResponse().getCookie(SESSION);
        assertThat(c).as("session cookie").isNotNull();
        return c;
    }

    Cookie ownerSession() throws Exception {
        MvcResult r = signUp(ownerEmail, OWNER_PASSWORD, OWNER_PASSWORD);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return sessionOf(r);
    }

    Cookie brokerSession() throws Exception {
        MvcResult r = signIn(SAM, DEMO_PASSWORD);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return sessionOf(r);
    }

    // ---- AC1: sign in

    @Test
    void theBrokerSignsInWithEmailAndPasswordAndTheSessionReadsItBack() throws Exception {
        mvc.perform(json(post("/api/auth/signin"), "{\"email\":\"" + SAM + "\",\"password\":\"" + DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("BROKER"))
                .andExpect(jsonPath("$.displayName").value("Sam Reyes"))
                .andExpect(jsonPath("$.email").value(SAM));
        Cookie session = brokerSession();
        mvc.perform(get("/api/auth/session").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(brokerId));
    }

    @Test
    void emailsMatchIgnoringCaseAndSurroundingSpaces() throws Exception {
        assertThat(signIn("  SAM@Plainly.Test ", DEMO_PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    // ---- AC2: one message for every wrong combination

    @Test
    void aWrongPasswordAndAnUnknownEmailGetTheSame401() throws Exception {
        MvcResult wrongPassword = signIn(SAM, "not-the-password");
        MvcResult unknownEmail = signIn("nobody@example.com", DEMO_PASSWORD);
        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrongPassword.getResponse().getContentAsString()).isEqualTo(unknownEmail.getResponse().getContentAsString())
                .contains("That email and password don't match. Check them and try again.");
        assertThat(wrongPassword.getResponse().getCookie(SESSION)).isNull();
    }

    @Test
    void anOwnerWhoHasNotSignedUpCannotSignIn() throws Exception {
        assertThat(signIn(ownerEmail, "").getResponse().getStatus()).isEqualTo(401);
        assertThat(signIn(ownerEmail, OWNER_PASSWORD).getResponse().getStatus()).isEqualTo(401);
    }

    // ---- AC4: sign up with the email the broker entered

    @Test
    void theOwnerSignsUpWithTheEmailTheirBrokerEnteredAndIsSignedIn() throws Exception {
        MvcResult r = signUp(ownerEmail.toUpperCase(), OWNER_PASSWORD, OWNER_PASSWORD);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        mvc.perform(get("/api/auth/session").cookie(sessionOf(r)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownerUserId))
                .andExpect(jsonPath("$.role").value("POLICYHOLDER"))
                .andExpect(jsonPath("$.displayName").value("Pat Owner"));
        assertThat(jdbc.sql("select signed_up_at is not null from app_users where id = ?").param(ownerUserId)
                .query(Boolean.class).single()).isTrue();

        assertThat(signIn(ownerEmail, OWNER_PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    // ---- AC5: refusals

    @Test
    void anEmailNoBrokerEnteredCannotSignUp() throws Exception {
        mvc.perform(json(post("/api/auth/signup"), """
                {"email":"stranger@example.com","password":"%s","confirmPassword":"%s"}""".formatted(OWNER_PASSWORD, OWNER_PASSWORD)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.email")
                        .value("We can't find a policy for that email. Check with your broker that they used this address."))
                .andExpect(cookie().doesNotExist(SESSION));
    }

    @Test
    void anEmailThatAlreadyHasAnAccountCannotSignUpAgain() throws Exception {
        ownerSession();
        mvc.perform(json(post("/api/auth/signup"), """
                {"email":"%s","password":"another-password-1","confirmPassword":"another-password-1"}""".formatted(ownerEmail)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.email").value("This email already has an account. Sign in instead."));
        // the first password still works; the second attempt changed nothing
        assertThat(signIn(ownerEmail, OWNER_PASSWORD).getResponse().getStatus()).isEqualTo(200);
        assertThat(signIn(ownerEmail, "another-password-1").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void theBrokersEmailCanNeverBeUsedToSignUp() throws Exception {
        mvc.perform(json(post("/api/auth/signup"), """
                {"email":"%s","password":"taking-over-sam","confirmPassword":"taking-over-sam"}""".formatted(SAM)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.email").value("This email already has an account. Sign in instead."));
        assertThat(signIn(SAM, DEMO_PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void aShortPasswordOrPasswordsThatDoNotMatchAreRefusedAndNothingIsCreated() throws Exception {
        mvc.perform(json(post("/api/auth/signup"), """
                {"email":"%s","password":"short","confirmPassword":"different"}""".formatted(ownerEmail)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.password").value("Use at least 10 characters."))
                .andExpect(jsonPath("$.fields.confirmPassword").value("The two passwords don't match."));
        assertThat(jdbc.sql("select password_hash is null and signed_up_at is null from app_users where id = ?")
                .param(ownerUserId).query(Boolean.class).single()).isTrue();
    }

    // ---- AC7: the session cookie and token

    @Test
    void theSessionCookieIsHttpOnlyLaxAndExpiresInSevenDays() throws Exception {
        MvcResult r = signIn(SAM, DEMO_PASSWORD);
        Cookie c = sessionOf(r);
        assertThat(c.isHttpOnly()).isTrue();
        assertThat(c.getMaxAge()).isEqualTo(7 * 24 * 3600);
        assertThat(r.getResponse().getHeader("Set-Cookie")).contains("SameSite=Lax");
        assertThat(c.getValue()).as("a random token, not a user id").matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void onlyAHashOfTheTokenIsStored() throws Exception {
        String token = brokerSession().getValue();
        int raw = jdbc.sql("select count(*) from sessions where encode(token_hash, 'escape') = ? or encode(token_hash, 'hex') = ?")
                .params(token, token).query(Integer.class).single();
        int hashed = jdbc.sql("select count(*) from sessions where token_hash = sha256(convert_to(?, 'UTF8'))")
                .param(token).query(Integer.class).single();
        assertThat(raw).isZero();
        assertThat(hashed).isEqualTo(1);
    }

    @Test
    void anEditedUnknownExpiredOrOldDemoCookieCountsAsSignedOut() throws Exception {
        Cookie good = brokerSession();
        String v = good.getValue();
        String edited = v.substring(0, v.length() - 1) + (v.endsWith("A") ? "B" : "A");
        mvc.perform(get("/api/broker/policies").cookie(new Cookie(SESSION, edited))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("signed_out"));
        mvc.perform(get("/api/broker/policies").cookie(new Cookie(SESSION, "not-a-token"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/broker/policies").cookie(new Cookie("plainly_demo_user", Long.toString(brokerId))))
                .andExpect(status().isUnauthorized());

        jdbc.sql("update sessions set expires_at = now() - interval '1 minute' where token_hash = sha256(convert_to(?, 'UTF8'))")
                .param(v).update();
        mvc.perform(get("/api/broker/policies").cookie(good)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/session").cookie(good)).andExpect(status().isUnauthorized());
    }

    // ---- AC8: sign out

    @Test
    void signingOutEndsTheSessionOnTheServerAndSigningInAgainGivesANewToken() throws Exception {
        Cookie first = brokerSession();
        mvc.perform(delete("/api/auth/session").cookie(first))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(SESSION, 0));
        mvc.perform(get("/api/broker/policies").cookie(first)).andExpect(status().isUnauthorized());

        Cookie second = brokerSession();
        assertThat(second.getValue()).isNotEqualTo(first.getValue());
        mvc.perform(get("/api/broker/policies").cookie(second)).andExpect(status().isOk());
    }

    // ---- AC9: passwords

    @Test
    void passwordsAreStoredAsBcryptHashesAndNeverReturned() throws Exception {
        MvcResult r = signUp(ownerEmail, OWNER_PASSWORD, OWNER_PASSWORD);
        String hash = jdbc.sql("select password_hash from app_users where id = ?").param(ownerUserId).query(String.class).single();
        assertThat(hash).startsWith("$2").doesNotContain(OWNER_PASSWORD);
        assertThat(r.getResponse().getContentAsString()).doesNotContain("password").doesNotContain(hash);
        assertThat(mvc.perform(get("/api/auth/session").cookie(sessionOf(r))).andReturn().getResponse().getContentAsString())
                .doesNotContain("password");
    }

    // ---- AC10: role rules still hold

    @Test
    void withNoSessionTheGuardedAreasAnswer401() throws Exception {
        mvc.perform(get("/api/auth/session")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/broker/policies")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("signed_out"));
        mvc.perform(get("/api/me/cover")).andExpect(status().isUnauthorized());
    }

    @Test
    void aPolicyholderCannotUseBrokerRoutesAndABrokerCannotUsePolicyholderRoutes() throws Exception {
        Cookie owner = ownerSession();
        Cookie broker = brokerSession();
        mvc.perform(get("/api/broker/policies").cookie(owner))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
        mvc.perform(get("/api/me/cover").cookie(broker)).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/cover").cookie(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.business.legalName").value("Auth Test Bakery LLC"));
    }

    @Test
    void pathParametersAndEncodedCharactersDoNotSlipPastTheGuard() throws Exception {
        Cookie owner = ownerSession();
        Cookie broker = brokerSession();
        for (String path : new String[] {"/api/broker;x/policies", "/api;x/broker/policies", "/api/%62roker/policies",
                "/api/broker/policies;x"}) {
            int signedOut = mvc.perform(get(URI.create(path))).andReturn().getResponse().getStatus();
            int asHolder = mvc.perform(get(URI.create(path)).cookie(owner)).andReturn().getResponse().getStatus();
            assertThat(signedOut).as("signed out, " + path).isEqualTo(401);
            assertThat(asHolder).as("policyholder, " + path).isEqualTo(403);
        }
        mvc.perform(get(URI.create("/api/me;x/cover")).cookie(broker)).andExpect(status().isForbidden());
        mvc.perform(get(URI.create("/api/%6De/cover")).cookie(broker)).andExpect(status().isForbidden());
    }

    @Test
    void unknownApiRoutesNeedASessionButHealthAndSignInDoNot() throws Exception {
        mvc.perform(get("/api/no-such-area")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        assertThat(signIn(SAM, DEMO_PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void theOldDemoEndpointsAreGone() throws Exception {
        Cookie broker = brokerSession();
        int users = mvc.perform(get("/api/demo/users").cookie(broker)).andReturn().getResponse().getStatus();
        int session = mvc.perform(json(post("/api/demo/session"), "{\"userId\":" + brokerId + "}"))
                .andReturn().getResponse().getStatus();
        assertThat(users).isEqualTo(404);
        assertThat(session).isEqualTo(401);
    }

    // covers real-sign-in AC7, T17 — security review: personal answers are never cached
    @Test
    void apiAnswersAreNeverCached() throws Exception {
        Cookie broker = brokerSession();
        assertThat(mvc.perform(get("/api/broker/policies").cookie(broker)).andReturn().getResponse().getHeader("Cache-Control"))
                .isEqualTo("no-store");
        assertThat(mvc.perform(get("/api/auth/session").cookie(broker)).andReturn().getResponse().getHeader("Cache-Control"))
                .isEqualTo("no-store");
        assertThat(mvc.perform(get("/api/me/cover")).andReturn().getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    }

    // covers real-sign-in AC7, T17 — security review: the cookie is Secure on any HTTPS request
    @Test
    void theSessionCookieIsSecureOverHttps() throws Exception {
        MvcResult r = mvc.perform(json(post("/api/auth/signin").secure(true),
                "{\"email\":\"" + SAM + "\",\"password\":\"" + DEMO_PASSWORD + "\"}")).andReturn();
        assertThat(sessionOf(r).getSecure()).isTrue();
        assertThat(sessionOf(signIn(SAM, DEMO_PASSWORD)).getSecure()).as("plain http in dev").isFalse();
    }

    // covers real-sign-in AC1, AC4, T17 — security review: an email with odd whitespace is stored the way sign-up looks it up
    @Test
    void anOwnerEmailWithATabOrNonBreakingSpaceCanStillSignUp() throws Exception {
        String email = "tab-" + UUID.randomUUID() + "@example.com";
        issue.issue(new IssuePolicyCommand("Tab Shop LLC", "a shop", "Tia Owner", "\t\u00a0" + email.toUpperCase() + " ",
                LocalDate.of(2026, 9, 1), false), brokerId);
        assertThat(signUp(email, OWNER_PASSWORD, OWNER_PASSWORD).getResponse().getStatus()).isEqualTo(200);
    }
}
