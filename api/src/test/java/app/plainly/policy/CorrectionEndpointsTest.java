package app.plainly.policy;

import static app.plainly.CorrectionTestData.REASON;
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

/** B4's Correct tab over HTTP: the preview, the save, and the field messages the form puts under each box. */
// covers AC3, AC4, AC5, AC6, AC7, T2
@IntegrationTest
@AutoConfigureMockMvc
class CorrectionEndpointsTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issuer;
    @Autowired AddCoverageService adder;

    Cookie broker;
    Paid paid;
    long policyId;

    @BeforeEach
    void paidWithExtraCoverFromOct15() throws Exception {
        broker = AuthTestSupport.signInAsBroker(mvc);
        paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issuer, adder, "Correct Endpoints Bakery LLC");
        policyId = paid.owner().policyId();
    }

    String corrections(long id) {
        return "/api/broker/policies/" + id + "/corrections";
    }

    static String body(String startDate, String reason) {
        return "{\"startDate\":" + startDate + ",\"reason\":" + reason + "}";
    }

    static String startOn(String date) {
        return body("\"" + date + "\"", "\"" + REASON + "\"");
    }

    MockHttpServletRequestBuilder json(String url, String body, Cookie who) {
        return post(url).cookie(who).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // --- preview (AC3, AC4, AC5) ---

    @Test
    void previewShowsTheUndoTheCorrectedChargeAndTheNetChange() throws Exception {
        mvc.perform(json(corrections(policyId) + "/preview", startOn("2026-10-08"), broker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyId").value(policyId))
                .andExpect(jsonPath("$.oldStart").value("2026-10-15"))
                .andExpect(jsonPath("$.newStart").value("2026-10-08"))
                .andExpect(jsonPath("$.undo.entryDisplayId", Matchers.matchesPattern("LE-\\d{4,}")))
                .andExpect(jsonPath("$.undo.label").value("Product liability endorsement from Oct 15"))
                .andExpect(jsonPath("$.undo.amountCents").value(-64200))
                .andExpect(jsonPath("$.undo.calc.annualCents").value(73000))
                .andExpect(jsonPath("$.undo.calc.days").value(321))
                .andExpect(jsonPath("$.undo.calc.termDays").value(365))
                .andExpect(jsonPath("$.rebill.label").value("Product liability endorsement from Oct 8"))
                .andExpect(jsonPath("$.rebill.from").value("2026-10-08"))
                .andExpect(jsonPath("$.rebill.to").value("2027-09-01"))
                .andExpect(jsonPath("$.rebill.calc.days").value(328))
                .andExpect(jsonPath("$.rebill.calc.amountCents").value(65600))
                .andExpect(jsonPath("$.netCents").value(1400))
                .andExpect(jsonPath("$.balanceAfterCents").value(1400))
                .andExpect(jsonPath("$.refunds.length()").value(0))
                .andExpect(jsonPath("$.refundTotalCents").value(0));
    }

    @Test
    void previewOfALaterStartListsEachRefundWithItsCard() throws Exception {
        mvc.perform(json(corrections(policyId) + "/preview", startOn("2026-10-22"), broker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rebill.calc.days").value(314))
                .andExpect(jsonPath("$.rebill.calc.amountCents").value(62800))
                .andExpect(jsonPath("$.netCents").value(-1400))
                .andExpect(jsonPath("$.refunds.length()").value(1))
                .andExpect(jsonPath("$.refunds[0].paymentId").value(paid.extraPaymentId()))
                .andExpect(jsonPath("$.refunds[0].paymentDisplayId").isNotEmpty())
                .andExpect(jsonPath("$.refunds[0].cardLast4").value("4242"))
                .andExpect(jsonPath("$.refunds[0].amountCents").value(1400))
                .andExpect(jsonPath("$.refundTotalCents").value(1400));
    }

    @Test
    void previewDoesNotNeedAReasonYet() throws Exception {
        mvc.perform(json(corrections(policyId) + "/preview", body("\"2026-10-08\"", "null"), broker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.netCents").value(1400));
    }

    // --- save (AC7) ---

    @Test
    void savingAnswers201WithTheIdsTheBannerShows() throws Exception {
        mvc.perform(json(corrections(policyId), startOn("2026-10-08"), broker))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/broker/policies/" + policyId))
                .andExpect(jsonPath("$.policyId").value(policyId))
                .andExpect(jsonPath("$.transactionDisplayId", Matchers.matchesPattern("PTX-\\d{4,}")))
                .andExpect(jsonPath("$.undoDisplayId", Matchers.matchesPattern("LE-\\d{4,}")))
                .andExpect(jsonPath("$.rebillDisplayId", Matchers.matchesPattern("LE-\\d{4,}")))
                .andExpect(jsonPath("$.netCents").value(1400))
                .andExpect(jsonPath("$.balanceCents").value(1400))
                .andExpect(jsonPath("$.refunds.length()").value(0));
    }

    @Test
    void savingACreditAnswersWithTheRefundItStarted() throws Exception {
        mvc.perform(json(corrections(policyId), startOn("2026-10-22"), broker))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.netCents").value(-1400))
                .andExpect(jsonPath("$.balanceCents").value(-1400))
                .andExpect(jsonPath("$.refunds.length()").value(1))
                .andExpect(jsonPath("$.refunds[0].displayId", Matchers.matchesPattern("RF-\\d{4,}")))
                .andExpect(jsonPath("$.refunds[0].paymentId").value(paid.extraPaymentId()))
                .andExpect(jsonPath("$.refunds[0].amountCents").value(1400));
    }

    // --- 422: the message goes under the field to fix (AC6) ---

    @Test
    void aMissingOrUnreadableDateIsAFieldErrorNotACrash() throws Exception {
        for (String date : new String[] {"null", "\"\"", "\"Oct 8\""}) {
            mvc.perform(json(corrections(policyId) + "/preview", body(date, "\"" + REASON + "\""), broker))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error").value("validation"))
                    .andExpect(jsonPath("$.fields.startDate").value(CorrectCoverageService.NO_START));
        }
    }

    @Test
    void theSameStartIsAFieldErrorOnTheDate() throws Exception {
        mvc.perform(json(corrections(policyId), startOn("2026-10-15"), broker))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.startDate").value(CorrectCoverageService.SAME_START));
    }

    @Test
    void savingWithoutAReasonListsBothThingsToFix() throws Exception {
        mvc.perform(json(corrections(policyId), body("null", "\"  \""), broker))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("2 things to fix before you can save."))
                .andExpect(jsonPath("$.fields.startDate").value(CorrectCoverageService.NO_START))
                .andExpect(jsonPath("$.fields.reason").value(CorrectCoverageService.NO_REASON));
    }

    @Test
    void aReasonOver500CharactersIsAFieldError() throws Exception {
        mvc.perform(json(corrections(policyId), body("\"2026-10-08\"", "\"" + "x".repeat(501) + "\""), broker))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.reason").value(CorrectCoverageService.REASON_TOO_LONG));
    }

    @Test
    void aPolicyWithoutExtraCoverIsAPolicyError() throws Exception {
        long baseOnly = app.plainly.PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Base Only Bakery LLC").policyId();
        mvc.perform(json(corrections(baseOnly) + "/preview", startOn("2026-10-08"), broker))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.policy").value(CorrectCoverageService.NO_EXTRA_COVER));
    }

    // --- who may, and what exists (403, 404) ---

    @Test
    void aPolicyholderCannotPreviewOrSaveACorrection() throws Exception {
        Cookie owner = paid.owner().session();
        mvc.perform(json(corrections(policyId) + "/preview", startOn("2026-10-08"), owner))
                .andExpect(status().isForbidden());
        mvc.perform(json(corrections(policyId), startOn("2026-10-08"), owner))
                .andExpect(status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/broker/policies/" + policyId).cookie(broker))
                .andExpect(jsonPath("$.transactions[?(@.type == 'CORRECT')]").isEmpty());
    }

    @Test
    void anUnknownPolicyIsNotFound() throws Exception {
        mvc.perform(json(corrections(999_999_999L) + "/preview", startOn("2026-10-08"), broker))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
        mvc.perform(json(corrections(999_999_999L), startOn("2026-10-08"), broker))
                .andExpect(status().isNotFound());
    }
}
