package app.plainly.policy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.AuthTestSupport;
import app.plainly.IntegrationTest;
import app.plainly.TestData;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// covers AC3, AC4, AC5, AC11, T2
@IntegrationTest
@AutoConfigureMockMvc
class AddCoverageEndpointsTest {

    static final String OCT_15 = "{\"kind\":\"PRODUCT_LIABILITY\",\"startDate\":\"2026-10-15\"}";

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issue;

    Cookie broker;
    String ownerEmail;
    long policyId;

    @BeforeEach
    void baseCoverOnlyPolicy() throws Exception {
        long brokerId = jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
        broker = AuthTestSupport.signInAsBroker(mvc);
        ownerEmail = TestData.uniqueEmail("maya");
        policyId = issue.issue(new IssuePolicyCommand("Add Cover Bakery LLC", "a neighborhood bakery", "Maya Okafor",
                ownerEmail, LocalDate.of(2026, 9, 1), false), brokerId).policyId();
    }

    String coverages(long id) {
        return "/api/broker/policies/" + id + "/coverages";
    }

    MockHttpServletRequestBuilder json(String url, String body, Cookie who) {
        return post(url).cookie(who).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void previewShowsTheChargeAndItsArithmetic() throws Exception {
        mvc.perform(json(coverages(policyId) + "/preview", OCT_15, broker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyId").value(policyId))
                .andExpect(jsonPath("$.kind").value("PRODUCT_LIABILITY"))
                .andExpect(jsonPath("$.label").value("Extra cover from Oct 15"))
                .andExpect(jsonPath("$.from").value("2026-10-15"))
                .andExpect(jsonPath("$.to").value("2027-09-01"))
                .andExpect(jsonPath("$.calc.annualCents").value(73000))
                .andExpect(jsonPath("$.calc.days").value(321))
                .andExpect(jsonPath("$.calc.termDays").value(365))
                .andExpect(jsonPath("$.calc.amountCents").value(64200))
                .andExpect(jsonPath("$.totalCents").value(64200));
    }

    @Test
    void addingAnswers201WithWhereToFindThePolicyAndItsNewBalance() throws Exception {
        mvc.perform(json(coverages(policyId), OCT_15, broker))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/broker/policies/" + policyId))
                .andExpect(jsonPath("$.policyId").value(policyId))
                .andExpect(jsonPath("$.transactionDisplayId", Matchers.matchesPattern("PTX-\\d{4,}")))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].displayId", Matchers.matchesPattern("LE-\\d{4,}")))
                .andExpect(jsonPath("$.totalCents").value(64200))
                .andExpect(jsonPath("$.balanceCents").value(120000 + 64200));

        mvc.perform(get("/api/broker/policies/" + policyId).cookie(broker))
                .andExpect(jsonPath("$.coverages[1].kind").value("PRODUCT_LIABILITY"))
                .andExpect(jsonPath("$.coverages[1].from").value("2026-10-15"))
                .andExpect(jsonPath("$.ledger[1].description").value("Extra cover from Oct 15"))
                .andExpect(jsonPath("$.ledger[1].calcDays").value(321))
                .andExpect(jsonPath("$.transactions[1].type").value("ADD_COVERAGE"));
    }

    @Test
    void aMissingOrUnreadableDateIsAFieldErrorNotACrash() throws Exception {
        for (String date : new String[] {"null", "\"\"", "\"Oct 15\""}) {
            mvc.perform(json(coverages(policyId) + "/preview", "{\"kind\":\"PRODUCT_LIABILITY\",\"startDate\":" + date + "}", broker))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.error").value("validation"))
                    .andExpect(jsonPath("$.fields.startDate").value("Pick the day the extra cover should start."));
        }
    }

    @Test
    void aCoverWeDoNotSellIsAFieldError() throws Exception {
        for (String kind : new String[] {"\"BASE\"", "\"SPACE_TRAVEL\"", "null"}) {
            mvc.perform(json(coverages(policyId), "{\"kind\":" + kind + ",\"startDate\":\"2026-10-15\"}", broker))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.fields.kind").value("Pick a cover to add."));
        }
    }

    @Test
    void addingItTwiceIsAFieldErrorOnTheCover() throws Exception {
        mvc.perform(json(coverages(policyId), OCT_15, broker)).andExpect(status().isCreated());
        mvc.perform(json(coverages(policyId), OCT_15, broker))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("1 thing to fix before you can save."))
                .andExpect(jsonPath("$.fields.kind").value("This policy already has product liability cover."));
    }

    @Test
    void aPolicyholderCannotPreviewOrAddCover() throws Exception {
        Cookie owner = AuthTestSupport.signUp(mvc, ownerEmail);
        mvc.perform(json(coverages(policyId) + "/preview", OCT_15, owner)).andExpect(status().isForbidden());
        mvc.perform(json(coverages(policyId), OCT_15, owner)).andExpect(status().isForbidden());
        mvc.perform(get("/api/broker/policies/" + policyId).cookie(broker))
                .andExpect(jsonPath("$.coverages.length()").value(1));
    }

    @Test
    void anUnknownPolicyIsNotFound() throws Exception {
        mvc.perform(json(coverages(999_999_999L) + "/preview", OCT_15, broker))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
        mvc.perform(json(coverages(999_999_999L), OCT_15, broker))
                .andExpect(status().isNotFound());
    }
}
