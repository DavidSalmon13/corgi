package app.plainly.timeline;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.AuthTestSupport;
import app.plainly.CorrectionTestData;
import app.plainly.CorrectionTestData.Paid;
import app.plainly.IntegrationTest;
import app.plainly.MePaths;
import app.plainly.PaymentTestData;
import app.plainly.policy.AddCoverageService;
import app.plainly.policy.CorrectCoverageCommand;
import app.plainly.policy.CorrectCoverageService;
import app.plainly.policy.IssuePolicyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** GET /api/me/policies/{id}/timeline: the signed-in owner's own cover timeline, and nobody else's. */
// covers AC1, AC4, AC7, T3; more-policies AC9, T1
@IntegrationTest
@AutoConfigureMockMvc
class TimelineEndpointTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issuer;
    @Autowired AddCoverageService adder;
    @Autowired CorrectCoverageService corrector;

    @Test
    void theOwnerGetsTheirTimelineWithTheCorrectionAndTheMoney() throws Exception {
        Paid paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issuer, adder, "Timeline Endpoint LLC");
        corrector.correct(paid.owner().policyId(), new CorrectCoverageCommand(CorrectionTestData.OCT_8,
                CorrectionTestData.REASON), CorrectionTestData.brokerId(jdbc));

        mvc.perform(get(MePaths.timeline(paid.owner().policyId())).cookie(paid.owner().session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.term.start").value("2026-09-01"))
                .andExpect(jsonPath("$.term.end").value("2027-09-01"))
                .andExpect(jsonPath("$.term.today").value("2026-11-22"))
                .andExpect(jsonPath("$.covers.length()").value(2))
                .andExpect(jsonPath("$.covers[0].kind").value("BASE"))
                .andExpect(jsonPath("$.covers[1].kind").value("PRODUCT_LIABILITY"))
                .andExpect(jsonPath("$.covers[1].from").value("2026-10-08"))
                .andExpect(jsonPath("$.covers[1].previousStart").value("2026-10-15"))
                .andExpect(jsonPath("$.covers[1].perDayCents").value(200))
                .andExpect(jsonPath("$.covers[1].daysKept").value(328))
                .andExpect(jsonPath("$.covers[1].chargedCents").value(65_600))
                .andExpect(jsonPath("$.covers[1].rows[0].trace.maths.days").value(328))
                .andExpect(jsonPath("$.covers[1].correction.dayDelta").value(7))
                .andExpect(jsonPath("$.covers[1].correction.reason").value(CorrectionTestData.REASON));
    }

    @Test
    void eachOwnerOnlySeesTheirOwnPolicy() throws Exception {
        var a = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Timeline Owner A LLC");
        Paid b = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issuer, adder, "Timeline Owner B LLC");

        mvc.perform(get(MePaths.timeline(a.policyId())).cookie(a.session()))
                .andExpect(jsonPath("$.covers.length()").value(1));
        mvc.perform(get(MePaths.timeline(b.owner().policyId())).cookie(b.owner().session()))
                .andExpect(jsonPath("$.covers.length()").value(2));
    }

    // covers more-policies AC9, T1
    @Test
    void anotherOwnersPolicyIsNotFound() throws Exception {
        var a = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Timeline Peeker A LLC");
        var b = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Timeline Peeker B LLC");

        mvc.perform(get(MePaths.timeline(b.policyId())).cookie(a.session())).andExpect(status().isNotFound());
        mvc.perform(get(MePaths.timeline(Long.MAX_VALUE)).cookie(a.session())).andExpect(status().isNotFound());
    }

    // covers more-policies AC9, T1: the per-policy route replaces the one-policy route
    @Test
    void theOldRouteIsGone() throws Exception {
        var a = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Old Timeline Route LLC");

        mvc.perform(get("/api/me/timeline").cookie(a.session())).andExpect(status().isNotFound());
    }

    @Test
    void aBrokerIsForbidden() throws Exception {
        var a = PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Timeline Broker Peek LLC");

        mvc.perform(get(MePaths.timeline(a.policyId())).cookie(AuthTestSupport.signInAsBroker(mvc)))
                .andExpect(status().isForbidden());
    }

    @Test
    void signedOutIsUnauthorized() throws Exception {
        mvc.perform(get(MePaths.timeline(1))).andExpect(status().isUnauthorized());
    }
}
