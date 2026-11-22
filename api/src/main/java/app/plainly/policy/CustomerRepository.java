package app.plainly.policy;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL on {@code customers}. */
@Repository
public class CustomerRepository {

    private final JdbcClient jdbc;

    public CustomerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** What Stripe needs to know about a customer, and the Stripe customer if one was made. */
    public record StripeDetails(String stripeCustomerId, String legalName, String ownerEmail) {}

    /** Throws DuplicateKeyException if another customer has this owner email. */
    public long insert(String legalName, String businessDescription, String ownerName, String ownerEmail) {
        return jdbc.sql("""
                insert into customers (legal_name, business_description, owner_name, owner_email)
                values (?, ?, ?, ?) returning id""")
                .params(legalName, businessDescription, ownerName, ownerEmail)
                .query(Long.class).single();
    }

    /** Compared case-insensitively. */
    public boolean ownerEmailExists(String email) {
        return jdbc.sql("select exists (select 1 from customers where lower(owner_email) = lower(?))")
                .param(email).query(Boolean.class).single();
    }

    /** Holds the customer row until the caller's transaction ends, so one Stripe customer is made at most. */
    public StripeDetails lockStripeDetails(long customerId) {
        return jdbc.sql("select stripe_customer_id, legal_name, owner_email from customers where id = ? for update")
                .param(customerId)
                .query((rs, n) -> new StripeDetails(rs.getString(1), rs.getString(2), rs.getString(3)))
                .single();
    }

    public void setStripeCustomerId(long customerId, String stripeCustomerId) {
        jdbc.sql("update customers set stripe_customer_id = ? where id = ?").params(stripeCustomerId, customerId).update();
    }
}
