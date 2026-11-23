package app.plainly.statement;

import app.plainly.auth.SignedInUser;
import app.plainly.billing.PaymentService;
import app.plainly.billing.RefundListing;
import app.plainly.common.NotFoundException;
import app.plainly.common.Today;
import app.plainly.ledger.EntryType;
import app.plainly.ledger.LedgerLine;
import app.plainly.me.MyCoverService.DueReason;
import app.plainly.policy.PolicyService;
import app.plainly.policy.PolicyViews;
import app.plainly.statement.Statement.Banner;
import app.plainly.statement.Statement.BannerState;
import app.plainly.statement.Statement.Cause;
import app.plainly.statement.Statement.Correction;
import app.plainly.statement.Statement.Maths;
import app.plainly.statement.Statement.Money;
import app.plainly.statement.Statement.MoneyStatus;
import app.plainly.statement.Statement.Ref;
import app.plainly.statement.Statement.RefKind;
import app.plainly.statement.Statement.Row;
import app.plainly.statement.Statement.Trace;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Builds P3 "Your statement" and its P4 traces for one policy, read-only and role-neutral (the broker's history
 * reuses it). Everything is read back from what was posted: the ledger lines, the policy's changes, its payments and
 * refunds. Nothing here writes, and no number is stored twice, so the list and the drawer can't disagree.
 */
@Service
public class StatementService {

    private final PolicyService policies;
    private final PaymentService payments;
    private final Today today;

    public StatementService(PolicyService policies, PaymentService payments, Today today) {
        this.policies = policies;
        this.payments = payments;
        this.today = today;
    }

    /** The statement for a policy, every amount traced back to its change, payment or refund. */
    public Statement forPolicy(long policyId) {
        return build(policies.detail(policyId));
    }

    /** P3 for the signed-in owner: the statement of their own (latest) policy, never anyone else's. */
    public Statement forOwner(SignedInUser me) {
        return build(policies.latestForCustomer(me.customerId())
                .orElseThrow(() -> new NotFoundException("You don't have a policy yet.")));
    }

    private Statement build(PolicyViews.Detail p) {
        return new Builder(p, policies.actorNames(p.policyId()), payments.sentForPolicy(p.policyId()), today)
                .build();
    }

    /** One build: the lookups every row needs, made once. */
    private static final class Builder {

        private final PolicyViews.Detail p;
        private final Map<String, String> actors;
        private final List<PaymentService.Sent> sent;
        private final Today today;
        private final Map<String, LedgerLine> linesById;
        private final Map<String, PolicyViews.Transaction> txById;
        private final Map<String, PaymentService.Sent> paymentsByIntent;
        private final Map<String, RefundListing> refundsById;
        /** A line a correction undid → that correction's change id. */
        private final Map<String, String> correctedBy = new HashMap<>();

        Builder(PolicyViews.Detail p, Map<String, String> actors, List<PaymentService.Sent> sent, Today today) {
            this.p = p;
            this.actors = actors;
            this.sent = sent;
            this.today = today;
            this.linesById = index(p.ledger(), LedgerLine::displayId);
            this.txById = index(p.transactions(), PolicyViews.Transaction::displayId);
            this.paymentsByIntent = sent.stream().filter(s -> s.intentId() != null)
                    .collect(Collectors.toMap(PaymentService.Sent::intentId, Function.identity(), (a, b) -> b));
            this.refundsById = index(p.refunds(), RefundListing::displayId);
        }

        Statement build() {
            List<Correction> corrections = corrections();
            List<Row> rows = new ArrayList<>();
            for (LedgerLine l : p.ledger()) {
                rows.add(ledgerRow(l));
            }
            rows.addAll(pendingRows());
            return new Statement(new Statement.Header(p.customer().legalName(), p.policyNumber(), p.balanceCents()),
                    banner(), List.copyOf(rows), corrections, p.today());
        }

        // --- the banner (AC3) ---

        private Banner banner() {
            PaymentService.Sent waiting = waitingPayment();
            if (waiting != null) {
                return new Banner(BannerState.PAYMENT_WAITING, waiting.amountCents(), null, waiting.cardLast4());
            }
            long balance = p.balanceCents();
            if (balance > 0) {
                return new Banner(BannerState.OWES, balance, DueReason.of(balance, p.transactions()), null);
            }
            List<RefundListing> owed = unsettledRefunds();
            if (!owed.isEmpty()) {
                long total = owed.stream().mapToLong(RefundListing::amountCents).sum();
                return new Banner(BannerState.REFUND_WAITING, total, null, largest(owed).cardLast4());
            }
            return new Banner(BannerState.PAID_UP, 0, null, null);
        }

        // --- corrections (AC5, AC6) ---

        private List<Correction> corrections() {
            List<Correction> out = new ArrayList<>();
            for (PolicyViews.Transaction tx : p.transactions()) {
                if (!"CORRECT".equals(tx.type())) {
                    continue;
                }
                LedgerLine undo = lineOf(tx, EntryType.REVERSAL);
                LedgerLine rebill = lineOf(tx, EntryType.CHARGE);
                LedgerLine original = undo == null ? null : linesById.get(undo.reversesDisplayId());
                if (undo == null || rebill == null || original == null) {
                    continue;
                }
                correctedBy.put(original.displayId(), tx.displayId());
                long annual = rebill.calcAnnualCents();
                int term = rebill.calcTermDays();
                Long perDay = annual % term == 0 ? annual / term : null;
                out.add(new Correction(tx.displayId(), tx.recordedOn(), original.coverFrom(), rebill.coverFrom(),
                        rebill.calcDays() - original.calcDays(), undo.amountCents() + rebill.amountCents(), perDay,
                        tx.reason(), actors.get(tx.displayId()), original.displayId(), undo.displayId(),
                        rebill.displayId()));
            }
            return List.copyOf(out);
        }

        private LedgerLine lineOf(PolicyViews.Transaction tx, EntryType type) {
            return p.ledger().stream()
                    .filter(l -> l.type() == type && tx.displayId().equals(l.policyTransactionDisplayId()))
                    .findFirst().orElse(null);
        }

        // --- ledger rows (AC2, AC4, AC7) ---

        private Row ledgerRow(LedgerLine l) {
            LocalDate undoneOn = l.reversedByDisplayId() == null ? null
                    : linesById.get(l.reversedByDisplayId()).entryDate();
            String ptx = l.policyTransactionDisplayId();
            String correctionId = ptx != null && txById.containsKey(ptx) && "CORRECT".equals(txById.get(ptx).type())
                    ? ptx : correctedBy.get(l.displayId());
            MoneyStatus status = switch (l.type()) {
                case PAYMENT -> l.amountCents() == 0 ? MoneyStatus.DECLINED : MoneyStatus.PAID;
                case REFUND -> MoneyStatus.REFUND_SENT;
                default -> null;
            };
            return new Row(l.displayId(), l.type(), l.entryDate(), l.description(), l.amountCents(),
                    l.balanceAfterCents(), false, status, undoneOn, correctionId, trace(l, status));
        }

        private Trace trace(LedgerLine l, MoneyStatus status) {
            return switch (l.type()) {
                case CHARGE, CREDIT -> new Trace(l.type(), byTheDay(l, l.amountCents(), false), changeCause(l),
                        null, refs(RefKind.CHANGE, l.policyTransactionDisplayId(), RefKind.LINE, l.displayId()));
                case REVERSAL -> {
                    LedgerLine undone = linesById.get(l.reversesDisplayId());
                    yield new Trace(l.type(), byTheDay(undone, l.amountCents(), true), changeCause(l), null,
                            refs(RefKind.CHANGE, l.policyTransactionDisplayId(), RefKind.LINE, l.displayId(),
                                    RefKind.UNDOES, l.reversesDisplayId()));
                }
                case PAYMENT -> {
                    PaymentService.Sent pay = paymentsByIntent.get(l.stripePaymentIntentId());
                    yield new Trace(l.type(), balanceMaths(l), new Cause(null, null, l.entryDate(),
                            p.customer().ownerName(), null, null, null),
                            new Money(pay == null ? null : pay.cardLast4(), l.entryDate(), status),
                            refs(RefKind.LINE, l.displayId(), RefKind.STRIPE_PAYMENT, l.stripePaymentIntentId(),
                                    RefKind.STRIPE_EVENT, l.stripeEventId()));
                }
                case REFUND -> {
                    RefundListing r = refundsById.get(l.refundDisplayId());
                    String ptx = r != null ? r.policyTransactionDisplayId() : l.policyTransactionDisplayId();
                    yield new Trace(l.type(), balanceMaths(l), changeCause(ptx),
                            new Money(r == null ? null : r.cardLast4(), l.entryDate(), status),
                            refs(RefKind.CHANGE, ptx, RefKind.LINE, l.displayId(), RefKind.REFUND,
                                    l.refundDisplayId(), RefKind.STRIPE_REFUND, l.stripeRefundId(),
                                    RefKind.STRIPE_EVENT, l.stripeEventId()));
                }
            };
        }

        /** Annual price × days ÷ days in term over the line's cover dates; a reversal shows the undone line's. */
        private static Maths byTheDay(LedgerLine numbers, long resultCents, boolean undoes) {
            return new Maths(numbers.calcAnnualCents(), numbers.calcDays(), numbers.calcTermDays(),
                    numbers.coverFrom(), numbers.coverTo(), resultCents, undoes, null, null);
        }

        private static Maths balanceMaths(LedgerLine l) {
            return new Maths(null, null, null, null, null, l.amountCents(), false,
                    l.balanceAfterCents() - l.amountCents(), l.balanceAfterCents());
        }

        private Cause changeCause(LedgerLine l) {
            return changeCause(l.policyTransactionDisplayId());
        }

        /** The change behind a row: type, dates, who, the broker's words, and old → new start for a correction. */
        private Cause changeCause(String ptx) {
            PolicyViews.Transaction tx = ptx == null ? null : txById.get(ptx);
            if (tx == null) {
                return new Cause(null, null, null, null, null, null, null);
            }
            LocalDate oldStart = null;
            LocalDate newStart = null;
            if ("CORRECT".equals(tx.type())) {
                LedgerLine undo = lineOf(tx, EntryType.REVERSAL);
                LedgerLine original = undo == null ? null : linesById.get(undo.reversesDisplayId());
                oldStart = original == null ? null : original.coverFrom();
                newStart = tx.effectiveDate();
            }
            return new Cause(tx.type(), tx.effectiveDate(), tx.recordedOn(), actors.get(tx.displayId()), tx.reason(),
                    oldStart, newStart);
        }

        // --- money on its way, outside the balance (AC4) ---

        private List<Row> pendingRows() {
            List<Row> rows = new ArrayList<>();
            long balance = p.balanceCents();
            PaymentService.Sent waiting = waitingPayment();
            if (waiting != null) {
                LocalDate on = today.dayOf(waiting.createdAt());
                long amount = -waiting.amountCents();
                Trace t = new Trace(EntryType.PAYMENT,
                        new Maths(null, null, null, null, null, amount, false, balance, balance + amount),
                        new Cause(null, null, on, p.customer().ownerName(), null, null, null),
                        new Money(waiting.cardLast4(), on, MoneyStatus.WAITING),
                        refs(RefKind.STRIPE_PAYMENT, waiting.intentId()));
                rows.add(new Row(waiting.displayId(), EntryType.PAYMENT, on, "Payment", amount, balance, true,
                        MoneyStatus.WAITING, null, null, t));
            }
            for (RefundListing r : unsettledRefunds()) {
                Cause cause = changeCause(r.policyTransactionDisplayId());
                LocalDate on = cause.recordedOn() != null ? cause.recordedOn() : p.today();
                String card = r.cardLast4() == null ? "card" : "card ending " + r.cardLast4();
                Trace t = new Trace(EntryType.REFUND,
                        new Maths(null, null, null, null, null, r.amountCents(), false, balance,
                                balance + r.amountCents()),
                        cause, new Money(r.cardLast4(), on, MoneyStatus.REFUND_ON_ITS_WAY),
                        refs(RefKind.CHANGE, r.policyTransactionDisplayId(), RefKind.REFUND, r.displayId(),
                                RefKind.STRIPE_REFUND, r.stripeRefundId()));
                rows.add(new Row(r.displayId(), EntryType.REFUND, on, "Refund to " + card, r.amountCents(),
                        balance, true, MoneyStatus.REFUND_ON_ITS_WAY, null, null, t));
            }
            return rows;
        }

        /** The payment with the bank right now, if any (the newest PROCESSING one). */
        private PaymentService.Sent waitingPayment() {
            return sent.stream().filter(s -> "PROCESSING".equals(s.status())).reduce((a, b) -> b).orElse(null);
        }

        /** Refunds not yet confirmed by Stripe; a failed one counts, because the broker retries it (as on P1). */
        private List<RefundListing> unsettledRefunds() {
            return p.refunds().stream().filter(r -> !"SUCCEEDED".equals(r.status())).toList();
        }

        private static RefundListing largest(List<RefundListing> refunds) {
            return refunds.stream().max(Comparator.comparingLong(RefundListing::amountCents)).orElseThrow();
        }

        /** References in the order given, leaving out ids that don't exist for this row. */
        private static List<Ref> refs(Object... kindThenId) {
            List<Ref> out = new ArrayList<>();
            for (int i = 0; i < kindThenId.length; i += 2) {
                if (kindThenId[i + 1] != null) {
                    out.add(new Ref((RefKind) kindThenId[i], (String) kindThenId[i + 1]));
                }
            }
            return List.copyOf(out);
        }

        private static <T> Map<String, T> index(List<T> items, Function<T, String> key) {
            Map<String, T> out = new HashMap<>();
            items.forEach(i -> out.put(key.apply(i), i));
            return out;
        }
    }
}
