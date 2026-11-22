package app.plainly.statement;

import app.plainly.billing.PaymentService;
import app.plainly.billing.RefundListing;
import app.plainly.ledger.EntryType;
import app.plainly.policy.PolicyService;
import app.plainly.policy.PolicyViews;
import app.plainly.statement.History.Action;
import app.plainly.statement.History.ActionKind;
import app.plainly.statement.History.Status;
import app.plainly.statement.Statement.RefKind;
import app.plainly.statement.Statement.Row;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Builds B5 "History" for the broker: the statement's rows grouped by the action behind them, one card each. The
 * rows and their traces come from {@link StatementService} unchanged, so the broker and the policyholder can never
 * see different numbers. On top, it reads the real state of payments and refunds that went wrong, which the
 * policyholder's statement words more softly. Read-only.
 */
@Service
public class HistoryService {

    private final StatementService statements;
    private final PolicyService policies;
    private final PaymentService payments;

    public HistoryService(StatementService statements, PolicyService policies, PaymentService payments) {
        this.statements = statements;
        this.policies = policies;
        this.payments = payments;
    }

    /** The history of a policy, one card per action, oldest first. */
    public History forPolicy(long policyId) {
        Statement s = statements.forPolicy(policyId); // NotFoundException for an unknown policy
        PolicyViews.Detail p = policies.detail(policyId);
        Map<String, String> actors = policies.actorNames(policyId);
        Map<String, PaymentService.Sent> paymentsByIntent = payments.sentForPolicy(policyId).stream()
                .filter(x -> x.intentId() != null)
                .collect(Collectors.toMap(PaymentService.Sent::intentId, Function.identity(), (a, b) -> b));
        Map<String, PolicyViews.Transaction> txById = p.transactions().stream()
                .collect(Collectors.toMap(PolicyViews.Transaction::displayId, Function.identity(), (a, b) -> b));
        Map<String, RefundListing> refundsById = p.refunds().stream()
                .collect(Collectors.toMap(RefundListing::displayId, Function.identity(), (a, b) -> b));
        Map<String, Statement.Correction> correctionsById = s.corrections().stream()
                .collect(Collectors.toMap(Statement.Correction::transactionDisplayId, Function.identity(),
                        (a, b) -> b));

        // group the rows by their action, in the statement's order (ledger lines, then money on its way)
        Map<String, List<Row>> groups = new LinkedHashMap<>();
        for (Row r : s.rows()) {
            groups.computeIfAbsent(actionId(r, paymentsByIntent), k -> new ArrayList<>()).add(r);
        }

        List<Action> actions = new ArrayList<>();
        groups.forEach((id, rows) -> {
            Row first = rows.getFirst();
            boolean pending = rows.stream().allMatch(Row::pending);
            long amount = rows.stream().filter(r -> pending || !r.pending()).mapToLong(Row::amountCents).sum();
            String actor = first.trace().cause() == null ? null : first.trace().cause().actorName();
            actions.add(switch (first.kind()) {
                case CHARGE, CREDIT, REVERSAL -> {
                    PolicyViews.Transaction tx = txById.get(id);
                    yield new Action(id, ActionKind.CHANGE, tx == null ? null : tx.type(), first.date(),
                            actors.getOrDefault(id, actor), amount, pending, null, null, null,
                            tx == null ? null : tx.reason(), correctionsById.get(id), List.copyOf(rows));
                }
                case PAYMENT -> {
                    PaymentService.Sent sent = paymentsByIntent.get(ref(first, RefKind.STRIPE_PAYMENT));
                    Status status = pending ? Status.WAITING : amount == 0 ? Status.DECLINED : Status.PAID;
                    String card = sent != null ? sent.cardLast4()
                            : first.trace().money() == null ? null : first.trace().money().cardLast4();
                    yield new Action(id, ActionKind.PAYMENT, null, first.date(), actor, amount, pending, status,
                            status == Status.DECLINED && sent != null ? sent.failureReason() : null, card, null, null,
                            List.copyOf(rows));
                }
                case REFUND -> {
                    RefundListing refund = refundsById.get(id);
                    Status status = refund == null || "SUCCEEDED".equals(refund.status()) ? Status.REFUND_SENT
                            : "FAILED".equals(refund.status()) ? Status.REFUND_FAILED : Status.REFUND_ON_ITS_WAY;
                    yield new Action(id, ActionKind.REFUND, null, first.date(), actor, amount, pending, status,
                            status == Status.REFUND_FAILED ? refund.failureReason() : null,
                            refund == null ? null : refund.cardLast4(), null, null, List.copyOf(rows));
                }
            });
        });
        actions.sort(Comparator.comparing(Action::date)); // stable: same-day actions keep the statement's order

        return new History(s.header().businessName(), s.header().policyNumber(), s.header().balanceCents(),
                List.copyOf(actions), s.today());
    }

    /**
     * The action a row belongs to: its policy change (PTX-…) for a charge, credit or reversal; its payment (PAY-…)
     * for a payment line or a payment with the bank; its refund (RF-…) for a refund, settled or not.
     */
    private static String actionId(Row r, Map<String, PaymentService.Sent> paymentsByIntent) {
        String id = switch (r.kind()) {
            case CHARGE, CREDIT, REVERSAL -> ref(r, RefKind.CHANGE);
            case PAYMENT -> {
                PaymentService.Sent sent = paymentsByIntent.get(ref(r, RefKind.STRIPE_PAYMENT));
                yield sent != null ? sent.displayId() : null;
            }
            case REFUND -> ref(r, RefKind.REFUND);
        };
        return id != null ? id : r.id();
    }

    private static String ref(Row r, RefKind kind) {
        return r.trace().refs().stream().filter(x -> x.kind() == kind).map(Statement.Ref::id).findFirst()
                .orElse(null);
    }
}
