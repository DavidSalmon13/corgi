package app.plainly.policy;

import app.plainly.common.NotFoundException;
import app.plainly.common.ValidationException;
import app.plainly.ledger.PostedEntry;
import app.plainly.pricing.ProRata;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Adds the product liability extra cover to an active policy part-way through its year, charged by
 * the day to the policy's end. {@link #preview} and {@link #add} share {@link #plan}, so the price
 * the broker saw is the price posted.
 */
@Service
public class AddCoverageService {

    static final String CANCELLED = "This policy is cancelled, so cover can't be added.";
    static final String ALREADY_HAS = "This policy already has product liability cover.";
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private final JdbcClient jdbc;

    public AddCoverageService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** What adding saved, with the ids the broker will see and the policy's new balance. */
    public record Added(long policyId, String transactionDisplayId, List<PostedEntry> entries, long totalCents,
            long balanceCents) {}

    /** The policy as adding cover needs it. */
    private record PolicyTerm(long id, String status, LocalDate start, LocalDate end, boolean hasProductLiability) {}

    /** Prices the extra cover without saving anything (the live preview on B4). */
    public AddCoveragePlan preview(long policyId, CoverKind kind, LocalDate startDate) {
        PolicyTerm policy = load(policyId, false);
        validate(policy, kind, startDate);
        return plan(policy, kind, startDate);
    }

    /** Adds the cover, its transaction and its charge, all or nothing. */
    public Added add(long policyId, AddCoverageCommand cmd, long brokerUserId) {
        throw new UnsupportedOperationException("not built yet (add-coverage T5)");
    }

    /** {@code lock} takes the policy row for the rest of the transaction, so two adds can't both pass validation. */
    private PolicyTerm load(long policyId, boolean lock) {
        return jdbc.sql("""
                select p.id, p.status, p.start_date, p.end_date,
                       exists (select 1 from coverages c where c.policy_id = p.id and c.kind = 'PRODUCT_LIABILITY')
                from policies p where p.id = ?""" + (lock ? " for update of p" : ""))
                .param(policyId)
                .query((rs, n) -> new PolicyTerm(rs.getLong(1), rs.getString(2), rs.getObject(3, LocalDate.class),
                        rs.getObject(4, LocalDate.class), rs.getBoolean(5)))
                .optional()
                .orElseThrow(() -> new NotFoundException("There's no policy with that id."));
    }

    /** One message per field (AC4). A cancelled policy has no field to fix, so its message stands alone. */
    private static void validate(PolicyTerm policy, CoverKind kind, LocalDate start) {
        if (!"ACTIVE".equals(policy.status())) {
            throw new ValidationException(Map.of("policy", CANCELLED));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        if (kind != CoverKind.PRODUCT_LIABILITY) {
            errors.put("kind", "Pick a cover to add.");
        } else if (policy.hasProductLiability()) {
            errors.put("kind", ALREADY_HAS);
        }
        if (start == null) {
            errors.put("startDate", "Pick the day the extra cover should start.");
        } else if (start.isBefore(policy.start())) {
            errors.put("startDate", "Extra cover can't start before the policy does (" + LONG.format(policy.start()) + ").");
        } else if (!start.isBefore(policy.end())) {
            errors.put("startDate", "Extra cover has to start before the policy ends (" + LONG.format(policy.end()) + ").");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /** Yearly price × days from the start to the policy's end ÷ days in the policy year, rounded once. */
    private static AddCoveragePlan plan(PolicyTerm policy, CoverKind kind, LocalDate start) {
        ProRata.Charge calc = ProRata.charge(kind.annualPremiumCents(), start, policy.end(), policy.start(), policy.end());
        return new AddCoveragePlan(policy.id(), kind, "Extra cover from " + SHORT.format(start), start, policy.end(),
                calc, calc.amountCents());
    }
}
