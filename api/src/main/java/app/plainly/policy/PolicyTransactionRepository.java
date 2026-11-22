package app.plainly.policy;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** SQL on {@code policy_transactions}: one row per broker action (ISSUE, ADD_COVERAGE, …). */
@Repository
public class PolicyTransactionRepository {

    private final JdbcClient jdbc;

    public PolicyTransactionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The new transaction's row id and the reader-facing id (PT-0001). */
    public record Recorded(long id, String displayId) {}

    /** {@code reason} is the broker's words for the customer, or null when the action needs none. */
    public Recorded insert(long policyId, String type, LocalDate effectiveDate, LocalDate recordedOn, long createdBy,
            String reason) {
        return jdbc.sql("""
                insert into policy_transactions (policy_id, type, effective_date, recorded_on, created_by, reason)
                values (?, ?, ?, ?, ?, ?) returning id, display_id""")
                .params(policyId, type, effectiveDate, recordedOn, createdBy, reason)
                .query((rs, n) -> new Recorded(rs.getLong("id"), rs.getString("display_id"))).single();
    }

    public List<PolicyViews.Transaction> listForPolicy(long policyId) {
        return jdbc.sql("""
                select display_id, type, effective_date, recorded_on, reason
                from policy_transactions where policy_id = ? order by id""")
                .param(policyId)
                .query((rs, n) -> new PolicyViews.Transaction(rs.getString(1), rs.getString(2),
                        rs.getObject(3, LocalDate.class), rs.getObject(4, LocalDate.class), rs.getString(5)))
                .list();
    }

    /** Who recorded one transaction: its reader-facing id and the person's display name. */
    public record Actor(String transactionDisplayId, String name) {}

    /** The person behind each of the policy's transactions, oldest first; ones with no recorded author are left out. */
    public List<Actor> actorsForPolicy(long policyId) {
        return jdbc.sql("""
                select t.display_id, u.display_name from policy_transactions t join app_users u on u.id = t.created_by
                where t.policy_id = ? order by t.id""")
                .param(policyId)
                .query((rs, n) -> new Actor(rs.getString(1), rs.getString(2)))
                .list();
    }

    /** The name of the broker who issued the policy (a read of app_users). */
    public Optional<String> issuedByName(long policyId) {
        return jdbc.sql("""
                select u.display_name from policy_transactions t join app_users u on u.id = t.created_by
                where t.policy_id = ? and t.type = 'ISSUE' order by t.id limit 1""")
                .param(policyId).query(String.class).optional();
    }
}
