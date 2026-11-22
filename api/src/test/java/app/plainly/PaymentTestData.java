package app.plainly;

import app.plainly.policy.IssuePolicyCommand;
import app.plainly.policy.IssuePolicyService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** A signed-up owner with a policy and an open balance, ready to pay. Each call makes a new customer. */
public final class PaymentTestData {

    /** Base cover only, starting Sep 1, 2026: $1,200.00 for the year is due. */
    public static final long BASE_DUE_CENTS = 120000;

    public record Owner(long customerId, long policyId, String email, Cookie session) {}

    /** A payment the owner started: our row id and the Stripe PaymentIntent behind it. */
    public record Started(long paymentId, String intentId, long amountCents) {}

    private PaymentTestData() {}

    public static Owner ownerWithBalanceDue(MockMvc mvc, JdbcClient jdbc, IssuePolicyService issue, String business)
            throws Exception {
        long broker = jdbc.sql("select id from app_users where role = 'BROKER' order by id limit 1")
                .query(Long.class).single();
        String email = TestData.uniqueEmail("payer");
        issue.issue(new IssuePolicyCommand(business, "a test shop", "Pat Payer", email, LocalDate.of(2026, 9, 1), false),
                broker);
        long customerId = jdbc.sql("select id from customers where owner_email = ?").param(email)
                .query(Long.class).single();
        long policyId = jdbc.sql("select id from policies where customer_id = ?").param(customerId)
                .query(Long.class).single();
        return new Owner(customerId, policyId, email, AuthTestSupport.signUp(mvc, email));
    }

    /** Starts paying the balance due, as the pay page does, and returns the payment it opened. */
    public static Started startPayment(MockMvc mvc, JdbcClient jdbc, Owner owner) throws Exception {
        var result = mvc.perform(post("/api/me/payments").cookie(owner.session())).andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(result.getResponse().getStatus()).as("start payment: " + body).isEqualTo(200);
        long paymentId = ((Number) JsonPath.read(body, "$.paymentId")).longValue();
        long amount = ((Number) JsonPath.read(body, "$.amountCents")).longValue();
        String intentId = jdbc.sql("select stripe_payment_intent_id from payments where id = ?").param(paymentId)
                .query(String.class).single();
        return new Started(paymentId, intentId, amount);
    }

    public static String paymentStatus(JdbcClient jdbc, long paymentId) {
        return jdbc.sql("select status from payments where id = ?").param(paymentId).query(String.class).single();
    }

    /** Posts an extra charge of {@code cents} on the policy, so the owner owes money again after paying. */
    public static void addCharge(JdbcClient jdbc, long policyId, long cents) {
        long tx = TestData.transaction(jdbc, policyId, "ADD_COVERAGE", LocalDate.of(2026, 11, 22));
        jdbc.sql("""
                insert into ledger_entries (policy_id, policy_transaction_id, entry_type, entry_date, description, amount_cents)
                values (?, ?, 'CHARGE', ?, 'Test extra charge', ?)""")
                .params(policyId, tx, LocalDate.of(2026, 11, 22), cents).update();
    }

    /** The policy's balance straight from the ledger: positive means the owner still owes it. */
    public static long balance(JdbcClient jdbc, long policyId) {
        return jdbc.sql("select coalesce(sum(amount_cents), 0) from ledger_entries where policy_id = ?")
                .param(policyId).query(Long.class).single();
    }
}
