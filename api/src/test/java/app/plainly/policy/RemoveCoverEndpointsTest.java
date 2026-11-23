package app.plainly.policy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.AuthTestSupport;
import app.plainly.CorrectionTestData;
import app.plainly.CorrectionTestData.Paid;
import app.plainly.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * B4's Remove tab over HTTP: the preview, the save, and the field messages the form puts under each box. The policy
 * is paid in full with extra cover from Oct 15 ($642.00); removing it from Dec 1 gives back 274 days, $548.00.
 */
// covers AC2, AC3, AC4, AC5, T1
@IntegrationTest
@AutoConfigureMockMvc
class RemoveCoverEndpointsTest {

    static final String REASON = "Stopped selling packaged goods to shops.";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issuer;
    @Autowired AddCoverageService adder;

    Cookie broker;
    Paid paid;
    long policyId;

    @BeforeEach
    void paidWithExtraCover() throws Exception {
        broker = AuthTestSupport.signInAsBroker(mvc);
        paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issuer, adder, "Remove Endpoints Bakery LLC");
        policyId = paid.owner().policyId();
    }

    String removal(long id) {
        return "/api/broker/policies/" + id + "/cover-removal";
    }

    static String body(String endDate, String reason) {
        return "{\"endDate\":" + endDate + ",\"reason\":" + reason + "}";
    }

    static String from(String date) {
        return body("\"" + date + "\"", "\"" + REASON + "\"");
    }

    MockHttpServletRequestBuilder json(String url, String body, Cookie who) {
        return post(url).cookie(who).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // --- preview (AC3) ---

    @Test
    void previewShowsTheCreditAndTheReturnPremium() throws Exception {
        mvc.perform(json(removal(policyId) + "/preview", from("2026-12-01"), broker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyId").value(policyId))
                .andExpect(jsonPath("$.endDate").value("2026-12-01"))
                .andExpect(jsonPath("$.coverFrom").value("2026-10-15"))
                .andExpect(jsonPath("$.policyEnd").value("2027-09-01"))
                .andExpect(jsonPath("$.credit.kind").value("PRODUCT_LIABILITY"))
                .andExpect(jsonPath("$.credit.label").value("Product liability return premium"))
                .andExpect(jsonPath("$.credit.from").value("2026-12-01"))
                .andExpect(jsonPath("$.credit.to").value("2027-09-01"))
                .andExpect(jsonPath("$.credit.calc.annualCents").value(73000))
                .andExpect(jsonPath("$.credit.calc.days").value(274))
                .andExpect(jsonPath("$.credit.calc.termDays").value(365))
                .andExpect(jsonPath("$.credit.amountCents").value(-54800))
                .andExpect(jsonPath("$.creditCents").value(-54800))
                .andExpect(jsonPath("$.keptBackCents").value(0))
                .andExpect(jsonPath("$.balanceAfterCents").value(-54800))
                .andExpect(jsonPath("$.stillOwedCents").value(0))
                .andExpect(jsonPath("$.refunds.length()").value(1))
                .andExpect(jsonPath("$.refunds[0].paymentId").value(paid.extraPaymentId()))
                .andExpect(jsonPath("$.refunds[0].cardLast4").value("4242"))
                .andExpect(jsonPath("$.refunds[0].amountCents").value(54800))
                .andExpect(jsonPath("$.refundTotalCents").value(54800));
    }

    @Test
    void previewDoesNotNeedAReasonYet() throws Exception {
        mvc.perform(json(removal(policyId) + "/preview", body("\"2026-12-01\"", "null"), broker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundTotalCents").value(54800));
    }

    // --- save (AC4) ---

    @Test
    void savingAnswers201WithTheIdsTheBannerShows() throws Exception {
        mvc.perform(json(removal(policyId), from("2026-12-01"), broker))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/broker/policies/" + policyId))
                .andExpect(jsonPath("$.policyId").value(policyId))
                .andExpect(jsonPath("$.endDate").value("2026-12-01"))
                .andExpect(jsonPath("$.transactionDisplayId", Matchers.matchesPattern("PTX-\\d{4,}")))
                .andExpect(jsonPath("$.creditDisplayId", Matchers.matchesPattern("LE-\\d{4,}")))
                .andExpect(jsonPath("$.balanceCents").value(-54800))
                .andExpect(jsonPath("$.refundTotalCents").value(54800))
                .andExpect(jsonPath("$.refunds.length()").value(1))
                .andExpect(jsonPath("$.refunds[0].displayId", Matchers.matchesPattern("RF-\\d{4,}")))
                .andExpect(jsonPath("$.refunds[0].paymentId").value(paid.extraPaymentId()));
        mvc.perform(get("/api/broker/policies/" + policyId).cookie(broker))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.coverages[1].endedOn").value("2026-12-01"))
                .andExpect(jsonPath("$.transactions[?(@.type == 'REMOVE_COVERAGE')].reason").value(REASON));
    }

    // --- 422: the message goes under the field to fix (AC2, AC5) ---

    @Test
    void aMissingOrUnreadableDateIsAFieldErrorNotACrash() throws Exception {
        for (String date : new String[] {"null", "\"\"", "\"Dec 1\""}) {
            mvc.perform(json(removal(policyId) + "/preview", body(date, "\"" + REASON + "\""), broker))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error").value("validation"))
                    .andExpect(jsonPath("$.fields.endDate").value(RemoveCoverService.NO_DATE));
        }
    }

    @Test
    void aDayOutsideTheExtraCoverIsAFieldErrorOnTheDate() throws Exception {
        mvc.perform(json(removal(policyId), from("2026-10-14"), broker))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.endDate").value("Product liability can't end before its effective date (Oct 15, 2026)."));
        mvc.perform(json(removal(policyId), from("2027-09-01"), broker))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.endDate").value("Pick a day before the policy period ends (Sep 1, 2027)."));
    }

    @Test
    void savingWithoutAReasonListsBothThingsToFix() throws Exception {
        mvc.perform(json(removal(policyId), body("null", "\"  \""), broker))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("2 things to fix before you can save."))
                .andExpect(jsonPath("$.fields.endDate").value(RemoveCoverService.NO_DATE))
                .andExpect(jsonPath("$.fields.reason").value(RemoveCoverService.NO_REASON));
    }

    @Test
    void aReasonOver500CharactersIsAFieldError() throws Exception {
        mvc.perform(json(removal(policyId), body("\"2026-12-01\"", "\"" + "x".repeat(501) + "\""), broker))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.reason").value(RemoveCoverService.REASON_TOO_LONG));
    }

    @Test
    void removingTwiceIsAPolicyError() throws Exception {
        mvc.perform(json(removal(policyId), from("2026-12-01"), broker)).andExpect(status().isCreated());
        mvc.perform(json(removal(policyId), from("2026-12-01"), broker))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.policy").value("Product liability was removed on Dec 1, 2026."));
        mvc.perform(json("/api/broker/policies/" + policyId + "/coverages",
                        "{\"kind\":\"PRODUCT_LIABILITY\",\"startDate\":\"2027-01-01\"}", broker))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.policy")
                        .value("Product liability was removed from this policy on Dec 1, 2026, so it can't be added again."));
    }

    // --- who may, and what exists (403, 404) ---

    @Test
    void aPolicyholderCannotPreviewOrRemove() throws Exception {
        Cookie owner = paid.owner().session();
        mvc.perform(json(removal(policyId) + "/preview", from("2026-12-01"), owner))
                .andExpect(status().isForbidden());
        mvc.perform(json(removal(policyId), from("2026-12-01"), owner))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/broker/policies/" + policyId).cookie(broker))
                .andExpect(jsonPath("$.transactions[?(@.type == 'REMOVE_COVERAGE')]").isEmpty());
    }

    @Test
    void signedOutIsUnauthorized() throws Exception {
        mvc.perform(post(removal(policyId)).contentType(MediaType.APPLICATION_JSON).content(from("2026-12-01")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownPolicyIsNotFound() throws Exception {
        mvc.perform(json(removal(999_999_999L) + "/preview", from("2026-12-01"), broker))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
        mvc.perform(json(removal(999_999_999L), from("2026-12-01"), broker))
                .andExpect(status().isNotFound());
    }
}
