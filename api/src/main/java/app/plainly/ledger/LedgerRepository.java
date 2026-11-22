package app.plainly.ledger;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * SQL on {@code ledger_entries}. Insert-only by design: there is no update or delete here, and the
 * database trigger (V2) refuses them anyway.
 */
@Repository
public class LedgerRepository {

    private final JdbcClient jdbc;

    public LedgerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The policy and amount of an existing line, which a reversal must undo exactly. */
    public record Original(long policyId, long amountCents) {}

    public PostedEntry insert(long policyId, Long policyTransactionId, NewEntry e) {
        return jdbc.sql("""
                insert into ledger_entries (policy_id, policy_transaction_id, coverage_id, entry_type, entry_date,
                    description, amount_cents, calc_annual_cents, calc_days, calc_term_days, cover_from, cover_to,
                    reverses_entry_id, payment_id, stripe_event_id)
                values (:policyId, :ptxId, :coverageId, :type, :entryDate, :description, :amount, :calcAnnual,
                    :calcDays, :calcTermDays, :coverFrom, :coverTo, :reverses, :paymentId, :stripeEventId)
                returning id, display_id""")
                .param("policyId", policyId)
                .param("ptxId", policyTransactionId)
                .param("coverageId", e.coverageId())
                .param("type", e.type().name())
                .param("entryDate", e.entryDate())
                .param("description", e.description())
                .param("amount", e.amountCents())
                .param("calcAnnual", e.calc() == null ? null : e.calc().annualCents())
                .param("calcDays", e.calc() == null ? null : Math.toIntExact(e.calc().days()))
                .param("calcTermDays", e.calc() == null ? null : Math.toIntExact(e.calc().termDays()))
                .param("coverFrom", e.coverFrom())
                .param("coverTo", e.coverTo())
                .param("reverses", e.reversesEntryId())
                .param("paymentId", e.paymentId())
                .param("stripeEventId", e.stripeEventId())
                .query((rs, n) -> new PostedEntry(rs.getLong("id"), rs.getString("display_id")))
                .single();
    }

    public Optional<Original> findOriginal(long entryId) {
        return jdbc.sql("select policy_id, amount_cents from ledger_entries where id = ?")
                .param(entryId)
                .query((rs, n) -> new Original(rs.getLong("policy_id"), rs.getLong("amount_cents")))
                .optional();
    }

    /** The policy a payment belongs to, so a payment line can only credit its own policy (a read of payments). */
    public Optional<Long> policyOfPayment(long paymentId) {
        return jdbc.sql("select policy_id from payments where id = ?").param(paymentId).query(Long.class).optional();
    }

    /** Lines oldest first with a running balance and the records each one links to. */
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

    public long balance(long policyId) {
        return jdbc.sql("select coalesce(sum(amount_cents), 0) from ledger_entries where policy_id = ?")
                .param(policyId).query(Long.class).single();
    }

    /** Balances of the policies that have lines; policies with none are absent. */
    public Map<Long, Long> balances(Collection<Long> policyIds) {
        Map<Long, Long> result = new HashMap<>();
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
