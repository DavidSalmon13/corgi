package app.plainly.statement;

import org.springframework.stereotype.Service;

/** Builds B5 "History" for the broker from the statement's rows, grouped by action. Not built yet (T3). */
@Service
public class HistoryService {

    private final StatementService statements;

    public HistoryService(StatementService statements) {
        this.statements = statements;
    }

    /** The history of a policy, one action per card, oldest first. */
    public History forPolicy(long policyId) {
        throw new UnsupportedOperationException("History is not built yet (T3)");
    }
}
