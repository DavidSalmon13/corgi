package app.plainly.policy;

import java.sql.ResultSet;
import java.sql.SQLException;
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
            LocalDate lastOn, boolean paymentWaiting, long customerId, PolicyViews.PolicyLink replaces,
            PolicyViews.PolicyLink replacedBy) {}

    /** The policy and its customer for the detail view. */
    public record Head(String number, String status, LocalDate start, LocalDate end, LocalDate cancelledOn,
            PolicyViews.Customer customer, long customerId, PolicyViews.PolicyLink replaces,
            PolicyViews.PolicyLink replacedBy) {}

    /** One of a customer's policies, for the policyholder's "Your policies" list, before its balance is added. */
    public record CustomerPolicy(long id, String number, LocalDate start, LocalDate end, String status,
            LocalDate cancelledOn, boolean paymentWaiting, PolicyViews.PolicyLink replaces,
            PolicyViews.PolicyLink replacedBy) {}

    /** What the rewrite rules read from the policy being replaced; {@code replacedByNumber} is null until rewritten. */
    public record ReplaceTarget(long id, long customerId, String number, String status, LocalDate cancelledOn,
            String replacedByNumber) {}

    /** The policy's term and status, as a policy change needs it. */
    /** {@code cancelledOn} is the day all cover stopped, set only when the policy is CANCELLED; {@code end} stays the
     * original term end, which prices every line. */
    public record Term(long id, String status, LocalDate start, LocalDate end, LocalDate cancelledOn) {}

    /** Reads a policy's links both ways. A policy is replaced at most once (unique index), so each join adds one row. */
    private static final String LINK_JOINS = """
            left join policies rp on rp.id = p.replaces_policy_id
            left join policies rb on rb.replaces_policy_id = p.id
            """;
    private static final String LINK_COLUMNS = "rp.id, rp.policy_number, rb.id, rb.policy_number";

    /** The link in columns {@code idCol} and {@code idCol + 1}, or null when there is none. */
    private static PolicyViews.PolicyLink link(ResultSet rs, int idCol) throws SQLException {
        long id = rs.getLong(idCol);
        return rs.wasNull() ? null : new PolicyViews.PolicyLink(id, rs.getString(idCol + 1));
    }

    /** Numbers the policy GL-&lt;start year&gt;-&lt;sequence&gt;. */
    public Created insert(long customerId, LocalDate start, LocalDate end) {
        return insert(customerId, start, end, null);
    }

    /**
     * As {@link #insert(long, LocalDate, LocalDate)}, for a rewrite of {@code replacesPolicyId} (null when it replaces
     * nothing). Throws DuplicateKeyException if that policy was already rewritten.
     */
    public Created insert(long customerId, LocalDate start, LocalDate end, Long replacesPolicyId) {
        return jdbc.sql("""
                insert into policies (policy_number, customer_id, start_date, end_date, replaces_policy_id)
                values ('GL-' || ? || '-' || lpad(nextval('policy_number_seq')::text, 4, '0'), ?, ?, ?, ?)
                returning id, policy_number""")
                .params(start.getYear(), customerId, start, end, replacesPolicyId)
                .query((rs, n) -> new Created(rs.getLong("id"), rs.getString("policy_number"))).single();
    }

    /** All policies, most recently acted on first. */
    public List<ListRow> listRows() {
        return jdbc.sql("""
                select p.id, c.legal_name, c.owner_name, p.policy_number, p.status, last.type, last.recorded_on,
                       exists (select 1 from payments pay where pay.policy_id = p.id and pay.status = 'PROCESSING'),
                       p.customer_id, %s
                from policies p
                join customers c on c.id = p.customer_id
                %s
                left join lateral (
                    select t.type, t.recorded_on from policy_transactions t
                    where t.policy_id = p.id order by t.id desc limit 1
                ) last on true
                order by last.recorded_on desc nulls last, p.id desc""".formatted(LINK_COLUMNS, LINK_JOINS))
                .query((rs, n) -> new ListRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getObject(7, LocalDate.class), rs.getBoolean(8), rs.getLong(9), link(rs, 10),
                        link(rs, 12)))
                .list();
    }

    public Optional<Head> findHead(long policyId) {
        return jdbc.sql("""
                select p.policy_number, p.status, p.start_date, p.end_date, p.cancelled_on,
                       c.legal_name, c.business_description, c.owner_name, c.owner_email, u.signed_up_at,
                       p.customer_id, %s
                from policies p join customers c on c.id = p.customer_id
                %s
                left join app_users u on u.customer_id = c.id and u.role = 'POLICYHOLDER'
                where p.id = ?""".formatted(LINK_COLUMNS, LINK_JOINS))
                .param(policyId)
                .query((rs, n) -> new Head(rs.getString(1), rs.getString(2), rs.getObject(3, LocalDate.class),
                        rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class),
                        new PolicyViews.Customer(rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9),
                                rs.getObject(10, OffsetDateTime.class)),
                        rs.getLong(11), link(rs, 12), link(rs, 14)))
                .optional();
    }

    /** A customer's policies, newest first, with their links both ways. */
    public List<CustomerPolicy> listForCustomer(long customerId) {
        return jdbc.sql("""
                select p.id, p.policy_number, p.start_date, p.end_date, p.status, p.cancelled_on,
                       exists (select 1 from payments pay where pay.policy_id = p.id and pay.status = 'PROCESSING'),
                       %s
                from policies p
                %s
                where p.customer_id = ?
                order by p.id desc""".formatted(LINK_COLUMNS, LINK_JOINS))
                .param(customerId)
                .query((rs, n) -> new CustomerPolicy(rs.getLong(1), rs.getString(2), rs.getObject(3, LocalDate.class),
                        rs.getObject(4, LocalDate.class), rs.getString(5), rs.getObject(6, LocalDate.class),
                        rs.getBoolean(7), link(rs, 8), link(rs, 10)))
                .list();
    }

    /** False for a policy that doesn't exist, too, so callers answer both the same way. */
    public boolean isOwnedBy(long policyId, long customerId) {
        return jdbc.sql("select exists (select 1 from policies where id = ? and customer_id = ?)")
                .params(policyId, customerId).query(Boolean.class).single();
    }

    /**
     * Holds the policy a rewrite would replace ({@code for update}) until the caller's transaction ends, then reads
     * what the rules need. A second rewrite waits on the lock, and its read (a new statement) sees the first one's
     * policy once that commits.
     */
    public Optional<ReplaceTarget> lockReplaceTarget(long policyId) {
        if (jdbc.sql("select id from policies where id = ? for update").param(policyId).query(Long.class).optional().isEmpty()) {
            return Optional.empty();
        }
        return jdbc.sql("""
                select p.id, p.customer_id, p.policy_number, p.status, p.cancelled_on, rb.policy_number
                from policies p left join policies rb on rb.replaces_policy_id = p.id
                where p.id = ?""")
                .param(policyId)
                .query((rs, n) -> new ReplaceTarget(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5, LocalDate.class), rs.getString(6)))
                .optional();
    }

    /** {@code lock} holds the row ({@code for update}) until the caller's transaction ends. */
    public Optional<Term> findTerm(long policyId, boolean lock) {
        return jdbc.sql("select id, status, start_date, end_date, cancelled_on from policies where id = ?"
                        + (lock ? " for update" : ""))
                .param(policyId)
                .query((rs, n) -> new Term(rs.getLong(1), rs.getString(2), rs.getObject(3, LocalDate.class),
                        rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class)))
                .optional();
    }

    /**
     * Marks an ACTIVE policy CANCELLED from {@code day} (the start of that day). Returns false if it was already
     * cancelled, so a second cancellation changes nothing. The term's end stays as it was.
     */
    public boolean markCancelled(long policyId, LocalDate day) {
        return jdbc.sql("update policies set status = 'CANCELLED', cancelled_on = ? where id = ? and status = 'ACTIVE'")
                .params(day, policyId).update() == 1;
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
