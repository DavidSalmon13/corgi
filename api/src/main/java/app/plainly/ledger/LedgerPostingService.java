package app.plainly.ledger;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way lines enter the ledger. Insert-only by design: there is no update or delete here,
 * and the database trigger (V2) refuses them anyway. Mistakes are fixed with a reversal.
 */
@Service
public class LedgerPostingService {

    private final JdbcClient jdbc;

    public LedgerPostingService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Posts all lines or none. Joins the caller's transaction when there is one. */
    @Transactional
    public List<PostedEntry> post(long policyId, Long policyTransactionId, List<NewEntry> entries) {
        List<PostedEntry> posted = new ArrayList<>(entries.size());
        for (NewEntry e : entries) {
            check(policyId, policyTransactionId, e);
            posted.add(jdbc.sql("""
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
                    .single());
        }
        return posted;
    }

    private void check(long policyId, Long policyTransactionId, NewEntry e) {
        boolean fromPolicyAction = e.type() == EntryType.CHARGE || e.type() == EntryType.REVERSAL;
        if (fromPolicyAction && policyTransactionId == null) {
            throw new IllegalArgumentException(e.type() + " lines must come from a policy transaction");
        }
        if (e.type() == EntryType.CHARGE && e.calc() == null) {
            throw new IllegalArgumentException("a charge must carry its pro-rata arithmetic");
        }
        if (e.type() == EntryType.PAYMENT) {
            if (e.paymentId() == null || e.stripeEventId() == null || e.stripeEventId().isBlank()) {
                throw new IllegalArgumentException("a payment line must name its payment and the Stripe event that settled it");
            }
            long paymentPolicy = jdbc.sql("select policy_id from payments where id = ?").param(e.paymentId())
                    .query(Long.class).optional()
                    .orElseThrow(() -> new IllegalArgumentException("no payment " + e.paymentId()));
            if (paymentPolicy != policyId || e.amountCents() > 0) {
                throw new IllegalArgumentException("a payment line must be a credit on its payment's own policy");
            }
        }
        if (e.type() == EntryType.REVERSAL) {
            if (e.reversesEntryId() == null) {
                throw new IllegalArgumentException("a reversal must name the line it undoes");
            }
            var original = jdbc.sql("select policy_id, amount_cents from ledger_entries where id = ?")
                    .param(e.reversesEntryId())
                    .query((rs, n) -> new long[] {rs.getLong("policy_id"), rs.getLong("amount_cents")})
                    .optional()
                    .orElseThrow(() -> new IllegalArgumentException("no ledger line " + e.reversesEntryId()));
            if (original[0] != policyId || e.amountCents() != -original[1]) {
                throw new IllegalArgumentException("a reversal must exactly undo a line on the same policy");
            }
        }
    }
}
