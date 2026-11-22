package app.plainly.statement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.AuthTestSupport;
import app.plainly.CorrectionTestData;
import app.plainly.CorrectionTestData.Paid;
import app.plainly.IntegrationTest;
import app.plainly.policy.AddCoverageService;
import app.plainly.policy.CorrectCoverageCommand;
import app.plainly.policy.CorrectCoverageService;
import app.plainly.policy.IssuePolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** GET /api/broker/policies/{id}/history: B5 for the broker only; any policy, 404 for one that doesn't exist. */
// covers AC1, AC2, AC6, T4
@IntegrationTest
@AutoConfigureMockMvc
class BrokerHistoryEndpointTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issuer;
    @Autowired AddCoverageService adder;
    @Autowired CorrectCoverageService corrector;

    @Test
    void theBrokerGetsEveryActionWithTheCorrectionAsOneCard() throws Exception {
        Paid paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issuer, adder, "History Bakery LLC");
        long id = paid.owner().policyId();
        CorrectCoverageService.Corrected saved = corrector.correct(id, new CorrectCoverageCommand(
                CorrectionTestData.OCT_8, CorrectionTestData.REASON), CorrectionTestData.brokerId(jdbc));

        mvc.perform(get("/api/broker/policies/{id}/history", id).cookie(AuthTestSupport.signInAsBroker(mvc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("History Bakery LLC"))
                .andExpect(jsonPath("$.balanceCents").value(1_400))
                .andExpect(jsonPath("$.actions.length()").value(5))
                .andExpect(jsonPath("$.actions[0].kind").value("CHANGE"))
                .andExpect(jsonPath("$.actions[0].changeType").value("ISSUE"))
                .andExpect(jsonPath("$.actions[1].kind").value("PAYMENT"))
                .andExpect(jsonPath("$.actions[1].status").value("PAID"))
                .andExpect(jsonPath("$.actions[4].id").value(saved.transactionDisplayId()))
                .andExpect(jsonPath("$.actions[4].changeType").value("CORRECT"))
                .andExpect(jsonPath("$.actions[4].amountCents").value(1_400))
                .andExpect(jsonPath("$.actions[4].actorName").value("Sam Reyes"))
                .andExpect(jsonPath("$.actions[4].reason").value(CorrectionTestData.REASON))
                .andExpect(jsonPath("$.actions[4].correction.dayDelta").value(7))
                .andExpect(jsonPath("$.actions[4].rows.length()").value(2))
                .andExpect(jsonPath("$.actions[4].rows[1].trace.maths.days").value(328))
                .andExpect(jsonPath("$.today").value("2026-11-22"));
    }

    @Test
    void anUnknownPolicyIsNotFound() throws Exception {
        mvc.perform(get("/api/broker/policies/{id}/history", Long.MAX_VALUE)
                        .cookie(AuthTestSupport.signInAsBroker(mvc)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aPolicyholderIsForbidden() throws Exception {
        Paid paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issuer, adder, "Not Yours Bakery LLC");

        mvc.perform(get("/api/broker/policies/{id}/history", paid.owner().policyId()).cookie(paid.owner().session()))
                .andExpect(status().isForbidden());
    }

    @Test
    void signedOutIsUnauthorized() throws Exception {
        mvc.perform(get("/api/broker/policies/{id}/history", 1)).andExpect(status().isUnauthorized());
    }
}
