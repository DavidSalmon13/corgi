package app.plainly.policy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.AuthTestSupport;
import app.plainly.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

// covers AC8, AC9, T9
@IntegrationTest
@AutoConfigureMockMvc
class BrokerPolicyReadTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issue;

    Cookie broker;
    IssuePolicyService.Issued juniper;

    @BeforeEach
    void issueOne() throws Exception {
        long brokerId = jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
        broker = AuthTestSupport.signInAsBroker(mvc);
        juniper = issue.issue(new IssuePolicyCommand("Read Test Bakery LLC", "a neighborhood bakery", "Maya Okafor",
                app.plainly.TestData.uniqueEmail("maya"), LocalDate.of(2026, 9, 1), true), brokerId);
    }

    @Test
    void theListShowsEachPolicyWithItsBalanceAndLastAction() throws Exception {
        String row = "$[?(@.policyId == " + juniper.policyId() + ")]";
        mvc.perform(get("/api/broker/policies").cookie(broker))
                .andExpect(status().isOk())
                .andExpect(jsonPath(row + ".customerName").value("Read Test Bakery LLC"))
                .andExpect(jsonPath(row + ".ownerName").value("Maya Okafor"))
                .andExpect(jsonPath(row + ".policyNumber").value(juniper.policyNumber()))
                .andExpect(jsonPath(row + ".status").value("ACTIVE"))
                .andExpect(jsonPath(row + ".balanceCents").value(193000))
                .andExpect(jsonPath(row + ".owesMoney").value(true))
                .andExpect(jsonPath(row + ".lastAction.type").value("ISSUE"))
                .andExpect(jsonPath(row + ".lastAction.recordedOn").value("2026-11-22"));
    }

    @Test
    void thePolicyViewHasTheHeaderCoversAndEveryLedgerLineWithItsSource() throws Exception {
        mvc.perform(get("/api/broker/policies/" + juniper.policyId()).cookie(broker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policyNumber").value(juniper.policyNumber()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.start").value("2026-09-01"))
                .andExpect(jsonPath("$.end").value("2027-09-01"))
                .andExpect(jsonPath("$.today").value("2026-11-22"))
                .andExpect(jsonPath("$.customer.legalName").value("Read Test Bakery LLC"))
                .andExpect(jsonPath("$.customer.ownerName").value("Maya Okafor"))
                .andExpect(jsonPath("$.balanceCents").value(193000))
                .andExpect(jsonPath("$.coverages[0].kind").value("BASE"))
                .andExpect(jsonPath("$.coverages[0].label").value("Base cover"))
                .andExpect(jsonPath("$.coverages[0].annualPremiumCents").value(120000))
                .andExpect(jsonPath("$.coverages[1].kind").value("PRODUCT_LIABILITY"))
                .andExpect(jsonPath("$.coverages[1].from").value("2026-09-01"))
                .andExpect(jsonPath("$.ledger[0].displayId").value(juniper.entries().get(0).displayId()))
                .andExpect(jsonPath("$.ledger[0].policyTransactionDisplayId").value(juniper.transactionDisplayId()))
                .andExpect(jsonPath("$.ledger[0].calcDays").value(365))
                .andExpect(jsonPath("$.ledger[0].balanceAfterCents").value(120000))
                .andExpect(jsonPath("$.ledger[1].balanceAfterCents").value(193000))
                .andExpect(jsonPath("$.transactions[0].displayId").value(juniper.transactionDisplayId()))
                .andExpect(jsonPath("$.transactions[0].type").value("ISSUE"));
    }

    @Test
    void anUnknownPolicyIsNotFound() throws Exception {
        mvc.perform(get("/api/broker/policies/999999").cookie(broker))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not_found"));
    }

    // covers real-sign-in AC11, T8 — the broker can see whether the owner has created their account
    @Test
    void thePolicyViewSaysWhetherTheOwnerHasSignedUp() throws Exception {
        String url = "/api/broker/policies/" + juniper.policyId();
        mvc.perform(get(url).cookie(broker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.ownerSignedUpAt").doesNotExist());
        String email = jdbc.sql("select c.owner_email from customers c join policies p on p.customer_id = c.id where p.id = ?")
                .param(juniper.policyId()).query(String.class).single();
        AuthTestSupport.signUp(mvc, email);
        mvc.perform(get(url).cookie(broker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.ownerSignedUpAt").isNotEmpty());
    }
}
