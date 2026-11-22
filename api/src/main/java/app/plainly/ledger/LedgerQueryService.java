package app.plainly.ledger;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Read side of the ledger: lines oldest first with a running balance, and balances per policy. */
@Service
public class LedgerQueryService {

    private final LedgerRepository ledger;

    public LedgerQueryService(LedgerRepository ledger) {
        this.ledger = ledger;
    }

    public List<LedgerLine> lines(long policyId) {
        return ledger.lines(policyId);
    }

    /** What the customer owes on a policy now (negative = we owe them). */
    public long balance(long policyId) {
        return ledger.balance(policyId);
    }

    /** Balances for several policies at once; every requested id is present, 0 when it has no lines. */
    public Map<Long, Long> balances(Collection<Long> policyIds) {
        Map<Long, Long> result = new HashMap<>();
        policyIds.forEach(id -> result.put(id, 0L));
        if (!policyIds.isEmpty()) {
            result.putAll(ledger.balances(policyIds));
        }
        return result;
    }
}
