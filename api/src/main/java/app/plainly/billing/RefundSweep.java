package app.plainly.billing;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Makes sure no refund we owe gets stuck. A refund is sent right after the correction commits, but if the process
 * stopped first, or Stripe's answer never came back, it stays PENDING with no Stripe id. Every minute this asks
 * {@link RefundService#resendUnsent} to send those again (same attempt, same key, adopting any refund Stripe already
 * made). Off in tests, which call {@code resendUnsent} directly.
 */
@Component
@ConditionalOnProperty(name = "plainly.refunds.sweep", havingValue = "true", matchIfMissing = true)
class RefundSweep {

    private final RefundService refunds;
    private final Duration olderThan;

    RefundSweep(RefundService refunds, @Value("${plainly.refunds.sweep-after:PT2M}") Duration olderThan) {
        this.refunds = refunds;
        this.olderThan = olderThan;
    }

    @Scheduled(fixedDelayString = "${plainly.refunds.sweep-every:PT1M}", initialDelayString = "PT1M")
    void resendUnsent() {
        refunds.resendUnsent(olderThan);
    }
}
