package app.plainly.policy;

import app.plainly.billing.RefundService;
import app.plainly.common.NotFoundException;
import app.plainly.common.Today;
import app.plainly.ledger.LedgerQueryService;
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
                            balance > 0, r.paymentWaiting(), last);
                })
                .toList();
    }

    public PolicyViews.Detail detail(long policyId) {
        return find(policyId).orElseThrow(() -> new NotFoundException("No policy " + policyId));
    }

    /** The newest policy of a customer (one policy per customer for now). */
    public Optional<PolicyViews.Detail> latestForCustomer(long customerId) {
        return policies.latestIdForCustomer(customerId).flatMap(this::find);
    }

    /** The customer's newest policy id, its row held until the caller's transaction ends. */
    public Optional<Long> lockLatestForCustomer(long customerId) {
        return policies.lockLatestIdForCustomer(customerId);
    }

    /** Holds the policy row until the caller's transaction ends. */
    public void lock(long policyId) {
        policies.lock(policyId);
    }

    /** The name of the broker who issued the policy, if known. */
    public Optional<String> issuedBy(long policyId) {
        return transactions.issuedByName(policyId);
    }

    private Optional<PolicyViews.Detail> find(long policyId) {
        return policies.findHead(policyId).map(h -> new PolicyViews.Detail(policyId, h.number(), h.status(), h.start(),
                h.end(), today.date(), h.customer(), ledger.balance(policyId), policies.hasPaymentProcessing(policyId),
                coverages.listForPolicy(policyId), ledger.lines(policyId), transactions.listForPolicy(policyId),
                refunds.listForPolicy(policyId)));
    }
}
