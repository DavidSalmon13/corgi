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
 * B4's Cancel tab over HTTP: the preview, the save, and the field messages the form puts under each box. The policy
 * is the spec's worked example: everything paid, extra cover corrected to Oct 8 so $14.00 is still owed.
 */
// covers AC3, AC5, AC6, AC7, AC8, T6
@IntegrationTest
@AutoConfigureMockMvc
class CancellationEndpointsTest {

    static final String REASON = "Closing the bakery at the end of November.";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issuer;
    @Autowired AddCoverageService adder;
    @Autowired CorrectCoverageService corrector;

    Cookie broker;
    Paid paid;
    long policyId;

    @BeforeEach
    void workedExample() throws Exception {
        broker = AuthTestSupport.signInAsBroker(mvc);
        paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issuer, adder, "Cancel Endpoints Bakery LLC");
        policyId = paid.owner().policyId();
        corrector.correct(policyId, new CorrectCoverageCommand(CorrectionTestData.OCT_8, CorrectionTestData.REASON),
                CorrectionTestData.brokerId(jdbc));
    }

    String cancellation(long id) {
        return "/api/broker/policies/" + id + "/cancellation";
    }

    static String body(String cancelDate, String reason) {
        return "{\"cancelDate\":" + cancelDate + ",\"reason\":" + reason + "}";
    }

    static String from(String date) {
        return body("\"" + date + "\"", "\"" + REASON + "\"");
    }

    MockHttpServletRequestBuilder json(String url, String body, Cookie who) {
        return post(url).cookie(who).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // --- preview (AC3, AC5) ---

    @Test
    void previewShowsEachCoversCreditWhatIsKeptBackAndTheRefund() throws Exception {
        mvc.perform(json(cancellation(policyId) + "/preview", from("2026-12-01"), broker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyId").value(policyId))
                .andExpect(jsonPath("$.cancelDate").value("2026-12-01"))
                .andExpect(jsonPath("$.usedDays").value(91))
                .andExpect(jsonPath("$.unusedDays").value(274))
                .andExpect(jsonPath("$.termDays").value(365))
                .andExpect(jsonPath("$.credits.length()").value(2))
                .andExpect(jsonPath("$.credits[0].kind").value("BASE"))
                .andExpect(jsonPath("$.credits[0].label").value("Base cover, unused days"))
                .andExpect(jsonPath("$.credits[0].from").value("2026-12-01"))
                .andExpect(jsonPath("$.credits[0].calc.annualCents").value(120000))
                .andExpect(jsonPath("$.credits[0].calc.days").value(274))
                .andExpect(jsonPath("$.credits[0].calc.termDays").value(365))
                .andExpect(jsonPath("$.credits[0].amountCents").value(-90082))
                .andExpect(jsonPath("$.credits[1].label").value("Extra cover, unused days"))
                .andExpect(jsonPath("$.credits[1].amountCents").value(-54800))
                .andExpect(jsonPath("$.creditTotalCents").value(-144882))
                .andExpect(jsonPath("$.keptBackCents").value(1400))
                .andExpect(jsonPath("$.keptBackFor").value("CORRECT"))
                .andExpect(jsonPath("$.stillOwedCents").value(0))
                .andExpect(jsonPath("$.refunds.length()").value(2))
                .andExpect(jsonPath("$.refunds[0].paymentId").value(paid.extraPaymentId()))
                .andExpect(jsonPath("$.refunds[0].cardLast4").value("4242"))
                .andExpect(jsonPath("$.refunds[0].amountCents").value(64200))
                .andExpect(jsonPath("$.refunds[1].amountCents").value(79282))
                .andExpect(jsonPath("$.refundTotalCents").value(143482));
    }

    @Test
    void previewDoesNotNeedAReasonYet() throws Exception {
        mvc.perform(json(cancellation(policyId) + "/preview", body("\"2026-12-01\"", "null"), broker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundTotalCents").value(143482));
    }

    @Test
    void previewOfAnUnpaidPolicyShowsWhatIsStillOwed() throws Exception {
        long unpaid = app.plainly.PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issuer, "Unpaid Bakery LLC").policyId();
        mvc.perform(json(cancellation(unpaid) + "/preview", from("2026-12-01"), broker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creditTotalCents").value(-90082))
                .andExpect(jsonPath("$.stillOwedCents").value(29918))
                .andExpect(jsonPath("$.refunds.length()").value(0))
                .andExpect(jsonPath("$.refundTotalCents").value(0));
    }

    // --- save (AC7) ---

    @Test
    void savingAnswers201WithTheIdsTheBannerShows() throws Exception {
        mvc.perform(json(cancellation(policyId), from("2026-12-01"), broker))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/broker/policies/" + policyId))
                .andExpect(jsonPath("$.policyId").value(policyId))
                .andExpect(jsonPath("$.cancelDate").value("2026-12-01"))
                .andExpect(jsonPath("$.transactionDisplayId", Matchers.matchesPattern("PTX-\\d{4,}")))
                .andExpect(jsonPath("$.creditDisplayIds.length()").value(2))
                .andExpect(jsonPath("$.creditDisplayIds[0]", Matchers.matchesPattern("LE-\\d{4,}")))
                .andExpect(jsonPath("$.balanceCents").value(-143482))
                .andExpect(jsonPath("$.refundTotalCents").value(143482))
                .andExpect(jsonPath("$.refunds.length()").value(2))
                .andExpect(jsonPath("$.refunds[0].displayId", Matchers.matchesPattern("RF-\\d{4,}")))
                .andExpect(jsonPath("$.refunds[0].paymentId").value(paid.extraPaymentId()));
        mvc.perform(get("/api/broker/policies/" + policyId).cookie(broker))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.transactions[?(@.type == 'CANCEL')].reason").value(REASON));
    }

    // --- 422: the message goes under the field to fix (AC6, AC8) ---

    @Test
    void aMissingOrUnreadableDateIsAFieldErrorNotACrash() throws Exception {
        for (String date : new String[] {"null", "\"\"", "\"Dec 1\""}) {
            mvc.perform(json(cancellation(policyId) + "/preview", body(date, "\"" + REASON + "\""), broker))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error").value("validation"))
                    .andExpect(jsonPath("$.fields.cancelDate").value(CancelPolicyService.NO_DATE));
        }
    }

    @Test
    void aDayOutsideTheTermIsAFieldErrorOnTheDate() throws Exception {
        mvc.perform(json(cancellation(policyId), from("2026-08-31"), broker))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.cancelDate").value("Cover can't end before the policy starts (Sep 1, 2026)."));
        mvc.perform(json(cancellation(policyId), from("2027-09-01"), broker))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.cancelDate").value("Pick a day before the policy ends (Sep 1, 2027)."));
    }

    @Test
    void savingWithoutAReasonListsBothThingsToFix() throws Exception {
        mvc.perform(json(cancellation(policyId), body("null", "\"  \""), broker))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("2 things to fix before you can save."))
                .andExpect(jsonPath("$.fields.cancelDate").value(CancelPolicyService.NO_DATE))
                .andExpect(jsonPath("$.fields.reason").value(CancelPolicyService.NO_REASON));
    }

    @Test
    void aReasonOver500CharactersIsAFieldError() throws Exception {
        mvc.perform(json(cancellation(policyId), body("\"2026-12-01\"", "\"" + "x".repeat(501) + "\""), broker))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.reason").value(CancelPolicyService.REASON_TOO_LONG));
    }

    @Test
    void cancellingTwiceIsAPolicyError() throws Exception {
        mvc.perform(json(cancellation(policyId), from("2026-12-01"), broker)).andExpect(status().isCreated());
        mvc.perform(json(cancellation(policyId), from("2026-12-01"), broker))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.policy").value(CancelPolicyService.ALREADY_CANCELLED));
        mvc.perform(json(cancellation(policyId) + "/preview", from("2026-12-01"), broker))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.policy").value(CancelPolicyService.ALREADY_CANCELLED));
    }

    // --- who may, and what exists (403, 404) ---

    @Test
    void aPolicyholderCannotPreviewOrCancel() throws Exception {
        Cookie owner = paid.owner().session();
        mvc.perform(json(cancellation(policyId) + "/preview", from("2026-12-01"), owner))
                .andExpect(status().isForbidden());
        mvc.perform(json(cancellation(policyId), from("2026-12-01"), owner))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/broker/policies/" + policyId).cookie(broker))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.transactions[?(@.type == 'CANCEL')]").isEmpty());
    }

    @Test
    void signedOutIsUnauthorized() throws Exception {
        mvc.perform(post(cancellation(policyId)).contentType(MediaType.APPLICATION_JSON).content(from("2026-12-01")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownPolicyIsNotFound() throws Exception {
        mvc.perform(json(cancellation(999_999_999L) + "/preview", from("2026-12-01"), broker))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
        mvc.perform(json(cancellation(999_999_999L), from("2026-12-01"), broker))
                .andExpect(status().isNotFound());
    }
}
