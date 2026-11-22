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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancels a policy from a chosen day. Nothing is edited: each live cover gets one credit line for its unused days,
 * every original charge stays as it was. The credit pays off anything still owed first and the rest goes back to the
 * card, latest payment first, sent after commit and settled only by webhook. {@link #preview} and {@link #cancel}
 * share one plan, so what the broker saw is what is posted.
 */
@Service
public class CancelPolicyService {

    static final String ALREADY_CANCELLED = "This policy is already cancelled.";
    static final String PAYMENT_WAITING = "A payment is waiting for the bank. Cancel once it settles.";
    static final String NO_DATE = "Pick the day cover ends.";
    static final String NO_REASON = "Say why the policy is being cancelled. The customer sees this.";
    static final String REASON_TOO_LONG = "Keep the reason under 500 characters.";
    static final int REASON_MAX = 500;
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private final PolicyRepository policies;
    private final CoverageRepository coverages;
    private final PolicyTransactionRepository transactions;
    private final LedgerPostingService poster;
    private final LedgerQueryService ledger;
    private final PaymentService payments;
    private final RefundService refunds;
    private final Today today;

    public CancelPolicyService(PolicyRepository policies, CoverageRepository coverages,
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
    public record Cancelled(long policyId, LocalDate cancelDate, String transactionDisplayId,
            List<String> creditDisplayIds, long balanceCents, long refundTotalCents, List<RefundStarted> refunds) {}

    /** Prices the cancellation without saving anything (the live preview on B4). The reason isn't needed yet. */
    public CancelPolicyPlan preview(long policyId, LocalDate cancelDate) {
        Loaded loaded = load(policyId, false);
        validate(loaded, cancelDate, null, false);
        return plan(loaded, cancelDate);
    }

    /** Saves the cancellation all or nothing, and records any refund it owes. */
    @Transactional
    public Cancelled cancel(long policyId, CancelPolicyCommand cmd, long brokerUserId) {
        Loaded loaded = load(policyId, true);
        validate(loaded, cmd.cancelDate(), cmd.reason(), true);
        CancelPolicyPlan plan = plan(loaded, cmd.cancelDate());
        // An old pay page must not pay the old amount. If the bank already has one, the cancellation waits.
        if (!payments.cancelOpenIntents(policyId)) {
            throw new ValidationException(Map.of("policy", PAYMENT_WAITING));
        }
        LocalDate recordedOn = today.date();

        PolicyTransactionRepository.Recorded tx = transactions.insert(policyId, "CANCEL", plan.cancelDate(),
                recordedOn, brokerUserId, cmd.reason().strip());
        List<PostedEntry> posted = poster.post(policyId, tx.id(), plan.credits().stream()
                .map(c -> NewEntry.credit(recordedOn, c.label(), c.coverageId(), c.calc(), c.from(), c.to()))
                .toList());
        if (!policies.markCancelled(policyId, plan.cancelDate())) {
            throw new IllegalStateException("policy " + policyId + " was cancelled while locked");
        }
        List<RefundStarted> started = refunds.recordAndSendAfterCommit(policyId, tx.id(), plan.refunds());

        return new Cancelled(policyId, plan.cancelDate(), tx.displayId(),
                posted.stream().map(PostedEntry::displayId).toList(), ledger.balance(policyId),
                plan.refundTotalCents(), started);
    }

    /** The policy, its live covers and whether a payment is with the bank, read together. */
    private record Loaded(PolicyRepository.Term policy, List<CoverageRepository.Live> covers,
            boolean paymentWaiting) {}

    /**
     * {@code lock} holds the policy row until commit, so two cancellations can't both pass validation: the second
     * waits, then reads the policy as CANCELLED. Covers are read after the lock, so they see a correction it waited
     * for.
     */
    private Loaded load(long policyId, boolean lock) {
        PolicyRepository.Term policy = policies.findTerm(policyId, lock)
                .orElseThrow(() -> new NotFoundException("There's no policy with that id."));
        return new Loaded(policy, coverages.listLive(policyId), policies.hasPaymentProcessing(policyId));
    }

    /**
     * A problem with the policy itself stands alone, since no field fixes it. Otherwise every field to fix is
     * reported at once, in form order. The preview doesn't need the reason yet.
     */
    private static void validate(Loaded loaded, LocalDate cancelDate, String reason, boolean saving) {
        String policyProblem = !"ACTIVE".equals(loaded.policy().status()) ? ALREADY_CANCELLED
                : loaded.paymentWaiting() ? PAYMENT_WAITING
                : null;
        if (policyProblem != null) {
            throw new ValidationException(Map.of("policy", policyProblem));
        }
        Map<String, String> errors = new LinkedHashMap<>();
        PolicyRepository.Term policy = loaded.policy();
        if (cancelDate == null) {
            errors.put("cancelDate", NO_DATE);
        } else if (cancelDate.isBefore(policy.start())) {
            errors.put("cancelDate", "Cover can't end before the policy starts (" + LONG.format(policy.start()) + ").");
        } else if (!cancelDate.isBefore(policy.end())) {
            errors.put("cancelDate", "Pick a day before the policy ends (" + LONG.format(policy.end()) + ").");
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

    /** Credit each cover's unused days, then work out what is kept back, still owed, or refunded. */
    private CancelPolicyPlan plan(Loaded loaded, LocalDate cancelDate) {
        PolicyRepository.Term policy = loaded.policy();
        List<CancelPolicyPlan.Credit> credits = CancelPolicyPlan.credits(policy, loaded.covers(), cancelDate);
        long balanceBefore = ledger.balance(policy.id());
        long credit = credits.stream().mapToLong(CancelPolicyPlan.Credit::amountCents).sum();
        List<PlannedRefund> planned = refunds.plan(policy.id(), balanceBefore + credit);
        return CancelPolicyPlan.of(policy, cancelDate, credits, balanceBefore, latestChange(policy.id()), planned);
    }

    /** The newest policy change before this one, which names what a balance still owed is for. */
    private String latestChange(long policyId) {
        List<PolicyViews.Transaction> all = transactions.listForPolicy(policyId);
        return all.isEmpty() ? null : all.getLast().type();
    }
}
