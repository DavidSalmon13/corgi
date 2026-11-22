package app.plainly.me;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.AuthTestSupport;
import app.plainly.IntegrationTest;
import app.plainly.TestData;
import app.plainly.policy.AddCoverageCommand;
import app.plainly.policy.AddCoverageService;
import app.plainly.policy.CoverKind;
import app.plainly.policy.IssuePolicyCommand;
import app.plainly.policy.IssuePolicyService;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

// covers AC10, T11
@IntegrationTest
@AutoConfigureMockMvc
class MyCoverTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issue;
    @Autowired AddCoverageService addCover;

    /** Issues a policy, then signs its owner up with the email the broker entered, as a real owner would. */
    Cookie ownerOf(String business, boolean extra) throws Exception {
        long broker = jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
        String email = TestData.uniqueEmail("maya");
        issue.issue(new IssuePolicyCommand(business, "a neighborhood bakery", "Maya Okafor", email, LocalDate.of(2026, 9, 1), extra),
                broker);
        return AuthTestSupport.signUp(mvc, email);
    }

    @Test
    void theOwnerSeesTheirCoverWhatTheyOweAndWhy() throws Exception {
        mvc.perform(get("/api/me/cover").cookie(ownerOf("My Cover Bakery LLC", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.business.legalName").value("My Cover Bakery LLC"))
                .andExpect(jsonPath("$.business.description").value("a neighborhood bakery"))
                .andExpect(jsonPath("$.business.ownerName").value("Maya Okafor"))
                .andExpect(jsonPath("$.policy.status").value("ACTIVE"))
                .andExpect(jsonPath("$.policy.start").value("2026-09-01"))
                .andExpect(jsonPath("$.policy.end").value("2027-09-01"))
                .andExpect(jsonPath("$.covers.length()").value(2))
                .andExpect(jsonPath("$.covers[1].kind").value("PRODUCT_LIABILITY"))
                .andExpect(jsonPath("$.amountDueCents").value(193000))
                .andExpect(jsonPath("$.dueReason").value("FIRST_YEAR"))
                .andExpect(jsonPath("$.broker.name").value("Sam Reyes"))
                .andExpect(jsonPath("$.today").value("2026-11-22"));
    }

    @Test
    void eachOwnerOnlySeesTheirOwnBusiness() throws Exception {
        Cookie a = ownerOf("Owner A Florist LLC", false);
        Cookie b = ownerOf("Owner B Tacos LLC", false);
        mvc.perform(get("/api/me/cover").cookie(a)).andExpect(jsonPath("$.business.legalName").value("Owner A Florist LLC"));
        mvc.perform(get("/api/me/cover").cookie(b)).andExpect(jsonPath("$.business.legalName").value("Owner B Tacos LLC"));
    }

    // covers add-coverage AC9, T2 — after extra cover is added, P1 says that is why more is due
    @Test
    void addedExtraCoverIsWhyMoreIsDueAndFromWhen() throws Exception {
        long broker = jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
        String email = TestData.uniqueEmail("maya");
        long policyId = issue.issue(new IssuePolicyCommand("Extra Cover Bakery LLC", "a neighborhood bakery", "Maya Okafor",
                email, LocalDate.of(2026, 9, 1), false), broker).policyId();
        addCover.add(policyId, new AddCoverageCommand(CoverKind.PRODUCT_LIABILITY, LocalDate.of(2026, 10, 15)), broker);

        mvc.perform(get("/api/me/cover").cookie(AuthTestSupport.signUp(mvc, email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountDueCents").value(120000 + 64200))
                .andExpect(jsonPath("$.dueReason").value("EXTRA_COVER"))
                .andExpect(jsonPath("$.latestChangeFrom").value("2026-10-15"))
                .andExpect(jsonPath("$.covers.length()").value(2))
                .andExpect(jsonPath("$.covers[1].kind").value("PRODUCT_LIABILITY"))
                .andExpect(jsonPath("$.covers[1].from").value("2026-10-15"));
    }
}
