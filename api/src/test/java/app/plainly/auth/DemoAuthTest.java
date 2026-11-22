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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

// covers AC1, T10 — demo sign-in is simulated, not real security (DECISIONS.md)
@IntegrationTest
@AutoConfigureMockMvc
class DemoAuthTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issue;

    long brokerId;
    long policyholderId;

    @BeforeEach
    void users() {
        brokerId = jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
        long policyId = issue.issue(new IssuePolicyCommand("Auth Test Bakery LLC", "a bakery", "Pat Owner",
                "pat@example.com", LocalDate.of(2026, 9, 1), false), brokerId).policyId();
        policyholderId = jdbc.sql("""
                select u.id from app_users u join policies p on p.customer_id = u.customer_id where p.id = ?""")
                .param(policyId).query(Long.class).single();
    }

    static Cookie as(long userId) {
        return new Cookie(DemoAuthFilter.COOKIE, Long.toString(userId));
    }

    @Test
    void theRoleSwitcherListsTheBrokerFirstThenEachOwner() throws Exception {
        mvc.perform(get("/api/demo/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("BROKER"))
                .andExpect(jsonPath("$[0].displayName").value("Sam Reyes"))
                .andExpect(jsonPath("$[?(@.id == %d)].subtitle", policyholderId).value("Owner, Auth Test Bakery LLC"))
                .andExpect(jsonPath("$[?(@.id == %d)].role", policyholderId).value("POLICYHOLDER"));
    }

    @Test
    void signingInSetsAnHttpOnlyCookieAndTheSessionReadsItBack() throws Exception {
        mvc.perform(post("/api/demo/session").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":" + brokerId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("BROKER"))
                .andExpect(cookie().value(DemoAuthFilter.COOKIE, Long.toString(brokerId)))
                .andExpect(cookie().httpOnly(DemoAuthFilter.COOKIE, true));
        mvc.perform(get("/api/demo/session").cookie(as(brokerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Sam Reyes"));
    }

    @Test
    void signingInAsSomeoneWhoDoesNotExistFails() throws Exception {
        mvc.perform(post("/api/demo/session").contentType(MediaType.APPLICATION_JSON).content("{\"userId\":999999}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void signingOutClearsTheCookie() throws Exception {
        mvc.perform(delete("/api/demo/session").cookie(as(brokerId)))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge(DemoAuthFilter.COOKIE, 0));
    }

    @Test
    void withNoSessionTheGuardedAreasAnswer401() throws Exception {
        mvc.perform(get("/api/demo/session")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/broker/policies")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("signed_out"));
        mvc.perform(get("/api/me/cover")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/broker/policies").cookie(new Cookie(DemoAuthFilter.COOKIE, "999999")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/broker/policies").cookie(new Cookie(DemoAuthFilter.COOKIE, "not-a-number")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aPolicyholderCannotUseBrokerRoutesAndABrokerCannotUsePolicyholderRoutes() throws Exception {
        mvc.perform(get("/api/broker/policies").cookie(as(policyholderId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("forbidden"));
        mvc.perform(get("/api/me/cover").cookie(as(brokerId))).andExpect(status().isForbidden());
    }

    @Test
    void theRightRoleGetsPastTheGuard() throws Exception {
        int broker = mvc.perform(get("/api/broker/no-such-route").cookie(as(brokerId))).andReturn().getResponse().getStatus();
        int holder = mvc.perform(get("/api/me/no-such-route").cookie(as(policyholderId))).andReturn().getResponse().getStatus();
        assertThat(broker).isNotIn(401, 403);
        assertThat(holder).isNotIn(401, 403);
    }

    @Test
    void pathParametersAndEncodedCharactersDoNotSlipPastTheGuard() throws Exception {
        for (String path : new String[] {"/api/broker;x/policies", "/api;x/broker/policies", "/api/%62roker/policies",
                "/api/broker/policies;x"}) {
            int signedOut = mvc.perform(get(URI.create(path))).andReturn().getResponse().getStatus();
            int asHolder = mvc.perform(get(URI.create(path)).cookie(as(policyholderId))).andReturn().getResponse().getStatus();
            assertThat(signedOut).as("signed out, " + path).isEqualTo(401);
            assertThat(asHolder).as("policyholder, " + path).isEqualTo(403);
        }
        mvc.perform(post(URI.create("/api/broker;x/policies")).cookie(as(policyholderId))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(get(URI.create("/api/me;x/cover")).cookie(as(brokerId))).andExpect(status().isForbidden());
        mvc.perform(get(URI.create("/api/%6De/cover")).cookie(as(brokerId))).andExpect(status().isForbidden());
    }

    @Test
    void unknownApiRoutesNeedASessionButHealthAndSignInDoNot() throws Exception {
        mvc.perform(get("/api/no-such-area")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/demo/users")).andExpect(status().isOk());
    }
}
