package app.plainly.me;

import app.plainly.auth.AuthFilter;
import app.plainly.auth.SignedInUser;
import app.plainly.billing.PaymentService;
import app.plainly.billing.StripeGateway;
import app.plainly.common.NotFoundException;
import app.plainly.policy.PolicyQueries;
import app.plainly.policy.PolicyViews;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

/** P1 "Your cover": what the signed-in owner is covered for, what they owe and why. Policyholder only. */
@RestController
class MyCoverController {

    private final PolicyQueries policies;
    private final JdbcClient jdbc;
    private final PaymentService payments;
    private final StripeGateway stripe;

    MyCoverController(PolicyQueries policies, JdbcClient jdbc, PaymentService payments, StripeGateway stripe) {
        this.policies = policies;
        this.jdbc = jdbc;
        this.payments = payments;
        this.stripe = stripe;
    }

    /** Why money is due, as a code the screen turns into a plain sentence. */
    enum DueReason {
        /** Only the first year's price has been charged. */
        FIRST_YEAR,
        /** A later change (added or corrected cover) left money owing. */
        CHANGE,
        /** Paid up, or we owe them. */
        NOTHING_DUE
    }

    record Business(String legalName, String description, String ownerName) {}

    record Policy(String policyNumber, String status, LocalDate start, LocalDate end) {}

    record Broker(String name) {}

    record MyCover(Business business, Policy policy, List<PolicyViews.Coverage> covers, long amountDueCents,
            DueReason dueReason, Broker broker, LocalDate today,
            /** The latest payment sent to the bank (null before the first); P1 shows waiting or declined from it. */
            PaymentService.PaymentSummary payment,
            /** False when Stripe isn't set up, so the pay button can say so instead of opening the card form. */
            boolean paymentsEnabled) {}

    @GetMapping("/api/me/cover")
    MyCover cover(@RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser me) {
        PolicyViews.Detail p = policies.latestForCustomer(me.customerId())
                .orElseThrow(() -> new NotFoundException("You don't have a policy yet."));
        long due = Math.max(0, p.balanceCents());
        boolean onlyIssued = p.transactions().stream().allMatch(t -> t.type().equals("ISSUE"));
        DueReason reason = due == 0 ? DueReason.NOTHING_DUE : onlyIssued ? DueReason.FIRST_YEAR : DueReason.CHANGE;
        String broker = jdbc.sql("""
                select u.display_name from policy_transactions t join app_users u on u.id = t.created_by
                where t.policy_id = ? and t.type = 'ISSUE' order by t.id limit 1""")
                .param(p.policyId()).query(String.class).optional().orElse("Your broker");
        return new MyCover(
                new Business(p.customer().legalName(), p.customer().businessDescription(), p.customer().ownerName()),
                new Policy(p.policyNumber(), p.status(), p.start(), p.end()),
                p.coverages(), due, reason, new Broker(broker), p.today(),
                payments.latestSubmitted(p.policyId()).orElse(null), stripe.enabled());
    }
}
