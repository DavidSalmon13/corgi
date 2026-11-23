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
    static final String ALREADY_HAS = "This policy already has product liability coverage.";
    static final String NO_KIND = "Pick a cover to add.";
    static final String NO_START = "Pick the endorsement's effective date.";
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private final PolicyRepository policies;
    private final CoverageRepository coverages;
    private final PolicyTransactionRepository transactions;
    private final LedgerPostingService ledger;
    private final LedgerQueryService balances;
    private final Today today;

    public AddCoverageService(PolicyRepository policies, CoverageRepository coverages, PolicyTransactionRepository transactions,
            LedgerPostingService ledger, LedgerQueryService balances, Today today) {
        this.policies = policies;
        this.coverages = coverages;
        this.transactions = transactions;
        this.ledger = ledger;
        this.balances = balances;
        this.today = today;
    }

    /** What adding saved, with the ids the broker will see and the policy's new balance. */
    public record Added(long policyId, String transactionDisplayId, List<PostedEntry> entries, long totalCents,
            long balanceCents) {}

    /** The policy as adding cover needs it. */
    private record PolicyTerm(long id, String status, LocalDate start, LocalDate end, boolean hasProductLiability,
            LocalDate productLiabilityEndedOn) {}

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

        PolicyTransactionRepository.Recorded tx = transactions.insert(policyId, "ADD_COVERAGE", plan.from(), recordedOn, brokerUserId, null);
        long coverageId = coverages.insert(policyId, plan.kind(), plan.calc().annualCents(), plan.from(), plan.to());
        List<PostedEntry> posted = ledger.post(policyId, tx.id(),
                List.of(NewEntry.charge(recordedOn, plan.label(), coverageId, plan.calc(), plan.from(), plan.to())));
        return new Added(policyId, tx.displayId(), posted, plan.totalCents(), balances.balance(policyId));
    }

    /**
     * {@code lock} takes the policy row for the rest of the transaction, so two adds can't both pass validation.
     * The cover check is a separate statement on purpose: under READ COMMITTED a statement reads the database as
     * it was when the statement began, so a check inside the locking select would miss a cover that the add we
     * waited for committed. Run after the lock is held, it sees that cover.
     */
    private PolicyTerm load(long policyId, boolean lock) {
        PolicyRepository.Term policy = policies.findTerm(policyId, lock)
                .orElseThrow(() -> new NotFoundException("There's no policy with that id."));
        var extra = coverages.findLive(policyId, CoverKind.PRODUCT_LIABILITY);
        return new PolicyTerm(policy.id(), policy.status(), policy.start(), policy.end(), extra.isPresent(),
                extra.map(CoverageRepository.Live::endedOn).orElse(null));
    }

    /**
     * One message per field (AC4). A cancelled policy, or one whose extra cover was removed (once per policy), has no
     * field to fix, so its message stands alone.
     */
    private static void validate(PolicyTerm policy, CoverKind kind, LocalDate start) {
        if (!"ACTIVE".equals(policy.status())) {
            throw new ValidationException(Map.of("policy", CANCELLED));
        }
        if (policy.productLiabilityEndedOn() != null) {
            throw new ValidationException(Map.of("policy", removed(policy.productLiabilityEndedOn())));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        if (kind != CoverKind.PRODUCT_LIABILITY) {
            errors.put("kind", NO_KIND);
        } else if (policy.hasProductLiability()) {
            errors.put("kind", ALREADY_HAS);
        }
        if (start == null) {
            errors.put("startDate", NO_START);
        } else if (start.isBefore(policy.start())) {
            errors.put("startDate", "The effective date can't be before the policy period starts (" + LONG.format(policy.start()) + ").");
        } else if (!start.isBefore(policy.end())) {
            errors.put("startDate", "The effective date has to be before the policy period ends (" + LONG.format(policy.end()) + ").");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /** Extra cover can be removed once per policy, so it can't come back (AC5). */
    static String removed(LocalDate endedOn) {
        return CoverKind.PRODUCT_LIABILITY.label() + " was removed from this policy on " + LONG.format(endedOn)
                + ", so it can't be added again.";
    }

    /** Yearly price × days from the start to the policy's end ÷ days in the policy year, rounded once. */
    private static AddCoveragePlan plan(PolicyTerm policy, CoverKind kind, LocalDate start) {
        ProRata.Charge calc = ProRata.charge(kind.annualPremiumCents(), start, policy.end(), policy.start(), policy.end());
        return new AddCoveragePlan(policy.id(), kind, "Product liability endorsement from " + SHORT.format(start), start, policy.end(),
                calc, calc.amountCents());
    }
}
