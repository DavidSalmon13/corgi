package app.plainly.me;

import app.plainly.auth.SignedInUser;
import app.plainly.billing.PaymentService;
import app.plainly.billing.RefundListing;
import app.plainly.billing.StripeGateway;
import app.plainly.common.NotFoundException;
import app.plainly.policy.PolicyService;
import app.plainly.policy.PolicyViews;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** P1 "Your cover": what the signed-in owner is covered for, what they owe and why. */
@Service
public class MyCoverService {

    private final PolicyService policies;
    private final PaymentService payments;
    private final StripeGateway stripe;

    public MyCoverService(PolicyService policies, PaymentService payments, StripeGateway stripe) {
        this.policies = policies;
        this.payments = payments;
        this.stripe = stripe;
    }

    /** Why money is due, as a code the screen turns into a plain sentence. */
    public enum DueReason {
        /** Only the first year's price has been charged. */
        FIRST_YEAR,
        /** Extra cover was added part-way through the year, and that is the latest change. */
        EXTRA_COVER,
        /** The broker fixed when the extra cover started, and that is the latest change. */
        CORRECTION,
        /** Another later change left money owing. */
        CHANGE,
        /** Paid up, or we owe them. */
        NOTHING_DUE
    }

    public record Business(String legalName, String description, String ownerName) {}

    public record Policy(String policyNumber, String status, LocalDate start, LocalDate end) {}

    public record Broker(String name) {}

    /** Money we owe the owner that is going back to their card (refunds not yet settled, including ones being retried). */
    public record RefundWaiting(long amountCents, String cardLast4) {}

    /**
     * {@code latestChangeFrom}: the day the newest change after issuing takes effect, or null if there is none.
     * {@code latestChangeReason}: the broker's words for that change when it was a correction, shown as written.
     * {@code refundWaiting}: null unless a refund is on its way.
     */
    public record MyCover(Business business, Policy policy, List<PolicyViews.Coverage> covers, long amountDueCents,
            DueReason dueReason, LocalDate latestChangeFrom, String latestChangeReason, RefundWaiting refundWaiting,
            Broker broker, LocalDate today,
            /** The latest payment sent to the bank (null before the first); P1 shows waiting or declined from it. */
            PaymentService.PaymentSummary payment,
            /** False when Stripe isn't set up, so the pay button can say so instead of opening the card form. */
            boolean paymentsEnabled) {}

    public MyCover cover(SignedInUser me) {
        PolicyViews.Detail p = policies.latestForCustomer(me.customerId())
                .orElseThrow(() -> new NotFoundException("You don't have a policy yet."));
        long due = Math.max(0, p.balanceCents());
        PolicyViews.Transaction latest = p.transactions().isEmpty() ? null : p.transactions().getLast();
        boolean onlyIssued = p.transactions().stream().allMatch(t -> t.type().equals("ISSUE"));
        DueReason reason = due == 0 ? DueReason.NOTHING_DUE
                : onlyIssued ? DueReason.FIRST_YEAR
                : "ADD_COVERAGE".equals(latest.type()) ? DueReason.EXTRA_COVER
                : "CORRECT".equals(latest.type()) ? DueReason.CORRECTION
                : DueReason.CHANGE;
        LocalDate latestChangeFrom = onlyIssued ? null : latest.effectiveDate();
        String latestChangeReason = onlyIssued ? null : latest.reason();
        String broker = policies.issuedBy(p.policyId()).orElse("Your broker");
        return new MyCover(
                new Business(p.customer().legalName(), p.customer().businessDescription(), p.customer().ownerName()),
                new Policy(p.policyNumber(), p.status(), p.start(), p.end()),
                p.coverages(), due, reason, latestChangeFrom, latestChangeReason, refundWaiting(p.refunds()),
                new Broker(broker), p.today(),
                payments.latestSubmitted(p.policyId()).orElse(null), stripe.enabled());
    }

    /**
     * What we still owe back: every refund not yet settled, summed. A failed one counts too, because the broker
     * retries it and the customer is still owed the money (B3 design: "Maya still sees 'We owe you $14.00' until it
     * goes through"). A refund split across cards names the card that gets the most.
     */
    private static RefundWaiting refundWaiting(List<RefundListing> refunds) {
        List<RefundListing> pending = refunds.stream().filter(r -> !"SUCCEEDED".equals(r.status())).toList();
        if (pending.isEmpty()) {
            return null;
        }
        long total = pending.stream().mapToLong(RefundListing::amountCents).sum();
        String card = pending.stream().max(Comparator.comparingLong(RefundListing::amountCents))
                .map(RefundListing::cardLast4).orElse(null);
        return new RefundWaiting(total, card);
    }
}
