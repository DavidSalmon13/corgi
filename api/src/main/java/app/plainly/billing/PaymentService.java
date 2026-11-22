package app.plainly.billing;

import app.plainly.auth.SignedInUser;
import app.plainly.common.NotFoundException;
import app.plainly.ledger.LedgerQueryService;
import app.plainly.policy.CustomerRepository;
import app.plainly.policy.CustomerService;
import app.plainly.policy.PolicyService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A policyholder paying what they owe. The amount is always the ledger's balance due, never one the browser
 * sends, and nothing here marks a payment paid or failed: only {@link StripeWebhookService} does that.
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final PolicyService policies;
    private final CustomerService customers;
    private final StripeGateway stripe;
    private final LedgerQueryService ledger;

    public PaymentService(PaymentRepository payments, PolicyService policies, CustomerService customers,
            StripeGateway stripe, LedgerQueryService ledger) {
        this.payments = payments;
        this.policies = policies;
        this.customers = customers;
        this.stripe = stripe;
        this.ledger = ledger;
    }

    public record SavedCard(String brand, String last4) {}

    public record Started(long paymentId, long amountCents, String clientSecret, String publishableKey,
            SavedCard savedCard) {}

    public record PaymentView(long paymentId, String status, long amountCents, String cardLast4, Instant createdAt,
            long amountDueCents) {}

    public record SavedCardConfirm(String status, String clientSecret) {}

    /** A payment as P1 shows it. */
    /** A payment that took money: what a refund can go back against. */
    public record Collected(long paymentId, String displayId, String cardLast4, long amountCents) {}

    public record PaymentSummary(String status, long amountCents, String cardLast4, Instant createdAt) {}

    /**
     * Opens (or reopens) a payment for the balance due. The policy row is locked so two tabs can't open two
     * intents at once. Reopening the pay page reuses the open intent while the amount is unchanged.
     *
     * <p>Before asking for money, every earlier intent nobody has finished paying (OPEN, or declined) is checked
     * with Stripe. If the bank already has one, the payment is on its way even though its webhook hasn't arrived,
     * so a second is refused. The rest are cancelled at Stripe, so a pay page left open in another tab can't
     * charge the customer again. None of this changes a payment's status: only webhooks do.
     */
    @Transactional
    public Started start(SignedInUser me) {
        if (!stripe.enabled()) {
            throw StripeJavaGateway.paymentsOff();
        }
        long policyId = policies.lockLatestForCustomer(me.customerId())
                .orElseThrow(() -> new NotFoundException("You don't have a policy yet."));
        if (payments.anyProcessing(policyId)) {
            throw PaymentProblem.inFlight();
        }
        long due = Math.max(0, ledger.balance(policyId));
        if (due == 0) {
            throw new PaymentProblem(409, "nothing_due", "You're all paid up. There's nothing to pay right now.");
        }
        SavedCard saved = payments.latestCardOnFile(me.customerId()).map(c -> new SavedCard(c.brand(), c.last4())).orElse(null);

        Started reuse = null;
        for (PaymentRepository.Unfinished u : payments.unfinished(policyId)) {
            if (reuse == null && u.status().equals("OPEN") && u.amountCents() == due) {
                StripeGateway.IntentState state = stripe.intentState(u.intentId());
                if (state.moneyMoving()) {
                    throw PaymentProblem.inFlight();
                }
                if (!state.status().equals("canceled")) {
                    reuse = new Started(u.id(), due, state.clientSecret(), stripe.publishableKey(), saved);
                    continue;
                }
            }
            StripeGateway.IntentState after = stripe.cancelIfUnpaid(u.intentId());
            if (after.moneyMoving()) {
                throw PaymentProblem.inFlight();
            }
            payments.markStripeCanceled(u.id());
        }
        if (reuse != null) {
            return reuse;
        }

        String customer = stripeCustomer(me.customerId());
        long paymentId = payments.nextId();
        StripeGateway.CreatedIntent intent = stripe.createPaymentIntent(customer, due, "pay-" + paymentId,
                Map.of("payment_id", String.valueOf(paymentId), "policy_id", String.valueOf(policyId)));
        payments.insert(paymentId, policyId, me.id(), intent.id(), due);
        return new Started(paymentId, due, intent.clientSecret(), stripe.publishableKey(), saved);
    }

    /** Pays an open payment with the card that paid last time. Stripe's answer is passed on, not acted on. */
    @Transactional
    public SavedCardConfirm payWithSavedCard(SignedInUser me, long paymentId) {
        PaymentRepository.Owned payment = owned(me, paymentId);
        if (!payment.status().equals("OPEN")) {
            throw new PaymentProblem(409, "payment_not_open", "This payment has already been sent to your bank.");
        }
        // Lock the policy as start() does, so a replacement can't open while this one is being paid.
        policies.lock(payment.policyId());
        boolean replaced = payments.isStripeCanceled(payment.id());
        if (replaced || payment.amountCents() != Math.max(0, ledger.balance(payment.policyId()))) {
            throw new PaymentProblem(409, "payment_out_of_date",
                    "What you owe has changed since this page opened. Go back and start again.");
        }
        PaymentRepository.CardOnFile card = payments.latestCardOnFile(me.customerId()).orElseThrow(() -> new PaymentProblem(409,
                "no_saved_card", "There's no card saved from a past payment. Enter your card details instead."));
        StripeGateway.ConfirmedIntent confirmed = stripe.confirmWithSavedCard(payment.intentId(), card.paymentMethodId());
        return new SavedCardConfirm(confirmed.status(), confirmed.clientSecret());
    }

    public PaymentView view(SignedInUser me, long paymentId) {
        PaymentRepository.Owned p = owned(me, paymentId);
        long due = Math.max(0, ledger.balance(p.policyId()));
        return new PaymentView(p.id(), p.status(), p.amountCents(), p.cardLast4(), p.createdAt(), due);
    }

    /**
     * The latest payment the owner actually sent to their bank. An OPEN one only means the pay page was opened,
     * so it isn't shown.
     */
    /** Payments that took money on this policy, newest first. */
    public List<Collected> collected(long policyId) {
        return payments.succeededNewestFirst(policyId);
    }

    public Optional<PaymentSummary> latestSubmitted(long policyId) {
        return payments.latestSubmitted(policyId);
    }

    /** A payment on the signed-in owner's own policy; anyone else's is "not found", never "forbidden". */
    private PaymentRepository.Owned owned(SignedInUser me, long paymentId) {
        return payments.findOwned(paymentId, me.customerId())
                .orElseThrow(() -> new NotFoundException("We can't find that payment."));
    }

    /** Our customer's Stripe customer, created on their first payment. */
    private String stripeCustomer(long customerId) {
        CustomerRepository.StripeDetails row = customers.lockStripeDetails(customerId);
        if (row.stripeCustomerId() != null) {
            return row.stripeCustomerId();
        }
        String created = stripe.createCustomer(customerId, row.legalName(), row.ownerEmail());
        customers.saveStripeCustomerId(customerId, created);
        return created;
    }
}
