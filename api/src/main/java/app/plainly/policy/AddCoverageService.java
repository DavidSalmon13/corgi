package app.plainly.policy;

import app.plainly.ledger.PostedEntry;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/** Adds the product liability extra cover to an active policy part-way through its year. */
@Service
public class AddCoverageService {

    /** What adding saved, with the ids the broker will see and the policy's new balance. */
    public record Added(long policyId, String transactionDisplayId, List<PostedEntry> entries, long totalCents,
            long balanceCents) {}

    /** Prices the extra cover without saving anything. */
    public AddCoveragePlan preview(long policyId, CoverKind kind, LocalDate startDate) {
        throw new UnsupportedOperationException("not built yet (add-coverage T4)");
    }

    /** Adds the cover, its transaction and its charge, all or nothing. */
    public Added add(long policyId, AddCoverageCommand cmd, long brokerUserId) {
        throw new UnsupportedOperationException("not built yet (add-coverage T5)");
    }
}
