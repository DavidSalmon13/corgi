package app.plainly.statement;

import java.time.LocalDate;
import java.util.List;

/**
 * B5 "History" for the broker: every action on one policy, oldest first, with what it did to the money. It is the
 * statement's rows grouped by the action behind them (a policy change, a payment attempt or a refund), so the broker
 * and the policyholder see the same numbers to the cent. Unlike the statement, it tells the broker the real state of
 * money that went wrong: a refund Stripe failed, and the reason the bank gave for a declined card.
 */
public record History(String businessName, String policyNumber, long balanceCents, List<Action> actions,
        LocalDate today) {

    public enum ActionKind {
        /** A policy change: issue, extra cover, a corrected date or a cancellation. */
        CHANGE,
        /** One payment attempt: paid, declined or still with the bank. */
        PAYMENT,
        /** One refund: sent, on its way, or failed and waiting for a retry. */
        REFUND
    }

    /** Where a payment or refund stands, as the broker needs to know it. Null for a policy change. */
    public enum Status { PAID, DECLINED, WAITING, REFUND_SENT, REFUND_ON_ITS_WAY, REFUND_FAILED }

    /**
     * One card on the timeline.
     *
     * <ul>
     *   <li>{@code id}: the change (PTX-…), the payment (PAY-…) or the refund (RF-…).</li>
     *   <li>{@code changeType}: ISSUE, ADD_COVERAGE, CORRECT or CANCEL for a change; else null.</li>
     *   <li>{@code actorName}: who did it: the broker for a change or a refund, the policyholder for a payment.</li>
     *   <li>{@code amountCents}: the sum of the action's rows; a correction's net. Positive means the customer owes
     *       more. When {@code pending}, the money is on its way and not counted in the balance.</li>
     *   <li>{@code failureReason}: Stripe's words for a declined card or a failed refund; else null.</li>
     *   <li>{@code reason}: the broker's reason for a correction or cancellation, as written.</li>
     *   <li>{@code correction}: the correction card (old → new start, days, net) for a CORRECT change.</li>
     *   <li>{@code rows}: the statement rows behind it, each with its "how we got this" trace.</li>
     * </ul>
     */
    public record Action(String id, ActionKind kind, String changeType, LocalDate date, String actorName,
            long amountCents, boolean pending, Status status, String failureReason, String cardLast4, String reason,
            Statement.Correction correction, List<Statement.Row> rows) {}
}
