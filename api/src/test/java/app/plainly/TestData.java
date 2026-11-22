package app.plainly;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Minimal rows for tests that need a policy to hang ledger lines on. Never cleans up (append-only). */
public final class TestData {

    private TestData() {}

    /** An owner email no other test uses: owner emails are unique across customers (real-sign-in AC6). */
    public static String uniqueEmail(String name) {
        return name + "-" + UUID.randomUUID() + "@example.com";
    }

    public static long policy(JdbcClient jdbc, LocalDate start, LocalDate end) {
        long customerId = jdbc.sql("""
                insert into customers (legal_name, business_description, owner_name, owner_email)
                values ('Test Shop LLC', 'a test shop', 'Test Owner', ?) returning id""")
                .param(uniqueEmail("owner")).query(Long.class).single();
        return jdbc.sql("""
                insert into policies (policy_number, customer_id, start_date, end_date)
                values ('TEST-' || nextval('policy_number_seq'), ?, ?, ?) returning id""")
                .params(customerId, start, end).query(Long.class).single();
    }

    public static long transaction(JdbcClient jdbc, long policyId, String type, LocalDate effective) {
        return jdbc.sql("insert into policy_transactions (policy_id, type, effective_date) values (?, ?, ?) returning id")
                .params(policyId, type, effective).query(Long.class).single();
    }
}
