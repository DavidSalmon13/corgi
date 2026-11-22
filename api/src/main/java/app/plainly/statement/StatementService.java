package app.plainly.statement;

import org.springframework.stereotype.Service;

/** Builds P3 "Your statement" and its P4 traces for one policy, read-only and role-neutral. */
@Service
public class StatementService {

    /** The statement for a policy, every amount traced back to its change, payment or refund. */
    public Statement forPolicy(long policyId) {
        throw new UnsupportedOperationException("not built yet (statement T3)");
    }
}
