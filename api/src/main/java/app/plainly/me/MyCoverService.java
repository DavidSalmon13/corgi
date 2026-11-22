package app.plainly.me;

import app.plainly.auth.SignedInUser;
import app.plainly.billing.PaymentService;
import app.plainly.billing.StripeGateway;
import app.plainly.common.NotFoundException;
import app.plainly.policy.PolicyService;
import app.plainly.policy.PolicyViews;
import java.time.LocalDate;
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
        /** A later change (such as corrected cover) left money owing. */
        CHANGE,
        /** Paid up, or we owe them. */
        NOTHING_DUE
    }

    public record Business(String legalName, String description, String ownerName) {}

    public record Policy(String policyNumber, String status, LocalDate start, LocalDate end) {}

    public record Broker(String name) {}

    /** {@code latestChangeFrom}: the day the newest change after issuing takes effect, or null if there is none. */
    public record MyCover(Business business, Policy policy, List<PolicyViews.Coverage> covers, long amountDueCents,
            DueReason dueReason, LocalDate latestChangeFrom, Broker broker, LocalDate today,
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
                : DueReason.CHANGE;
        LocalDate latestChangeFrom = onlyIssued ? null : latest.effectiveDate();
        String broker = policies.issuedBy(p.policyId()).orElse("Your broker");
        return new MyCover(
                new Business(p.customer().legalName(), p.customer().businessDescription(), p.customer().ownerName()),
                new Policy(p.policyNumber(), p.status(), p.start(), p.end()),
                p.coverages(), due, reason, latestChangeFrom, new Broker(broker), p.today(),
                payments.latestSubmitted(p.policyId()).orElse(null), stripe.enabled());
    }
}
