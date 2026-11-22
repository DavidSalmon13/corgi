package app.plainly.policy;

import app.plainly.billing.PaymentService;
import app.plainly.billing.RefundService;
import app.plainly.common.NotFoundException;
import app.plainly.common.ValidationException;
import app.plainly.ledger.LedgerQueryService;
import app.plainly.ledger.LiveCharge;
import app.plainly.pricing.ProRata;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

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
    private final LedgerQueryService ledger;
    private final PaymentService payments;
    private final RefundService refunds;

    public CorrectCoverageService(PolicyRepository policies, CoverageRepository coverages, LedgerQueryService ledger,
            PaymentService payments, RefundService refunds) {
        this.policies = policies;
        this.coverages = coverages;
        this.ledger = ledger;
        this.payments = payments;
        this.refunds = refunds;
    }

    /** What saving did, with the ids the broker will see and the policy's new balance. */
    public record Corrected(long policyId, String transactionDisplayId, String undoDisplayId, String rebillDisplayId,
            long netCents, long balanceCents, List<RefundStarted> refunds) {}

    /** A refund recorded with the correction. It is sent to Stripe after commit and settles only by webhook. */
    public record RefundStarted(String displayId, long paymentId, long amountCents) {}

    /** Prices the correction without saving anything (the live preview on B4). The reason isn't needed yet. */
    public CorrectionPlan preview(long policyId, LocalDate startDate) {
        Loaded loaded = load(policyId, false);
        validate(loaded, startDate, null, false);
        return plan(loaded, startDate);
    }

    /** Saves the correction all or nothing, and records any refund it owes. */
    public Corrected correct(long policyId, CorrectCoverageCommand cmd, long brokerUserId) {
        throw new UnsupportedOperationException("not built yet (T8)");
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
        List<CorrectionPlan.PlannedRefund> planned = balanceAfter < 0
                ? allocate(-balanceAfter, payments.collected(policy.id()), refunds.refundedPerPayment(policy.id()))
                : List.of();
        return new CorrectionPlan(policy.id(), cover.id(), cover.from(), newStart, undo, rebill, net, balanceAfter,
                planned);
    }

    /**
     * Splits what we owe over the payments that took money, newest first, each capped at what it collected less
     * what has already gone (or is going) back from it. Anything left over stays as a credit on the balance.
     */
    static List<CorrectionPlan.PlannedRefund> allocate(long owedCents, List<PaymentService.Collected> newestFirst,
            Map<Long, Long> alreadyRefunded) {
        List<CorrectionPlan.PlannedRefund> planned = new ArrayList<>();
        long left = owedCents;
        for (PaymentService.Collected p : newestFirst) {
            if (left <= 0) {
                break;
            }
            long room = p.amountCents() - alreadyRefunded.getOrDefault(p.paymentId(), 0L);
            long take = Math.min(left, room);
            if (take > 0) {
                planned.add(new CorrectionPlan.PlannedRefund(p.paymentId(), p.displayId(), p.cardLast4(), take));
                left -= take;
            }
        }
        return List.copyOf(planned);
    }

    private static String label(LocalDate start) {
        return "Extra cover from " + SHORT.format(start);
    }
}
