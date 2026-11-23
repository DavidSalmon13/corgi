package app.plainly.statement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.AuthTestSupport;
import app.plainly.CorrectionTestData;
import app.plainly.CorrectionTestData.Paid;
import app.plainly.PaymentTestData;
import app.plainly.IntegrationTest;
import app.plainly.MePaths;
import app.plainly.policy.AddCoverageService;
import app.plainly.policy.CorrectCoverageCommand;
import app.plainly.policy.CorrectCoverageService;
import app.plainly.policy.IssuePolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** GET /api/me/policies/{id}/statement: the signed-in owner's own statement, and nobody else's. */
// covers AC1, AC2, AC5, AC7, T4; more-policies AC9, T1
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

        mvc.perform(get(MePaths.statement(paid.owner().policyId())).cookie(paid.owner().session()))
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

    // covers statement-clarity AC3, AC5, T3
    @Test
    void theStatementCarriesItsTermItsDueDateAndEachCardsBalanceAfter() throws Exception {
        Paid paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issuer, adder, "Clarity Bakery LLC");
        corrector.correct(paid.owner().policyId(), new CorrectCoverageCommand(CorrectionTestData.OCT_8,
                CorrectionTestData.REASON), CorrectionTestData.brokerId(jdbc));

        mvc.perform(get(MePaths.statement(paid.owner().policyId())).cookie(paid.owner().session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.inceptionDate").value("2026-09-01"))
                .andExpect(jsonPath("$.header.expiryDate").value("2027-09-01"))
                .andExpect(jsonPath("$.header.statementDate").value("2026-11-22"))
                .andExpect(jsonPath("$.header.dueDate").value("2026-11-22"))
                .andExpect(jsonPath("$.corrections[0].balanceAfterCents").value(1_400));
    }

    // covers statement-clarity AC3, AC5, T3
    @Test
    void aCreditCardReadsNegativeAndNothingIsDue() throws Exception {
        Paid paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issuer, adder,
                "Clarity Credit Bakery LLC");
        corrector.correct(paid.owner().policyId(), new CorrectCoverageCommand(CorrectionTestData.OCT_22,
                CorrectionTestData.REASON), CorrectionTestData.brokerId(jdbc));

        mvc.perform(get(MePaths.statement(paid.owner().policyId())).cookie(paid.owner().session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.header.dueDate").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.corrections[0].balanceAfterCents").value(-1_400));
    }

    @Test
    void eachOwnerOnlySeesTheirOwnPolicy() throws Exception {
        var a = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Statement Owner A LLC");
        var b = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Statement Owner B LLC");

        mvc.perform(get(MePaths.statement(a.policyId())).cookie(a.session()))
                .andExpect(jsonPath("$.header.businessName").value("Statement Owner A LLC"));
        mvc.perform(get(MePaths.statement(b.policyId())).cookie(b.session()))
                .andExpect(jsonPath("$.header.businessName").value("Statement Owner B LLC"));
    }

    // covers more-policies AC9, T1
    @Test
    void anotherOwnersPolicyIsNotFound() throws Exception {
        var a = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Statement Peeker A LLC");
        var b = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Statement Peeker B LLC");

        mvc.perform(get(MePaths.statement(b.policyId())).cookie(a.session())).andExpect(status().isNotFound());
        mvc.perform(get(MePaths.statement(Long.MAX_VALUE)).cookie(a.session())).andExpect(status().isNotFound());
    }

    // covers more-policies AC9, T1: the per-policy route replaces the one-policy route
    @Test
    void theOldRouteIsGone() throws Exception {
        var a = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Old Statement Route LLC");

        mvc.perform(get("/api/me/statement").cookie(a.session())).andExpect(status().isNotFound());
    }

    @Test
    void aBrokerIsForbidden() throws Exception {
        var a = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Statement Broker Peek LLC");

        mvc.perform(get(MePaths.statement(a.policyId())).cookie(AuthTestSupport.signInAsBroker(mvc)))
                .andExpect(status().isForbidden());
    }

    @Test
    void signedOutIsUnauthorized() throws Exception {
        mvc.perform(get(MePaths.statement(1))).andExpect(status().isUnauthorized());
    }
}
