package app.plainly.ledger;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way lines enter the ledger. Insert-only by design: there is no update or delete here,
 * and the database trigger (V2) refuses them anyway. Mistakes are fixed with a reversal.
 */
@Service
public class LedgerPostingService {

    private final LedgerRepository ledger;

    public LedgerPostingService(LedgerRepository ledger) {
        this.ledger = ledger;
    }

    /** Posts all lines or none. Joins the caller's transaction when there is one. */
    @Transactional
    public List<PostedEntry> post(long policyId, Long policyTransactionId, List<NewEntry> entries) {
        List<PostedEntry> posted = new ArrayList<>(entries.size());
        for (NewEntry e : entries) {
            posted.add(ledger.insert(policyId, policyTransactionId, check(policyId, policyTransactionId, e)));
        }
        return posted;
    }

    /** Refuses a line that breaks the ledger's rules; returns it as it should be stored. */
    private NewEntry check(long policyId, Long policyTransactionId, NewEntry e) {
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
            long paymentPolicy = ledger.policyOfPayment(e.paymentId())
                    .orElseThrow(() -> new IllegalArgumentException("no payment " + e.paymentId()));
            if (paymentPolicy != policyId || e.amountCents() > 0) {
                throw new IllegalArgumentException("a payment line must be a credit on its payment's own policy");
            }
        }
        if (e.type() == EntryType.REVERSAL) {
            if (e.reversesEntryId() == null) {
                throw new IllegalArgumentException("a reversal must name the line it undoes");
            }
            LedgerRepository.Original original = ledger.findOriginal(e.reversesEntryId())
                    .orElseThrow(() -> new IllegalArgumentException("no ledger line " + e.reversesEntryId()));
            if (original.policyId() != policyId || e.amountCents() != -original.amountCents()) {
                throw new IllegalArgumentException("a reversal must exactly undo a line on the same policy");
            }
            return e.withCoverage(original.coverageId());
        }
        if (e.type() == EntryType.REFUND) {
            if (e.refundId() == null || e.stripeEventId() == null || e.stripeEventId().isBlank()) {
                throw new IllegalArgumentException("a refund line must name its refund and the Stripe event that settled it");
            }
            LedgerRepository.RefundOf refund = ledger.refundOf(e.refundId())
                    .orElseThrow(() -> new IllegalArgumentException("no refund " + e.refundId()));
            if (refund.policyId() != policyId || e.amountCents() != refund.amountCents()) {
                throw new IllegalArgumentException("a refund line must match its refund, on the refund's own policy");
            }
        }
        return e;
    }
}
