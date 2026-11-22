package app.plainly.statement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.AuthTestSupport;
import app.plainly.CorrectionTestData;
import app.plainly.CorrectionTestData.Paid;
import app.plainly.PaymentTestData;
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

/** GET /api/me/statement: the signed-in owner's own statement, and nobody else's. */
// covers AC1, AC2, AC5, AC7, T4
@IntegrationTest
@AutoConfigureMockMvc
class StatementEndpointTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issuer;
    @Autowired AddCoverageService adder;
    @Autowired CorrectCoverageService corrector;

    @Test
    void theOwnerGetsTheirStatementWithCorrectionsAndTraces() throws Exception {
        Paid paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issuer, adder, "Endpoint Bakery LLC");
        corrector.correct(paid.owner().policyId(), new CorrectCoverageCommand(CorrectionTestData.OCT_8,
                CorrectionTestData.REASON), CorrectionTestData.brokerId(jdbc));

        mvc.perform(get("/api/me/statement").cookie(paid.owner().session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.businessName").value("Endpoint Bakery LLC"))
                .andExpect(jsonPath("$.header.balanceCents").value(1_400))
                .andExpect(jsonPath("$.banner.state").value("OWES"))
                .andExpect(jsonPath("$.banner.dueReason").value("CORRECTION"))
                .andExpect(jsonPath("$.rows.length()").value(6))
                .andExpect(jsonPath("$.rows[5].balanceAfterCents").value(1_400))
                .andExpect(jsonPath("$.rows[5].trace.maths.days").value(328))
                .andExpect(jsonPath("$.rows[5].trace.cause.actorName").value("Sam Reyes"))
                .andExpect(jsonPath("$.rows[5].trace.refs[0].kind").value("CHANGE"))
                .andExpect(jsonPath("$.corrections.length()").value(1))
                .andExpect(jsonPath("$.corrections[0].netCents").value(1_400))
                .andExpect(jsonPath("$.corrections[0].oldStart").value("2026-10-15"))
                .andExpect(jsonPath("$.corrections[0].newStart").value("2026-10-08"))
                .andExpect(jsonPath("$.corrections[0].reason").value(CorrectionTestData.REASON))
                .andExpect(jsonPath("$.today").value("2026-11-22"));
    }

    @Test
    void eachOwnerOnlySeesTheirOwnPolicy() throws Exception {
        var a = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Statement Owner A LLC");
        var b = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Statement Owner B LLC");

        mvc.perform(get("/api/me/statement").cookie(a.session()))
                .andExpect(jsonPath("$.header.businessName").value("Statement Owner A LLC"));
        mvc.perform(get("/api/me/statement").cookie(b.session()))
                .andExpect(jsonPath("$.header.businessName").value("Statement Owner B LLC"));
    }

    @Test
    void aBrokerIsForbidden() throws Exception {
        mvc.perform(get("/api/me/statement").cookie(AuthTestSupport.signInAsBroker(mvc)))
                .andExpect(status().isForbidden());
    }

    @Test
    void signedOutIsUnauthorized() throws Exception {
        mvc.perform(get("/api/me/statement")).andExpect(status().isUnauthorized());
    }
}
