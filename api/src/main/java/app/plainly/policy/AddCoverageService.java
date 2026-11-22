package app.plainly.policy;

import app.plainly.common.NotFoundException;
import app.plainly.common.Today;
import app.plainly.common.ValidationException;
import app.plainly.ledger.LedgerPostingService;
import app.plainly.ledger.LedgerQueryService;
import app.plainly.ledger.NewEntry;
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
import org.springframework.transaction.annotation.Transactional;

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
    private final LedgerPostingService ledger;
    private final LedgerQueryService balances;
    private final Today today;

    public AddCoverageService(JdbcClient jdbc, LedgerPostingService ledger, LedgerQueryService balances, Today today) {
        this.jdbc = jdbc;
        this.ledger = ledger;
        this.balances = balances;
        this.today = today;
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

    /**
     * Adds the cover, its ADD_COVERAGE transaction and its one charge, all or nothing. The policy row
     * stays locked until commit, so a second add waits, then sees the cover and is told it's already on.
     */
    @Transactional
    public Added add(long policyId, AddCoverageCommand cmd, long brokerUserId) {
        PolicyTerm policy = load(policyId, true);
        validate(policy, cmd.kind(), cmd.startDate());
        AddCoveragePlan plan = plan(policy, cmd.kind(), cmd.startDate());
        LocalDate recordedOn = today.date();

        var tx = jdbc.sql("""
                insert into policy_transactions (policy_id, type, effective_date, recorded_on, created_by)
                values (?, 'ADD_COVERAGE', ?, ?, ?) returning id, display_id""")
                .params(policyId, plan.from(), recordedOn, brokerUserId)
                .query((rs, n) -> Map.entry(rs.getLong("id"), rs.getString("display_id"))).single();
        long coverageId = jdbc.sql("""
                insert into coverages (policy_id, kind, annual_premium_cents, effective_from, effective_to)
                values (?, ?, ?, ?, ?) returning id""")
                .params(policyId, plan.kind().name(), plan.calc().annualCents(), plan.from(), plan.to())
                .query(Long.class).single();
        List<PostedEntry> posted = ledger.post(policyId, tx.getKey(),
                List.of(NewEntry.charge(recordedOn, plan.label(), coverageId, plan.calc(), plan.from(), plan.to())));
        return new Added(policyId, tx.getValue(), posted, plan.totalCents(), balances.balance(policyId));
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
