package app.plainly.me;

import app.plainly.auth.SignedInUser;
import app.plainly.common.NotFoundException;
import app.plainly.policy.PolicyService;
import app.plainly.policy.PolicyViews;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * "Your policies": every policy the signed-in owner holds, and the ownership check every per-policy read and payment
 * goes through. Each policy is paid on its own; money never moves between them.
 */
@Service
public class MyPoliciesService {

    private final PolicyService policies;

    public MyPoliciesService(PolicyService policies) {
        this.policies = policies;
    }

    /**
     * One policy card. {@code balanceCents} is negative while a refund is on its way ({@code refundWaiting});
     * {@code replaces}/{@code replacedBy} link a rewrite and the cancelled policy it replaced (null when none).
     */
    public record Row(long policyId, String policyNumber, LocalDate start, LocalDate end, String status,
            long balanceCents, boolean paymentWaiting, MyCoverService.RefundWaiting refundWaiting,
            PolicyViews.PolicyLink replaces, PolicyViews.PolicyLink replacedBy) {}

    /** {@code totalDueCents} adds up positive balances only: a refund on one policy never pays another. */
    public record MyPolicies(List<Row> policies, long totalDueCents) {}

    /** Newest first. An owner with no policy yet gets an empty list. */
    public MyPolicies list(SignedInUser me) {
        List<Row> rows = policies.listForCustomer(customerId(me)).stream()
                .map(p -> new Row(p.policyId(), p.policyNumber(), p.start(), p.end(), p.status(), p.balanceCents(),
                        p.paymentWaiting(), MyCoverService.refundWaiting(p.refunds()), p.replaces(), p.replacedBy()))
                .toList();
        long totalDue = rows.stream().mapToLong(Row::balanceCents).filter(b -> b > 0).sum();
        return new MyPolicies(rows, totalDue);
    }

    /** {@code policyId} when the owner holds it, else NotFoundException (404), the same for a policy that doesn't exist. */
    public long ownedPolicy(SignedInUser me, long policyId) {
        return policies.requireOwned(customerId(me), policyId);
    }

    private static long customerId(SignedInUser me) {
        if (me.customerId() == null) {
            throw new NotFoundException("You don't have a policy yet.");
        }
        return me.customerId();
    }
}
