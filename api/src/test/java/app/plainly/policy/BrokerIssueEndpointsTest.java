package app.plainly.policy;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.AuthTestSupport;
import app.plainly.IntegrationTest;
import app.plainly.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// covers AC2, AC5, AC6, T8.1
@IntegrationTest
@AutoConfigureMockMvc
class BrokerIssueEndpointsTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;

    Cookie broker;

    @BeforeEach
    void signInAsBroker() throws Exception {
        broker = AuthTestSupport.signInAsBroker(mvc);
    }

    MockHttpServletRequestBuilder json(String url, String body) {
        return post(url).cookie(broker).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void previewPricesEachCoverWithItsArithmetic() throws Exception {
        mvc.perform(json("/api/broker/policies/preview", "{\"startDate\":\"2026-09-01\",\"productLiability\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value("2026-09-01"))
                .andExpect(jsonPath("$.end").value("2027-09-01"))
                .andExpect(jsonPath("$.lines[0].description").value("Base cover, full year"))
                .andExpect(jsonPath("$.lines[0].calc.annualCents").value(120000))
                .andExpect(jsonPath("$.lines[0].calc.days").value(365))
                .andExpect(jsonPath("$.lines[0].calc.termDays").value(365))
                .andExpect(jsonPath("$.lines[0].calc.amountCents").value(120000))
                .andExpect(jsonPath("$.lines[1].kind").value("PRODUCT_LIABILITY"))
                .andExpect(jsonPath("$.totalCents").value(193000));
    }

    @Test
    void previewWithoutAStartDateExplainsWhatIsMissing() throws Exception {
        mvc.perform(json("/api/broker/policies/preview", "{\"startDate\":null,\"productLiability\":false}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.startDate").value("Pick the day cover should start."));
    }

    @Test
    void aStartDateThatIsNotADateIsAFieldErrorNotACrash() throws Exception {
        mvc.perform(json("/api/broker/policies/preview", "{\"startDate\":\"Sep 1\",\"productLiability\":false}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.startDate").exists());
    }

    @Test
    void issuingReturnsThePolicyNumberAndWhereToFindIt() throws Exception {
        mvc.perform(json("/api/broker/policies", """
                {"legalName":"Endpoint Bakery LLC","businessDescription":"a bakery","ownerName":"Lee Owner",
                 "ownerEmail":"%s","startDate":"2026-09-01","productLiability":false}""".formatted(TestData.uniqueEmail("lee"))))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/broker/policies/\\d+")))
                .andExpect(jsonPath("$.policyNumber", matchesPattern("GL-2026-\\d{4}")))
                .andExpect(jsonPath("$.transactionDisplayId", matchesPattern("PTX-\\d{4,}")))
                .andExpect(jsonPath("$.entries[0].displayId", matchesPattern("LE-\\d{4,}")))
                .andExpect(jsonPath("$.totalCents").value(120000));
    }

    // covers real-sign-in AC6, T2
    @Test
    void anOwnerEmailAnotherCustomerUsesGetsAFieldError() throws Exception {
        String email = TestData.uniqueEmail("dup");
        String body = """
                {"legalName":"Dup Bakery LLC","businessDescription":"a bakery","ownerName":"Dee Owner",
                 "ownerEmail":"%s","startDate":"2026-09-01","productLiability":false}""";
        mvc.perform(json("/api/broker/policies", body.formatted(email))).andExpect(status().isCreated());
        mvc.perform(json("/api/broker/policies", body.formatted(email)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.fields.ownerEmail").value("Another customer already uses this email."));
    }

    @Test
    void anEmptyFormListsEveryProblemInOneAnswer() throws Exception {
        mvc.perform(json("/api/broker/policies", "{\"ownerEmail\":\"maya@juniperandrye\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("5 things to fix before you can save."))
                .andExpect(jsonPath("$.fields.legalName").value("Enter the business's legal name."))
                .andExpect(jsonPath("$.fields.ownerEmail").value("Enter an email like name@business.com."))
                .andExpect(jsonPath("$.fields.startDate").exists());
    }

    @Test
    void aBodyThatIsNotJsonIsABadRequest() throws Exception {
        mvc.perform(json("/api/broker/policies", "not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"));
    }
}
