package app.plainly.policy;

import app.plainly.billing.PaymentService;
import app.plainly.billing.PlannedRefund;
import app.plainly.billing.RefundService;
import app.plainly.billing.RefundStarted;
import app.plainly.common.NotFoundException;
import app.plainly.common.Today;
import app.plainly.common.ValidationException;
import app.plainly.ledger.LedgerPostingService;
import app.plainly.ledger.LedgerQueryService;
import app.plainly.ledger.LiveCharge;
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
 * Corrects when a policy's extra cover started. Nothing is edited: the live charge is undone to the cent, a corrected
 * charge is posted from the new start, and the cover row is replaced. A credit pays off any open balance first and the
 * rest is refunded to the card, latest payment first. {@link #preview} and {@link #correct} share one plan, so what
 * the broker saw is what is posted.
 */
@Service
public class CorrectCoverageService {

    static final String CANCELLED = "This policy is cancelled, so its cover can't be corrected.";
    static final String NO_EXTRA_COVER = "This policy has no extra cover to correct.";
    static final String PAYMENT_WAITING = "A payment is waiting for the bank. Correct this once it settles.";
    static final String SAME_START = "Extra cover already starts on that day.";
    static final String NO_START = "Pick the day the extra cover really started.";
    static final String NO_REASON = "Say why the start date is changing. The customer sees this.";
    static final String REASON_TOO_LONG = "Keep the reason under 500 characters.";
    static final int REASON_MAX = 500;
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private final PolicyRepository policies;
    private final CoverageRepository coverages;
    private final PolicyTransactionRepository transactions;
    private final LedgerPostingService poster;
    private final LedgerQueryService ledger;
    private final PaymentService payments;
    private final RefundService refunds;
    private final Today today;

    public CorrectCoverageService(PolicyRepository policies, CoverageRepository coverages,
            PolicyTransactionRepository transactions, LedgerPostingService poster, LedgerQueryService ledger,
            PaymentService payments, RefundService refunds, Today today) {
        this.policies = policies;
        this.coverages = coverages;
        this.transactions = transactions;
        this.poster = poster;
        this.ledger = ledger;
        this.payments = payments;
        this.refunds = refunds;
        this.today = today;
    }

    /** What saving did, with the ids the broker will see and the policy's new balance. */
    public record Corrected(long policyId, String transactionDisplayId, String undoDisplayId, String rebillDisplayId,
            long netCents, long balanceCents, List<RefundStarted> refunds) {}

    /** Prices the correction without saving anything (the live preview on B4). The reason isn't needed yet. */
    public CorrectionPlan preview(long policyId, LocalDate startDate) {
        Loaded loaded = load(policyId, false);
        validate(loaded, startDate, null, false);
        return plan(loaded, startDate);
    }

    /** Saves the correction all or nothing, and records any refund it owes. */
    @Transactional
    public Corrected correct(long policyId, CorrectCoverageCommand cmd, long brokerUserId) {
        Loaded loaded = load(policyId, true);
        validate(loaded, cmd.startDate(), cmd.reason(), true);
        CorrectionPlan plan = plan(loaded, cmd.startDate());
        // An old pay page must not pay the old amount. If the bank already has one, the correction waits.
        if (!payments.cancelOpenIntents(policyId)) {
            throw new ValidationException(Map.of("policy", PAYMENT_WAITING));
        }
        LocalDate recordedOn = today.date();

        PolicyTransactionRepository.Recorded tx = transactions.insert(policyId, "CORRECT", plan.newStart(), recordedOn,
                brokerUserId, cmd.reason().strip());
        CorrectionPlan.Rebill rebill = plan.rebill();
        long newCoverage = coverages.insert(policyId, CoverKind.PRODUCT_LIABILITY, rebill.calc().annualCents(),
                rebill.from(), rebill.to());
        coverages.markReplaced(plan.coverageId(), newCoverage);
        List<PostedEntry> posted = poster.post(policyId, tx.id(), List.of(
                NewEntry.reversal(recordedOn, "Undo: " + plan.undo().label(), plan.undo().entryId(),
                        plan.undo().amountCents()),
                NewEntry.charge(recordedOn, rebill.label(), newCoverage, rebill.calc(), rebill.from(), rebill.to())));

        List<RefundStarted> started = refunds.recordAndSendAfterCommit(policyId, tx.id(), plan.refunds());

        return new Corrected(policyId, tx.displayId(), posted.get(0).displayId(), posted.get(1).displayId(),
                plan.netCents(), ledger.balance(policyId), started);
    }

    /** The policy, its live extra cover and whether a payment is with the bank, read together. */
    private record Loaded(PolicyRepository.Term policy, CoverageRepository.Live cover, boolean paymentWaiting) {}

    /**
     * {@code lock} holds the policy row until commit, so two corrections can't both pass validation. The cover is
     * read in a separate statement after the lock, so it sees a correction the lock waited for (see
     * {@link AddCoverageService}).
     */
    private Loaded load(long policyId, boolean lock) {
        PolicyRepository.Term policy = policies.findTerm(policyId, lock)
                .orElseThrow(() -> new NotFoundException("There's no policy with that id."));
        CoverageRepository.Live cover = coverages.findLive(policyId, CoverKind.PRODUCT_LIABILITY).orElse(null);
        return new Loaded(policy, cover, policies.hasPaymentProcessing(policyId));
    }

    /**
     * A problem with the policy itself stands alone, since no field fixes it. Otherwise every field to fix is
     * reported at once, in form order. The preview doesn't need the reason yet.
     */
    private static void validate(Loaded loaded, LocalDate start, String reason, boolean saving) {
        String policyProblem = !"ACTIVE".equals(loaded.policy().status()) ? CANCELLED
                : loaded.cover() == null ? NO_EXTRA_COVER
                : loaded.paymentWaiting() ? PAYMENT_WAITING
                : null;
        if (policyProblem != null) {
            throw new ValidationException(Map.of("policy", policyProblem));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        PolicyRepository.Term policy = loaded.policy();
        if (start == null) {
            errors.put("startDate", NO_START);
        } else if (start.isBefore(policy.start())) {
            errors.put("startDate", "Extra cover can't start before the policy does (" + LONG.format(policy.start()) + ").");
        } else if (!start.isBefore(policy.end())) {
            errors.put("startDate", "Extra cover has to start before the policy ends (" + LONG.format(policy.end()) + ").");
        } else if (start.equals(loaded.cover().from())) {
            errors.put("startDate", SAME_START);
        }
        if (saving) {
            if (reason == null || reason.isBlank()) {
                errors.put("reason", NO_REASON);
            } else if (reason.strip().length() > REASON_MAX) {
                errors.put("reason", REASON_TOO_LONG);
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /**
     * Undo the cover's live charge to the cent, charge again from the new start to the policy's end, and work out
     * what comes back to the card: the credit pays off the open balance first, and only the rest is refunded.
     */
    private CorrectionPlan plan(Loaded loaded, LocalDate newStart) {
        PolicyRepository.Term policy = loaded.policy();
        CoverageRepository.Live cover = loaded.cover();
        LiveCharge live = ledger.liveChargeFor(cover.id())
                .orElseThrow(() -> new IllegalStateException("cover " + cover.id() + " has no live charge"));
        CorrectionPlan.Undo undo = new CorrectionPlan.Undo(live.entryId(), live.displayId(), label(cover.from()),
                live.calc(), -live.amountCents());
        ProRata.Charge calc = ProRata.charge(cover.annualPremiumCents(), newStart, policy.end(), policy.start(), policy.end());
        CorrectionPlan.Rebill rebill = new CorrectionPlan.Rebill(label(newStart), newStart, policy.end(), calc);

        long net = calc.amountCents() + undo.amountCents();
        long balanceAfter = ledger.balance(policy.id()) + net;
        List<PlannedRefund> planned = refunds.plan(policy.id(), balanceAfter);
        return new CorrectionPlan(policy.id(), cover.id(), cover.from(), newStart, undo, rebill, net, balanceAfter,
                planned);
    }

    private static String label(LocalDate start) {
        return "Extra cover from " + SHORT.format(start);
    }
}
