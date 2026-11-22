package app.plainly.billing;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Money going back to a card. A refund is recorded PENDING with the change that owes it, sent to Stripe after that
 * change commits, and settled only by a signed webhook.
 */
@Service
public class RefundService {

    private final RefundRepository refunds;

    public RefundService(RefundRepository refunds) {
        this.refunds = refunds;
    }

    /** Per payment id, what has gone or is on its way back to its card; failed refunds don't count. */
    public Map<Long, Long> refundedPerPayment(long policyId) {
        Map<Long, Long> result = new HashMap<>();
        refunds.refundedPerPayment(policyId).forEach(r -> result.put(r.paymentId(), r.amountCents()));
        return result;
    }
}
