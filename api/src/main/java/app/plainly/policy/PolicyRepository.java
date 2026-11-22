package app.plainly.policy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL on {@code policies}. The list and detail reads join customers, transactions and payments for the screens. */
@Repository
public class PolicyRepository {

    private final JdbcClient jdbc;

    public PolicyRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Created(long id, String policyNumber) {}

    /** A policy row for the B1 list, before its balance is added from the ledger. */
    public record ListRow(long id, String customer, String owner, String number, String status, String lastType,
            LocalDate lastOn, boolean paymentWaiting) {}

    /** The policy and its customer for the detail view. */
    public record Head(String number, String status, LocalDate start, LocalDate end, PolicyViews.Customer customer) {}

    /** The policy's term and status, as a policy change needs it. */
    public record Term(long id, String status, LocalDate start, LocalDate end) {}

    /** Numbers the policy GL-&lt;start year&gt;-&lt;sequence&gt;. */
    public Created insert(long customerId, LocalDate start, LocalDate end) {
        return jdbc.sql("""
                insert into policies (policy_number, customer_id, start_date, end_date)
                values ('GL-' || ? || '-' || lpad(nextval('policy_number_seq')::text, 4, '0'), ?, ?, ?)
                returning id, policy_number""")
                .params(start.getYear(), customerId, start, end)
                .query((rs, n) -> new Created(rs.getLong("id"), rs.getString("policy_number"))).single();
    }

    /** All policies, most recently acted on first. */
    public List<ListRow> listRows() {
        return jdbc.sql("""
                select p.id, c.legal_name, c.owner_name, p.policy_number, p.status, last.type, last.recorded_on,
                       exists (select 1 from payments pay where pay.policy_id = p.id and pay.status = 'PROCESSING')
                from policies p
                join customers c on c.id = p.customer_id
                left join lateral (
                    select t.type, t.recorded_on from policy_transactions t
                    where t.policy_id = p.id order by t.id desc limit 1
                ) last on true
                order by last.recorded_on desc nulls last, p.id desc""")
                .query((rs, n) -> new ListRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getObject(7, LocalDate.class), rs.getBoolean(8)))
                .list();
    }

    public Optional<Head> findHead(long policyId) {
        return jdbc.sql("""
                select p.policy_number, p.status, p.start_date, p.end_date,
                       c.legal_name, c.business_description, c.owner_name, c.owner_email, u.signed_up_at
                from policies p join customers c on c.id = p.customer_id
                left join app_users u on u.customer_id = c.id and u.role = 'POLICYHOLDER'
                where p.id = ?""")
                .param(policyId)
                .query((rs, n) -> new Head(rs.getString(1), rs.getString(2), rs.getObject(3, LocalDate.class),
                        rs.getObject(4, LocalDate.class),
                        new PolicyViews.Customer(rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                                rs.getObject(9, OffsetDateTime.class))))
                .optional();
    }

    /** {@code lock} holds the row ({@code for update}) until the caller's transaction ends. */
    public Optional<Term> findTerm(long policyId, boolean lock) {
        return jdbc.sql("select id, status, start_date, end_date from policies where id = ?" + (lock ? " for update" : ""))
                .param(policyId)
                .query((rs, n) -> new Term(rs.getLong(1), rs.getString(2), rs.getObject(3, LocalDate.class),
                        rs.getObject(4, LocalDate.class)))
                .optional();
    }

    /** The newest policy of a customer (one policy per customer for now). */
    public Optional<Long> latestIdForCustomer(long customerId) {
        return jdbc.sql("select id from policies where customer_id = ? order by id desc limit 1")
                .param(customerId).query(Long.class).optional();
    }

    /** As {@link #latestIdForCustomer}, holding the row until the caller's transaction ends. */
    public Optional<Long> lockLatestIdForCustomer(long customerId) {
        return jdbc.sql("select id from policies where customer_id = ? order by id desc limit 1 for update")
                .param(customerId).query(Long.class).optional();
    }

    /** Holds the policy row until the caller's transaction ends; fails if there is no such policy. */
    public void lock(long policyId) {
        jdbc.sql("select id from policies where id = ? for update").param(policyId).query(Long.class).single();
    }

    /** True while a payment on this policy is with the bank (a read of payments). */
    public boolean hasPaymentProcessing(long policyId) {
        return jdbc.sql("select exists (select 1 from payments where policy_id = ? and status = 'PROCESSING')")
                .param(policyId).query(Boolean.class).single();
    }
}
