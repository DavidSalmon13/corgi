package app.plainly.billing;

import static app.plainly.PaymentTestData.BASE_DUE_CENTS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.AuthTestSupport;
import app.plainly.IntegrationTest;
import app.plainly.PaymentTestData;
import app.plainly.PaymentTestData.Owner;
import app.plainly.PaymentTestData.Started;
import app.plainly.StripeTestSupport;
import app.plainly.policy.IssuePolicyService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** What each role is shown about a payment: the owner on P1, the broker on B3 and B1. */
// covers AC1, AC5, AC6, AC7, AC11, T3
@IntegrationTest
@AutoConfigureMockMvc
class PaymentViewsTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issue;

    private Owner owner(String business) throws Exception {
        return PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issue, business);
    }

    private void deliver(String body) throws Exception {
        mvc.perform(StripeTestSupport.delivery(body)).andExpect(status().isOk());
    }

    private Cookie broker() throws Exception {
        return AuthTestSupport.signInAsBroker(mvc);
    }

    // --- P1 (owner) ---

    @Test
    void beforePayingTheOwnerSeesWhatsDueAndThatPaymentsAreOn() throws Exception {
        Owner owner = owner("Before Paying Bakery LLC");

        mvc.perform(get("/api/me/cover").cookie(owner.session()))
                .andExpect(jsonPath("$.amountDueCents").value(BASE_DUE_CENTS))
                .andExpect(jsonPath("$.paymentsEnabled").value(true))
                .andExpect(jsonPath("$.payment").doesNotExist());
    }

    @Test
    void anUnsubmittedPayPageIsNotShownAsAPayment() throws Exception {
        Owner owner = owner("Unsubmitted Bakery LLC");
        PaymentTestData.startPayment(mvc, jdbc, owner);

        mvc.perform(get("/api/me/cover").cookie(owner.session()))
                .andExpect(jsonPath("$.payment").doesNotExist());
    }

    @Test
    void whileTheBankDecidesTheOwnerSeesItWaiting() throws Exception {
        Owner owner = owner("Owner Waiting Bakery LLC");
        Started p = PaymentTestData.startPayment(mvc, jdbc, owner);
        deliver(StripeTestSupport.intentProcessing(StripeTestSupport.newEventId(), p.intentId(), p.amountCents()));

        mvc.perform(get("/api/me/cover").cookie(owner.session()))
                .andExpect(jsonPath("$.amountDueCents").value(BASE_DUE_CENTS))
                .andExpect(jsonPath("$.payment.status").value("PROCESSING"))
                .andExpect(jsonPath("$.payment.amountCents").value(BASE_DUE_CENTS));
    }

    @Test
    void afterADeclineTheOwnerSeesItDidntGoThrough() throws Exception {
        Owner owner = owner("Owner Declined Bakery LLC");
        Started p = PaymentTestData.startPayment(mvc, jdbc, owner);
        deliver(StripeTestSupport.chargeFailed(StripeTestSupport.newEventId(), p.intentId(), p.amountCents(), "0002", "Your card was declined."));

        mvc.perform(get("/api/me/cover").cookie(owner.session()))
                .andExpect(jsonPath("$.amountDueCents").value(BASE_DUE_CENTS))
                .andExpect(jsonPath("$.payment.status").value("FAILED"))
                .andExpect(jsonPath("$.payment.cardLast4").value("0002"));
    }

    @Test
    void afterPayingTheOwnerIsAllPaidUp() throws Exception {
        Owner owner = owner("Owner Paid Bakery LLC");
        Started p = PaymentTestData.startPayment(mvc, jdbc, owner);
        deliver(StripeTestSupport.chargeSucceeded(StripeTestSupport.newEventId(), p.intentId(), p.amountCents(), "4242", "pm_v"));

        mvc.perform(get("/api/me/cover").cookie(owner.session()))
                .andExpect(jsonPath("$.amountDueCents").value(0))
                .andExpect(jsonPath("$.dueReason").value("NOTHING_DUE"))
                .andExpect(jsonPath("$.payment.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.payment.cardLast4").value("4242"));
    }

    // --- B3 / B1 (broker) ---

    @Test
    void theBrokerLedgerTracesEachPaymentLineToStripe() throws Exception {
        Owner owner = owner("Broker Trace Bakery LLC");
        Started p = PaymentTestData.startPayment(mvc, jdbc, owner);
        String failEvent = StripeTestSupport.newEventId();
        deliver(StripeTestSupport.chargeFailed(failEvent, p.intentId(), p.amountCents(), "0002", "Your card was declined."));
        Started retry = PaymentTestData.startPayment(mvc, jdbc, owner);
        String paidEvent = StripeTestSupport.newEventId();
        deliver(StripeTestSupport.chargeSucceeded(paidEvent, retry.intentId(), retry.amountCents(), "4242", "pm_b"));

        mvc.perform(get("/api/broker/policies/" + owner.policyId()).cookie(broker()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceCents").value(0))
                .andExpect(jsonPath("$.paymentWaiting").value(false))
                .andExpect(jsonPath("$.ledger[1].type").value("PAYMENT"))
                .andExpect(jsonPath("$.ledger[1].amountCents").value(0))
                .andExpect(jsonPath("$.ledger[1].description").value("Payment attempt, card ending 0002"))
                .andExpect(jsonPath("$.ledger[1].stripePaymentIntentId").value(p.intentId()))
                .andExpect(jsonPath("$.ledger[1].stripeEventId").value(failEvent))
                .andExpect(jsonPath("$.ledger[2].amountCents").value(-BASE_DUE_CENTS))
                .andExpect(jsonPath("$.ledger[2].stripePaymentIntentId").value(retry.intentId()))
                .andExpect(jsonPath("$.ledger[2].stripeEventId").value(paidEvent))
                .andExpect(jsonPath("$.ledger[2].balanceAfterCents").value(0));
    }

    @Test
    void theBrokerSeesWhenAPaymentIsWaitingForTheBank() throws Exception {
        Owner owner = owner("Broker Waiting Bakery LLC");
        Started p = PaymentTestData.startPayment(mvc, jdbc, owner);
        deliver(StripeTestSupport.intentProcessing(StripeTestSupport.newEventId(), p.intentId(), p.amountCents()));
        Cookie broker = broker();

        mvc.perform(get("/api/broker/policies/" + owner.policyId()).cookie(broker))
                .andExpect(jsonPath("$.paymentWaiting").value(true))
                .andExpect(jsonPath("$.balanceCents").value(BASE_DUE_CENTS));
        mvc.perform(get("/api/broker/policies").cookie(broker))
                .andExpect(jsonPath("$[?(@.policyId == %d)].paymentWaiting", owner.policyId()).value(true));
    }
}
