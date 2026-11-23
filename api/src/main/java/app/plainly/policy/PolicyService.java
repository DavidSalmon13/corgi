package app.plainly.policy;

import app.plainly.billing.RefundService;
import app.plainly.common.NotFoundException;
import app.plainly.common.Today;
import app.plainly.ledger.LedgerQueryService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Reads policies for the screens, and the policy lookups other modules need (billing locks the policy
 * it takes a payment for). Balances always come from the ledger, never from a stored total.
 */
@Service
public class PolicyService {

    private final PolicyRepository policies;
    private final CoverageRepository coverages;
    private final PolicyTransactionRepository transactions;
    private final LedgerQueryService ledger;
    private final RefundService refunds;
    private final Today today;

    public PolicyService(PolicyRepository policies, CoverageRepository coverages, PolicyTransactionRepository transactions,
            LedgerQueryService ledger, RefundService refunds, Today today) {
        this.policies = policies;
        this.coverages = coverages;
        this.transactions = transactions;
        this.ledger = ledger;
        this.refunds = refunds;
        this.today = today;
    }

    /** All policies, most recently acted on first. */
    public List<PolicyViews.ListRow> list() {
        List<PolicyRepository.ListRow> rows = policies.listRows();
        Map<Long, Long> balances = ledger.balances(rows.stream().map(PolicyRepository.ListRow::id).toList());
        return rows.stream()
                .map(r -> {
                    long balance = balances.get(r.id());
                    var last = r.lastType() == null ? null : new PolicyViews.LastAction(r.lastType(), r.lastOn());
                    return new PolicyViews.ListRow(r.id(), r.customer(), r.owner(), r.number(), r.status(), balance,
                            balance > 0, r.paymentWaiting(), last, r.customerId(), r.replaces(), r.replacedBy());
                })
                .toList();
    }

    public PolicyViews.Detail detail(long policyId) {
        return find(policyId).orElseThrow(() -> new NotFoundException("No policy " + policyId));
    }

    /** A customer's policies, newest first, each with its balance, refunds and rewrite links. */
    public List<PolicyViews.OwnedPolicy> listForCustomer(long customerId) {
        List<PolicyRepository.CustomerPolicy> rows = policies.listForCustomer(customerId);
        Map<Long, Long> balances = ledger.balances(rows.stream().map(PolicyRepository.CustomerPolicy::id).toList());
        return rows.stream()
                .map(p -> new PolicyViews.OwnedPolicy(p.id(), p.number(), p.start(), p.end(), p.status(), p.cancelledOn(),
                        balances.get(p.id()), p.paymentWaiting(), refunds.listForPolicy(p.id()), p.replaces(),
                        p.replacedBy()))
                .toList();
    }

    /**
     * {@code policyId} when the customer holds it. A policy that isn't theirs and one that doesn't exist both throw
     * the same NotFoundException, so a policyholder can't learn which policy ids exist (AC9).
     */
    public long requireOwned(long customerId, long policyId) {
        if (!policies.isOwnedBy(policyId, customerId)) {
            throw new NotFoundException("No policy " + policyId + " for you.");
        }
        return policyId;
    }

    /** Holds the policy row until the caller's transaction ends. */
    public void lock(long policyId) {
        policies.lock(policyId);
    }

    /**
     * Who made each change on the policy, by transaction id (PTX-…), oldest first. A change with no recorded author
     * has no entry, so callers fall back to "your broker".
     */
    public Map<String, String> actorNames(long policyId) {
        Map<String, String> names = new LinkedHashMap<>();
        transactions.actorsForPolicy(policyId)
                .forEach(a -> names.put(a.transactionDisplayId(), a.name()));
        return names;
    }

    /** Who to write to about a policy, and what it is: for emails (emails spec). */
    public record Contact(String ownerName, String ownerEmail, String legalName, String policyNumber, LocalDate start,
            LocalDate end) {}

    /** The policyholder's name and address and the policy's number and term; reads only the policy and customer. */
    public Contact contact(long policyId) {
        return policies.findHead(policyId)
                .map(h -> new Contact(h.customer().ownerName(), h.customer().ownerEmail(), h.customer().legalName(),
                        h.number(), h.start(), h.end()))
                .orElseThrow(() -> new NotFoundException("No policy " + policyId));
    }

    /** The name of the broker who issued the policy, if known. */
    public Optional<String> issuedBy(long policyId) {
        return transactions.issuedByName(policyId);
    }

    private Optional<PolicyViews.Detail> find(long policyId) {
        return policies.findHead(policyId).map(h -> new PolicyViews.Detail(policyId, h.number(), h.status(), h.start(),
                h.end(), h.cancelledOn(), today.date(), h.customer(), ledger.balance(policyId), policies.hasPaymentProcessing(policyId),
                coverages.listForPolicy(policyId), ledger.lines(policyId), transactions.listForPolicy(policyId),
                refunds.listForPolicy(policyId), h.customerId(), h.replaces(), h.replacedBy()));
    }
}
