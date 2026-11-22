package app.plainly;

import app.plainly.policy.IssuePolicyCommand;
import app.plainly.policy.IssuePolicyService;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/** A signed-up owner with a policy and an open balance, ready to pay. Each call makes a new customer. */
public final class PaymentTestData {

    /** Base cover only, starting Sep 1, 2026: $1,200.00 for the year is due. */
    public static final long BASE_DUE_CENTS = 120000;

    public record Owner(long customerId, long policyId, String email, Cookie session) {}

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

    /** The policy's balance straight from the ledger: positive means the owner still owes it. */
    public static long balance(JdbcClient jdbc, long policyId) {
        return jdbc.sql("select coalesce(sum(amount_cents), 0) from ledger_entries where policy_id = ?")
                .param(policyId).query(Long.class).single();
    }
}
