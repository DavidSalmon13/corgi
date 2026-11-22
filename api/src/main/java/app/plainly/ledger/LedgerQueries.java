package app.plainly.ledger;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Read side of the ledger: lines oldest first with a running balance, and balances per policy. */
@Component
public class LedgerQueries {

    private final JdbcClient jdbc;

    public LedgerQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<LedgerLine> lines(long policyId) {
        return jdbc.sql("""
                select le.id, le.display_id, le.entry_date, le.entry_type, le.description, le.amount_cents,
                       le.calc_annual_cents, le.calc_days, le.calc_term_days, le.cover_from, le.cover_to, le.coverage_id,
                       ptx.display_id as ptx_display_id,
                       rev_of.display_id as reverses_display_id,
                       rev_by.display_id as reversed_by_display_id,
                       pay.stripe_payment_intent_id, le.stripe_event_id,
                       sum(le.amount_cents) over (order by le.id) as balance_after
                from ledger_entries le
                left join policy_transactions ptx on ptx.id = le.policy_transaction_id
                left join ledger_entries rev_of on rev_of.id = le.reverses_entry_id
                left join ledger_entries rev_by on rev_by.reverses_entry_id = le.id
                left join payments pay on pay.id = le.payment_id
                where le.policy_id = ?
                order by le.id""")
                .param(policyId)
                .query((rs, n) -> new LedgerLine(
                        rs.getLong("id"),
                        rs.getString("display_id"),
                        date(rs.getDate("entry_date")),
                        EntryType.valueOf(rs.getString("entry_type")),
                        rs.getString("description"),
                        rs.getLong("amount_cents"),
                        rs.getObject("calc_annual_cents", Long.class),
                        rs.getObject("calc_days", Integer.class),
                        rs.getObject("calc_term_days", Integer.class),
                        date(rs.getDate("cover_from")),
                        date(rs.getDate("cover_to")),
                        rs.getObject("coverage_id", Long.class),
                        rs.getString("ptx_display_id"),
                        rs.getString("reverses_display_id"),
                        rs.getString("reversed_by_display_id"),
                        rs.getString("stripe_payment_intent_id"),
                        rs.getString("stripe_event_id"),
                        rs.getLong("balance_after")))
                .list();
    }

    /** What the customer owes on a policy now (negative = we owe them). */
    public long balance(long policyId) {
        return jdbc.sql("select coalesce(sum(amount_cents), 0) from ledger_entries where policy_id = ?")
                .param(policyId).query(Long.class).single();
    }

    /** Balances for several policies at once; every requested id is present, 0 when it has no lines. */
    public Map<Long, Long> balances(Collection<Long> policyIds) {
        Map<Long, Long> result = new HashMap<>();
        policyIds.forEach(id -> result.put(id, 0L));
        if (policyIds.isEmpty()) {
            return result;
        }
        jdbc.sql("select policy_id, sum(amount_cents) as balance from ledger_entries where policy_id in (:ids) group by policy_id")
                .param("ids", policyIds)
                .query((rs, n) -> Map.entry(rs.getLong("policy_id"), rs.getLong("balance")))
                .list()
                .forEach(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

    private static LocalDate date(Date d) {
        return d == null ? null : d.toLocalDate();
    }
}
