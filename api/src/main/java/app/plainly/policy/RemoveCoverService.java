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
import app.plainly.ledger.NewEntry;
import app.plainly.ledger.PostedEntry;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Removes a policy's extra cover from a chosen day, a mid-term endorsement. Nothing is edited: one credit line gives
 * back the extra cover's unused days and the cover row gets its end day. The credit pays off anything still owed
 * first and the rest goes back to the card, latest payment first, sent after commit and settled only by webhook.
 * {@link #preview} and {@link #remove} share one plan, so what the broker saw is what is posted.
 */
@Service
public class RemoveCoverService {

    static final String CANCELLED = "This policy is cancelled.";
    static final String NO_EXTRA_COVER = "This policy has no product liability to remove.";
    static final String PAYMENT_WAITING = "A payment is waiting for the bank. Remove this once it settles.";
    static final String NO_DATE = "Pick the day product liability ends.";
    static final String NO_REASON = "Say why product liability is being removed. The customer sees this.";
    static final String REASON_TOO_LONG = "Keep the reason under 500 characters.";
    static final int REASON_MAX = 500;
    static final String TRANSACTION_TYPE = "REMOVE_COVERAGE";
    private static final CoverKind EXTRA = CoverKind.PRODUCT_LIABILITY;
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private final PolicyRepository policies;
    private final CoverageRepository coverages;
    private final PolicyTransactionRepository transactions;
    private final LedgerPostingService poster;
    private final LedgerQueryService ledger;
    private final PaymentService payments;
    private final RefundService refunds;
    private final Today today;

    public RemoveCoverService(PolicyRepository policies, CoverageRepository coverages,
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
    public record Removed(long policyId, LocalDate endDate, String transactionDisplayId, String creditDisplayId,
            long balanceCents, long refundTotalCents, List<RefundStarted> refunds) {}

    /** Prices the removal without saving anything (the live preview on B4). The reason isn't needed yet. */
    public RemoveCoverPlan preview(long policyId, LocalDate endDate) {
        Loaded loaded = load(policyId, false);
        validate(loaded, endDate, null, false);
        return plan(loaded, endDate);
    }

    /** Saves the removal all or nothing, and records any return premium it owes. */
    @Transactional
    public Removed remove(long policyId, RemoveCoverCommand cmd, long brokerUserId) {
        Loaded loaded = load(policyId, true);
        validate(loaded, cmd.endDate(), cmd.reason(), true);
        RemoveCoverPlan plan = plan(loaded, cmd.endDate());
        // An old pay page must not pay the old amount. If the bank already has one, the removal waits.
        if (!payments.cancelOpenIntents(policyId)) {
            throw new ValidationException(Map.of("policy", PAYMENT_WAITING));
        }
        LocalDate recordedOn = today.date();
        CancelPolicyPlan.Credit c = plan.credit();

        PolicyTransactionRepository.Recorded tx = transactions.insert(policyId, TRANSACTION_TYPE, plan.endDate(),
                recordedOn, brokerUserId, cmd.reason().strip());
        List<PostedEntry> posted = poster.post(policyId, tx.id(),
                List.of(NewEntry.credit(recordedOn, c.label(), c.coverageId(), c.calc(), c.from(), c.to())));
        if (!coverages.markEnded(plan.coverageId(), plan.endDate())) {
            throw new IllegalStateException("cover " + plan.coverageId() + " was ended while the policy was locked");
        }
        List<RefundStarted> started = refunds.recordAndSendAfterCommit(policyId, tx.id(), plan.refunds());

        return new Removed(policyId, plan.endDate(), tx.displayId(), posted.getFirst().displayId(),
                ledger.balance(policyId), plan.refundTotalCents(), started);
    }

    /** The policy, its extra cover (if any) and whether a payment is with the bank, read together. */
    private record Loaded(PolicyRepository.Term policy, Optional<CoverageRepository.Live> cover,
            boolean paymentWaiting) {}

    /**
     * {@code lock} holds the policy row until commit, so two removals can't both pass validation: the second waits,
     * then reads the cover as already ended. The cover is read after the lock, so it sees a correction it waited for.
     */
    private Loaded load(long policyId, boolean lock) {
        PolicyRepository.Term policy = policies.findTerm(policyId, lock)
                .orElseThrow(() -> new NotFoundException("There's no policy with that id."));
        return new Loaded(policy, coverages.findLive(policyId, EXTRA), policies.hasPaymentProcessing(policyId));
    }

    /**
     * A problem with the policy itself stands alone, since no field fixes it. Otherwise every field to fix is
     * reported at once, in form order. The preview doesn't need the reason yet.
     */
    private static void validate(Loaded loaded, LocalDate endDate, String reason, boolean saving) {
        String policyProblem = policyProblem(loaded);
        if (policyProblem != null) {
            throw new ValidationException(Map.of("policy", policyProblem));
        }
        CoverageRepository.Live cover = loaded.cover().orElseThrow();
        PolicyRepository.Term policy = loaded.policy();
        Map<String, String> errors = new LinkedHashMap<>();
        if (endDate == null) {
            errors.put("endDate", NO_DATE);
        } else if (endDate.isBefore(cover.from())) {
            errors.put("endDate", EXTRA.label() + " can't end before its effective date ("
                    + LONG.format(cover.from()) + ").");
        } else if (!endDate.isBefore(policy.end())) {
            errors.put("endDate", "Pick a day before the policy period ends (" + LONG.format(policy.end()) + ").");
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

    /** Why nothing on the form could make this removal valid, or null. B4 shows the same reasons on its tab (AC1). */
    private static String policyProblem(Loaded loaded) {
        if (!"ACTIVE".equals(loaded.policy().status())) {
            return CANCELLED;
        }
        if (loaded.cover().isEmpty()) {
            return NO_EXTRA_COVER;
        }
        LocalDate endedOn = loaded.cover().get().endedOn();
        if (endedOn != null) {
            return removedOn(endedOn);
        }
        return loaded.paymentWaiting() ? PAYMENT_WAITING : null;
    }

    /** "Product liability was removed on Dec 1, 2026." Once per policy (AC5). */
    static String removedOn(LocalDate endedOn) {
        return EXTRA.label() + " was removed on " + LONG.format(endedOn) + ".";
    }

    /** Credit the cover's unused days, then work out what is kept back, still owed, or refunded. */
    private RemoveCoverPlan plan(Loaded loaded, LocalDate endDate) {
        PolicyRepository.Term policy = loaded.policy();
        CoverageRepository.Live cover = loaded.cover().orElseThrow();
        CancelPolicyPlan.Credit credit = RemoveCoverPlan.creditFor(policy, cover, endDate);
        long balanceBefore = ledger.balance(policy.id());
        List<PlannedRefund> planned = refunds.plan(policy.id(), balanceBefore + credit.amountCents());
        String owedFor = CancelPolicyPlan.soleCauseOfBalance(ledger.lines(policy.id()),
                transactions.listForPolicy(policy.id()));
        return RemoveCoverPlan.of(policy, cover, endDate, credit, balanceBefore, owedFor, planned);
    }
}
