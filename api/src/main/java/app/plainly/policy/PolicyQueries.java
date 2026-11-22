package app.plainly.policy;

import app.plainly.common.NotFoundException;
import app.plainly.common.Today;
import app.plainly.ledger.LedgerQueryService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Reads policies for the screens. Balances always come from the ledger, never from a stored total. */
@Component
public class PolicyQueries {

    private final JdbcClient jdbc;
    private final LedgerQueryService ledger;
    private final Today today;

    public PolicyQueries(JdbcClient jdbc, LedgerQueryService ledger, Today today) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.today = today;
    }

    /** All policies, most recently acted on first. */
    public List<PolicyViews.ListRow> list() {
        record Row(long id, String customer, String owner, String number, String status, String lastType,
                LocalDate lastOn, boolean paymentWaiting) {}
        List<Row> rows = jdbc.sql("""
                select p.id, c.legal_name, c.owner_name, p.policy_number, p.status, last.type, last.recorded_on,
                       exists (select 1 from payments pay where pay.policy_id = p.id and pay.status = 'PROCESSING')
                from policies p
                join customers c on c.id = p.customer_id
                left join lateral (
                    select t.type, t.recorded_on from policy_transactions t
                    where t.policy_id = p.id order by t.id desc limit 1
                ) last on true
                order by last.recorded_on desc nulls last, p.id desc""")
                .query((rs, n) -> new Row(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getObject(7, LocalDate.class), rs.getBoolean(8)))
                .list();
        Map<Long, Long> balances = ledger.balances(rows.stream().map(Row::id).toList());
        return rows.stream()
                .map(r -> {
                    long balance = balances.get(r.id());
                    var last = r.lastType() == null ? null : new PolicyViews.LastAction(r.lastType(), r.lastOn());
                    return new PolicyViews.ListRow(r.id(), r.customer(), r.owner(), r.number(), r.status(), balance,
                            balance > 0, r.paymentWaiting(), last);
                })
                .toList();
    }

    public PolicyViews.Detail detail(long policyId) {
        return find(policyId).orElseThrow(() -> new NotFoundException("No policy " + policyId));
    }

    /** The newest policy of a customer (one policy per customer for now). */
    public Optional<PolicyViews.Detail> latestForCustomer(long customerId) {
        return jdbc.sql("select id from policies where customer_id = ? order by id desc limit 1")
                .param(customerId).query(Long.class).optional().flatMap(this::find);
    }

    private Optional<PolicyViews.Detail> find(long policyId) {
        record Head(String number, String status, LocalDate start, LocalDate end,
                PolicyViews.Customer customer) {}
        Optional<Head> head = jdbc.sql("""
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
        return head.map(h -> new PolicyViews.Detail(policyId, h.number(), h.status(), h.start(), h.end(), today.date(),
                h.customer(), ledger.balance(policyId), paymentWaiting(policyId), coverages(policyId), ledger.lines(policyId), transactions(policyId)));
    }

    private boolean paymentWaiting(long policyId) {
        return jdbc.sql("select exists (select 1 from payments where policy_id = ? and status = 'PROCESSING')")
                .param(policyId).query(Boolean.class).single();
    }

    private List<PolicyViews.Coverage> coverages(long policyId) {
        return jdbc.sql("""
                select id, kind, annual_premium_cents, effective_from, effective_to
                from coverages where policy_id = ? order by id""")
                .param(policyId)
                .query((rs, n) -> {
                    CoverKind kind = CoverKind.valueOf(rs.getString(2));
                    return new PolicyViews.Coverage(rs.getLong(1), kind, kind.label(), rs.getLong(3),
                            rs.getObject(4, LocalDate.class), rs.getObject(5, LocalDate.class));
                })
                .list();
    }

    private List<PolicyViews.Transaction> transactions(long policyId) {
        return jdbc.sql("""
                select display_id, type, effective_date, recorded_on
                from policy_transactions where policy_id = ? order by id""")
                .param(policyId)
                .query((rs, n) -> new PolicyViews.Transaction(rs.getString(1), rs.getString(2),
                        rs.getObject(3, LocalDate.class), rs.getObject(4, LocalDate.class)))
                .list();
    }
}
