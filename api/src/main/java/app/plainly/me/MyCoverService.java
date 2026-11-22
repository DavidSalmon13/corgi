package app.plainly.me;

import app.plainly.auth.SignedInUser;
import app.plainly.billing.PaymentService;
import app.plainly.billing.RefundListing;
import app.plainly.billing.StripeGateway;
import app.plainly.common.NotFoundException;
import app.plainly.ledger.EntryType;
import app.plainly.ledger.LedgerLine;
import app.plainly.policy.CancelPolicyPlan;
import app.plainly.policy.CoverKind;
import app.plainly.policy.PolicyService;
import app.plainly.policy.PolicyViews;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
        /** The policy was cancelled and its credit didn't cover the days already used, so the rest is still owed. */
        CANCELLATION,
        /** Another later change left money owing. */
        CHANGE,
        /** Paid up, or we owe them. */
        NOTHING_DUE
    }

    public record Business(String legalName, String description, String ownerName) {}

    /** {@code end} is the original term end; {@code cancelledOn} is the day all cover stopped, or null. */
    public record Policy(String policyNumber, String status, LocalDate start, LocalDate end, LocalDate cancelledOn) {}

    public record Broker(String name) {}

    /** Money we owe the owner that is going back to their card (refunds not yet settled, including ones being retried). */
    public record RefundWaiting(long amountCents, String cardLast4) {}

    /** One cover's unused days given back: annual price × days ÷ days in term, as a negative amount. */
    public record CancelCredit(CoverKind kind, long annualCents, long days, long amountCents) {}

    /**
     * How a cancellation was worked out, for P1's cancelled card and P6. {@code keptBackCents} is the part of the
     * credit that paid off what was still owed, and {@code keptBackFor} the change that left it owing (ISSUE,
     * ADD_COVERAGE, CORRECT). {@code refundStatus} is null when nothing goes back to the card, SUCCEEDED once Stripe
     * has confirmed every refund ({@code settledOn} is that day), and PENDING until then, including while a failed
     * refund waits for the broker to retry it.
     */
    public record Cancellation(LocalDate endsOn, String reason, long usedDays, long unusedDays, long termDays,
            List<CancelCredit> credits, long keptBackCents, String keptBackFor, long refundCents, String refundStatus,
            String cardLast4, LocalDate settledOn) {}

    /**
     * {@code latestChangeFrom}: the day the newest change after issuing takes effect, or null if there is none.
     * {@code latestChangeReason}: the broker's words for that change when it was a correction, shown as written.
     * {@code refundWaiting}: null unless a refund is on its way.
     */
    public record MyCover(Business business, Policy policy, List<PolicyViews.Coverage> covers, long amountDueCents,
            DueReason dueReason, LocalDate latestChangeFrom, String latestChangeReason, RefundWaiting refundWaiting,
            /** Null unless the policy is cancelled. */
            Cancellation cancellation,
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
                : "CANCEL".equals(latest.type()) ? DueReason.CANCELLATION
                : DueReason.CHANGE;
        LocalDate latestChangeFrom = onlyIssued ? null : latest.effectiveDate();
        String latestChangeReason = onlyIssued ? null : latest.reason();
        String broker = policies.issuedBy(p.policyId()).orElse("Your broker");
        return new MyCover(
                new Business(p.customer().legalName(), p.customer().businessDescription(), p.customer().ownerName()),
                new Policy(p.policyNumber(), p.status(), p.start(), p.end(), p.cancelledOn()),
                p.coverages(), due, reason, latestChangeFrom, latestChangeReason, refundWaiting(p.refunds()),
                cancellation(p),
                new Broker(broker), p.today(),
                payments.latestSubmitted(p.policyId()).orElse(null), stripe.enabled());
    }

    /**
     * Reads the cancellation back from what it posted: the CANCEL transaction, its CREDIT lines and the refunds it
     * owed. Nothing is stored twice, so P6 always matches the statement.
     */
    private static Cancellation cancellation(PolicyViews.Detail p) {
        List<PolicyViews.Transaction> txs = p.transactions();
        int at = -1;
        for (int i = txs.size() - 1; i >= 0 && at < 0; i--) {
            if ("CANCEL".equals(txs.get(i).type())) {
                at = i;
            }
        }
        if (p.cancelledOn() == null || at < 0) {
            return null;
        }
        PolicyViews.Transaction cancel = txs.get(at);
        String ptx = cancel.displayId();
        List<CancelCredit> credits = new ArrayList<>();
        Map<Long, CoverKind> kinds = new HashMap<>();
        p.coverages().forEach(c -> kinds.put(c.id(), c.kind()));
        for (LedgerLine l : p.ledger()) {
            if (l.type() == EntryType.CREDIT && ptx.equals(l.policyTransactionDisplayId())) {
                credits.add(new CancelCredit(kinds.get(l.coverageId()), l.calcAnnualCents(), l.calcDays(),
                        l.amountCents()));
            }
        }
        long credit = -credits.stream().mapToLong(CancelCredit::amountCents).sum();

        List<RefundListing> refunds = p.refunds().stream().filter(r -> ptx.equals(r.policyTransactionDisplayId()))
                .toList();
        long refundCents = refunds.stream().mapToLong(RefundListing::amountCents).sum();
        boolean allSettled = !refunds.isEmpty() && refunds.stream().allMatch(r -> "SUCCEEDED".equals(r.status()));
        String status = refunds.isEmpty() ? null : allSettled ? "SUCCEEDED" : "PENDING";
        String card = refunds.stream().max(Comparator.comparingLong(RefundListing::amountCents))
                .map(RefundListing::cardLast4).orElse(null);
        Set<String> refundIds = refunds.stream().map(RefundListing::displayId).collect(Collectors.toSet());
        LocalDate settledOn = !allSettled ? null : p.ledger().stream()
                .filter(l -> l.type() == EntryType.REFUND && refundIds.contains(l.refundDisplayId()))
                .map(LedgerLine::entryDate).max(Comparator.naturalOrder()).orElse(null);

        long keptBack = Math.max(0, credit - refundCents);
        List<LedgerLine> before = p.ledger().stream().takeWhile(l -> !ptx.equals(l.policyTransactionDisplayId()))
                .toList();
        String keptBackFor = keptBack > 0 ? CancelPolicyPlan.soleCauseOfBalance(before, txs.subList(0, at)) : null;
        long used = ChronoUnit.DAYS.between(p.start(), p.cancelledOn());
        long term = ChronoUnit.DAYS.between(p.start(), p.end());
        return new Cancellation(p.cancelledOn(), cancel.reason(), used, term - used, term, List.copyOf(credits),
                keptBack, keptBackFor, refundCents, status, card, settledOn);
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
