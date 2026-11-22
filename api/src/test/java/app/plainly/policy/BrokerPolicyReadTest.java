package app.plainly.policy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.AuthTestSupport;
import app.plainly.CorrectionTestData;
import app.plainly.CorrectionTestData.Paid;
import app.plainly.IntegrationTest;
import app.plainly.StripeTestSupport;
import app.plainly.TestData;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

// covers AC8, AC9, T9; correct-effective-date AC7, AC11, AC12, T2; cancel-refund AC9, T7
@IntegrationTest
@AutoConfigureMockMvc
class BrokerPolicyReadTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issue;
    @Autowired AddCoverageService addCover;
    @Autowired CorrectCoverageService corrector;
    @Autowired CancelPolicyService canceller;

    Cookie broker;
    IssuePolicyService.Issued juniper;

    @BeforeEach
    void issueOne() throws Exception {
        long brokerId = jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
        broker = AuthTestSupport.signInAsBroker(mvc);
        juniper = issue.issue(new IssuePolicyCommand("Read Test Bakery LLC", "a neighborhood bakery", "Maya Okafor",
                TestData.uniqueEmail("maya"), LocalDate.of(2026, 9, 1), true), brokerId);
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

    // covers add-coverage AC10, T2 — B1 shows adding cover as the last action, and the policy owes money
    @Test
    void addedExtraCoverIsTheLastActionInTheList() throws Exception {
        long brokerId = jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
        long policyId = issue.issue(new IssuePolicyCommand("Last Action Bakery LLC", "a neighborhood bakery", "Maya Okafor",
                TestData.uniqueEmail("maya"), LocalDate.of(2026, 9, 1), false), brokerId).policyId();
        addCover.add(policyId, new AddCoverageCommand(CoverKind.PRODUCT_LIABILITY, LocalDate.of(2026, 10, 15)), brokerId);

        String row = "$[?(@.policyId == " + policyId + ")]";
        mvc.perform(get("/api/broker/policies").cookie(broker))
                .andExpect(status().isOk())
                .andExpect(jsonPath(row + ".lastAction.type").value("ADD_COVERAGE"))
                .andExpect(jsonPath(row + ".lastAction.recordedOn").value("2026-11-22"))
                .andExpect(jsonPath(row + ".balanceCents").value(120000 + 64200))
                .andExpect(jsonPath(row + ".owesMoney").value(true));
    }

    // --- correct-effective-date AC7, AC11, AC12, T2: B3 after a correction ---

    @Test
    void afterACorrectionThePolicyShowsOneExtraCoverWithItsOldStartAndTheReason() throws Exception {
        long brokerId = CorrectionTestData.brokerId(jdbc);
        corrector.correct(juniper.policyId(), new CorrectCoverageCommand(LocalDate.of(2026, 10, 1),
                CorrectionTestData.REASON), brokerId);

        mvc.perform(get("/api/broker/policies/" + juniper.policyId()).cookie(broker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverages.length()").value(2))
                .andExpect(jsonPath("$.coverages[0].kind").value("BASE"))
                .andExpect(jsonPath("$.coverages[0].previousStart").doesNotExist())
                .andExpect(jsonPath("$.coverages[1].kind").value("PRODUCT_LIABILITY"))
                .andExpect(jsonPath("$.coverages[1].from").value("2026-10-01"))
                .andExpect(jsonPath("$.coverages[1].previousStart").value("2026-09-01"))
                .andExpect(jsonPath("$.transactions[0].reason").doesNotExist())
                .andExpect(jsonPath("$.transactions[1].type").value("CORRECT"))
                .andExpect(jsonPath("$.transactions[1].effectiveDate").value("2026-10-01"))
                .andExpect(jsonPath("$.transactions[1].reason").value(CorrectionTestData.REASON))
                .andExpect(jsonPath("$.ledger[1].reversedByDisplayId").value(org.hamcrest.Matchers.startsWith("LE-")))
                .andExpect(jsonPath("$.ledger[2].type").value("REVERSAL"))
                .andExpect(jsonPath("$.ledger[2].reversesDisplayId").value(juniper.entries().get(1).displayId()))
                .andExpect(jsonPath("$.ledger[2].coverageId").isNumber())
                .andExpect(jsonPath("$.ledger[3].type").value("CHARGE"))
                .andExpect(jsonPath("$.ledger[3].description").value("Extra cover from Oct 1"))
                .andExpect(jsonPath("$.refunds.length()").value(0));

        String row = "$[?(@.policyId == " + juniper.policyId() + ")]";
        mvc.perform(get("/api/broker/policies").cookie(broker))
                .andExpect(jsonPath(row + ".lastAction.type").value("CORRECT"));
    }

    @Test
    void thePolicyListsItsRefundsAndTheRefundLineSaysWhichRefundItWas() throws Exception {
        Paid paid = CorrectionTestData.paidWithExtraCoverFromOct15(mvc, jdbc, issue, addCover, "Refund Read Bakery LLC");
        long policyId = paid.owner().policyId();
        CorrectCoverageService.Corrected saved = corrector.correct(policyId,
                new CorrectCoverageCommand(CorrectionTestData.OCT_22, CorrectionTestData.REASON),
                CorrectionTestData.brokerId(jdbc));
        String refund = saved.refunds().getFirst().displayId();
        String url = "/api/broker/policies/" + policyId;

        mvc.perform(get(url).cookie(broker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refunds.length()").value(1))
                .andExpect(jsonPath("$.refunds[0].id").isNumber())
                .andExpect(jsonPath("$.refunds[0].displayId").value(refund))
                .andExpect(jsonPath("$.refunds[0].status").value("PENDING"))
                .andExpect(jsonPath("$.refunds[0].amountCents").value(1400))
                .andExpect(jsonPath("$.refunds[0].cardLast4").value("4242"))
                .andExpect(jsonPath("$.refunds[0].attempt").value(1))
                .andExpect(jsonPath("$.refunds[0].policyTransactionDisplayId").value(saved.transactionDisplayId()))
                .andExpect(jsonPath("$.refunds[0].stripeRefundId").isNotEmpty())
                .andExpect(jsonPath("$.refunds[0].failureReason").doesNotExist());

        long refundId = jdbc.sql("select id from refunds where display_id = ?").param(refund).query(Long.class).single();
        String stripeRefundId = jdbc.sql("select stripe_refund_id from refunds where id = ?").param(refundId)
                .query(String.class).single();
        String eventId = StripeTestSupport.newEventId();
        mvc.perform(StripeTestSupport.delivery(StripeTestSupport.refundSucceeded(eventId, stripeRefundId, refundId,
                paid.extraIntentId(), 1400))).andExpect(status().isOk());

        String refundLine = "$.ledger[?(@.type == 'REFUND')]";
        mvc.perform(get(url).cookie(broker))
                .andExpect(jsonPath("$.refunds[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.balanceCents").value(0))
                .andExpect(jsonPath(refundLine + ".amountCents").value(1400))
                .andExpect(jsonPath(refundLine + ".description").value("Refund to card ending 4242"))
                .andExpect(jsonPath(refundLine + ".refundDisplayId").value(refund))
                .andExpect(jsonPath(refundLine + ".stripeRefundId").value(stripeRefundId))
                .andExpect(jsonPath(refundLine + ".stripeEventId").value(eventId));
    }

    // --- cancel-refund AC9, T7: a cancelled policy keeps its term, says its cancel day, and lists its credits ---

    @Test
    void aCancelledPolicyShowsItsCancelDayAndACreditLinePerCoverAfterTheCharges() throws Exception {
        long brokerId = jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1").query(Long.class).single();
        CancelPolicyService.Cancelled saved = canceller.cancel(juniper.policyId(),
                new CancelPolicyCommand(LocalDate.of(2026, 12, 1), "Closing the bakery."), brokerId);
        String base = "$.ledger[?(@.displayId == '" + saved.creditDisplayIds().get(0) + "')]";
        String extra = "$.ledger[?(@.displayId == '" + saved.creditDisplayIds().get(1) + "')]";

        mvc.perform(get("/api/broker/policies/" + juniper.policyId()).cookie(broker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.start").value("2026-09-01"))
                .andExpect(jsonPath("$.end").value("2027-09-01"))
                .andExpect(jsonPath("$.cancelledOn").value("2026-12-01"))
                .andExpect(jsonPath("$.balanceCents").value(193000 - 90082 - 54800))
                .andExpect(jsonPath("$.ledger.length()").value(4))
                .andExpect(jsonPath("$.ledger[0].type").value("CHARGE"))
                .andExpect(jsonPath("$.ledger[0].reversedByDisplayId").doesNotExist())
                .andExpect(jsonPath("$.ledger[2].displayId").value(saved.creditDisplayIds().get(0)))
                .andExpect(jsonPath(base + ".type").value("CREDIT"))
                .andExpect(jsonPath(base + ".description").value("Base cover, unused days"))
                .andExpect(jsonPath(base + ".amountCents").value(-90082))
                .andExpect(jsonPath(base + ".calcAnnualCents").value(120000))
                .andExpect(jsonPath(base + ".calcDays").value(274))
                .andExpect(jsonPath(base + ".calcTermDays").value(365))
                .andExpect(jsonPath(base + ".coverFrom").value("2026-12-01"))
                .andExpect(jsonPath(base + ".policyTransactionDisplayId").value(saved.transactionDisplayId()))
                .andExpect(jsonPath(extra + ".description").value("Extra cover, unused days"))
                .andExpect(jsonPath(extra + ".amountCents").value(-54800))
                .andExpect(jsonPath("$.transactions[?(@.type == 'CANCEL')].effectiveDate").value("2026-12-01"))
                .andExpect(jsonPath("$.transactions[?(@.type == 'CANCEL')].reason").value("Closing the bakery."));
    }

    @Test
    void anActivePolicyHasNoCancelDay() throws Exception {
        mvc.perform(get("/api/broker/policies/" + juniper.policyId()).cookie(broker))
                .andExpect(jsonPath("$.cancelledOn").doesNotExist());
    }
}
