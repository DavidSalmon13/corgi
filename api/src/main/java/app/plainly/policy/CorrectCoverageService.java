package app.plainly.policy;

import java.time.LocalDate;
import java.util.List;
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

    /** What saving did, with the ids the broker will see and the policy's new balance. */
    public record Corrected(long policyId, String transactionDisplayId, String undoDisplayId, String rebillDisplayId,
            long netCents, long balanceCents, List<RefundStarted> refunds) {}

    /** A refund recorded with the correction. It is sent to Stripe after commit and settles only by webhook. */
    public record RefundStarted(String displayId, long paymentId, long amountCents) {}

    /** Prices the correction without saving anything (the live preview on B4). The reason isn't needed yet. */
    public CorrectionPlan preview(long policyId, LocalDate startDate) {
        throw new UnsupportedOperationException("not built yet (T7)");
    }

    /** Saves the correction all or nothing, and records any refund it owes. */
    public Corrected correct(long policyId, CorrectCoverageCommand cmd, long brokerUserId) {
        throw new UnsupportedOperationException("not built yet (T8)");
    }
}
