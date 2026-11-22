package app.plainly.billing;

import static app.plainly.PaymentTestData.BASE_DUE_CENTS;
import static app.plainly.PaymentTestData.paymentStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.plainly.AuthTestSupport;
import app.plainly.FakeStripeGateway;
import app.plainly.IntegrationTest;
import app.plainly.PaymentTestData;
import app.plainly.PaymentTestData.Owner;
import app.plainly.PaymentTestData.Started;
import app.plainly.StripeTestSupport;
import app.plainly.policy.IssuePolicyService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** A policyholder starts paying what they owe; the server decides the amount and never settles a payment itself. */
// covers AC2, AC3, AC7, AC12, AC13, T3
@IntegrationTest
@AutoConfigureMockMvc
class MyPaymentsTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcClient jdbc;
    @Autowired IssuePolicyService issue;
    @Autowired FakeStripeGateway stripe;

    private Owner owner(String business) throws Exception {
        return PaymentTestData.ownerWithBalanceDue(mvc, jdbc, issue, business);
    }

    private String stripeCustomerOf(Owner owner) {
        return jdbc.sql("select stripe_customer_id from customers where id = ?").param(owner.customerId())
                .query(String.class).single();
    }

    private void deliver(String body) throws Exception {
        mvc.perform(StripeTestSupport.delivery(body)).andExpect(status().isOk());
    }

    // --- AC2: the server sets the amount ---

    @Test
    void startingAPaymentOpensAStripeIntentForTheBalanceDue() throws Exception {
        Owner owner = owner("Start Pay Bakery LLC");

        mvc.perform(post("/api/me/payments").cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").isNumber())
                .andExpect(jsonPath("$.amountCents").value(BASE_DUE_CENTS))
                .andExpect(jsonPath("$.clientSecret").isString())
                .andExpect(jsonPath("$.publishableKey").value(StripeTestSupport.PUBLISHABLE_KEY))
                .andExpect(jsonPath("$.savedCard").doesNotExist());

        String customer = stripeCustomerOf(owner);
        assertThat(customer).startsWith("cus_fake_");
        List<FakeStripeGateway.IntentCall> calls = stripe.intentsFor(customer);
        assertThat(calls).hasSize(1);
        FakeStripeGateway.IntentCall call = calls.get(0);
        long paymentId = jdbc.sql("select id from payments where stripe_payment_intent_id = ?").param(call.intentId())
                .query(Long.class).single();
        assertThat(call.amountCents()).isEqualTo(BASE_DUE_CENTS);
        assertThat(call.idempotencyKey()).isEqualTo("pay-" + paymentId);
        assertThat(call.metadata()).containsEntry("payment_id", String.valueOf(paymentId))
                .containsEntry("policy_id", String.valueOf(owner.policyId()));
        assertThat(paymentStatus(jdbc, paymentId)).isEqualTo("OPEN");
    }

    @Test
    void anAmountSentByTheBrowserIsIgnored() throws Exception {
        Owner owner = owner("Cheeky Amount Bakery LLC");

        mvc.perform(post("/api/me/payments").cookie(owner.session())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amountCents\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountCents").value(BASE_DUE_CENTS));
    }

    @Test
    void openingThePayPageAgainReusesTheSameUnpaidIntent() throws Exception {
        Owner owner = owner("Reopen Pay Bakery LLC");

        Started first = PaymentTestData.startPayment(mvc, jdbc, owner);
        Started second = PaymentTestData.startPayment(mvc, jdbc, owner);

        assertThat(second.paymentId()).isEqualTo(first.paymentId());
        assertThat(stripe.intentsFor(stripeCustomerOf(owner))).hasSize(1);
    }

    @Test
    void aNewAmountDueGetsANewIntentButTheSameStripeCustomer() throws Exception {
        Owner owner = owner("New Amount Bakery LLC");
        Started first = PaymentTestData.startPayment(mvc, jdbc, owner);
        String customer = stripeCustomerOf(owner);

        PaymentTestData.addCharge(jdbc, owner.policyId(), 1400);
        Started second = PaymentTestData.startPayment(mvc, jdbc, owner);

        assertThat(second.paymentId()).isNotEqualTo(first.paymentId());
        assertThat(second.amountCents()).isEqualTo(BASE_DUE_CENTS + 1400);
        assertThat(stripeCustomerOf(owner)).isEqualTo(customer);
        assertThat(stripe.intentsFor(customer)).hasSize(2);
    }

    @Test
    void thereIsNothingToPayWhenNothingIsDue() throws Exception {
        Owner owner = owner("Nothing Due Bakery LLC");
        Started p = PaymentTestData.startPayment(mvc, jdbc, owner);
        deliver(StripeTestSupport.chargeSucceeded(StripeTestSupport.newEventId(), p.intentId(), p.amountCents(), "4242", "pm_nd"));

        mvc.perform(post("/api/me/payments").cookie(owner.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("nothing_due"));
    }

    @Test
    void theBrowsersConfirmNeverSettlesAPayment() throws Exception {
        Owner owner = owner("Browser Says Paid Bakery LLC");
        Started p = PaymentTestData.startPayment(mvc, jdbc, owner);

        mvc.perform(get("/api/me/payments/" + p.paymentId()).cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
        assertThat(PaymentTestData.balance(jdbc, owner.policyId())).isEqualTo(BASE_DUE_CENTS);
    }

    // --- AC7: no double pay ---

    @Test
    void aSecondPaymentIsRefusedWhileTheFirstIsOnItsWay() throws Exception {
        Owner owner = owner("Double Pay Bakery LLC");
        Started p = PaymentTestData.startPayment(mvc, jdbc, owner);
        deliver(StripeTestSupport.intentProcessing(StripeTestSupport.newEventId(), p.intentId(), p.amountCents()));

        mvc.perform(post("/api/me/payments").cookie(owner.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("payment_in_flight"))
                .andExpect(jsonPath("$.message").value("Your last payment is still on its way."));
    }

    @Test
    void afterADeclineTheOwnerCanTryAgainWithAFreshIntent() throws Exception {
        Owner owner = owner("Try Again Bakery LLC");
        Started p = PaymentTestData.startPayment(mvc, jdbc, owner);
        deliver(StripeTestSupport.chargeFailed(StripeTestSupport.newEventId(), p.intentId(), p.amountCents(), "0002", "Your card was declined."));

        Started retry = PaymentTestData.startPayment(mvc, jdbc, owner);

        assertThat(retry.paymentId()).isNotEqualTo(p.paymentId());
        assertThat(retry.amountCents()).isEqualTo(BASE_DUE_CENTS);
        assertThat(paymentStatus(jdbc, retry.paymentId())).isEqualTo("OPEN");
    }

    // --- AC4: the pay page follows the payment until Stripe settles it ---

    @Test
    void thePayPageSeesTheStatusStripeReported() throws Exception {
        Owner owner = owner("Poll Status Bakery LLC");
        Started p = PaymentTestData.startPayment(mvc, jdbc, owner);
        deliver(StripeTestSupport.chargeSucceeded(StripeTestSupport.newEventId(), p.intentId(), p.amountCents(), "4242", "pm_poll"));

        mvc.perform(get("/api/me/payments/" + p.paymentId()).cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(p.paymentId()))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.amountCents").value(BASE_DUE_CENTS))
                .andExpect(jsonPath("$.cardLast4").value("4242"))
                .andExpect(jsonPath("$.amountDueCents").value(0));
    }

    // --- AC3: the card used last time ---

    @Test
    void aCardThatPaidBeforeIsOfferedAndCanPayWithoutCardDetails() throws Exception {
        Owner owner = owner("Saved Card Bakery LLC");
        Started first = PaymentTestData.startPayment(mvc, jdbc, owner);
        deliver(StripeTestSupport.chargeSucceeded(StripeTestSupport.newEventId(), first.intentId(), first.amountCents(), "4242", "pm_saved"));
        PaymentTestData.addCharge(jdbc, owner.policyId(), 1400);

        String body = mvc.perform(post("/api/me/payments").cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountCents").value(1400))
                .andExpect(jsonPath("$.savedCard.last4").value("4242"))
                .andExpect(jsonPath("$.savedCard.brand").value("visa"))
                .andReturn().getResponse().getContentAsString();
        long secondId = ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.paymentId")).longValue();
        String secondIntent = jdbc.sql("select stripe_payment_intent_id from payments where id = ?").param(secondId)
                .query(String.class).single();

        mvc.perform(post("/api/me/payments/" + secondId + "/saved-card").cookie(owner.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("processing"));

        assertThat(stripe.confirmsFor(secondIntent)).containsExactly(new FakeStripeGateway.ConfirmCall(secondIntent, "pm_saved"));
        assertThat(paymentStatus(jdbc, secondId)).as("only a webhook settles it").isEqualTo("OPEN");
    }

    @Test
    void withNoCardOnFileTheSavedCardOptionIsRefused() throws Exception {
        Owner owner = owner("No Card Bakery LLC");
        Started p = PaymentTestData.startPayment(mvc, jdbc, owner);

        mvc.perform(post("/api/me/payments/" + p.paymentId() + "/saved-card").cookie(owner.session()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("no_saved_card"));
    }

    // --- AC12: only your own payments ---

    @Test
    void anOwnerCantSeeOrPayWithSomeoneElsesPayment() throws Exception {
        Owner a = owner("Owner A Payments LLC");
        Owner b = owner("Owner B Payments LLC");
        Started aPayment = PaymentTestData.startPayment(mvc, jdbc, a);

        mvc.perform(get("/api/me/payments/" + aPayment.paymentId()).cookie(b.session()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/me/payments/" + aPayment.paymentId() + "/saved-card").cookie(b.session()))
                .andExpect(status().isNotFound());
    }

    @Test
    void paymentsAreForPolicyholdersOnly() throws Exception {
        mvc.perform(post("/api/me/payments").cookie(AuthTestSupport.signInAsBroker(mvc)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/me/payments")).andExpect(status().isUnauthorized());
    }

    // --- AC13: no keys, no payments ---

    @Test
    void withoutStripeKeysPaymentsAreSwitchedOffPlainly() throws Exception {
        Owner owner = owner("Keys Off Bakery LLC");
        stripe.setEnabled(false);
        try {
            mvc.perform(post("/api/me/payments").cookie(owner.session()))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("payments_off"));
            mvc.perform(get("/api/me/cover").cookie(owner.session()))
                    .andExpect(jsonPath("$.paymentsEnabled").value(false));
        } finally {
            stripe.setEnabled(true);
        }
    }
}
