import type { LedgerLine, PolicyTransaction } from '../api/types';
import { formatDate } from './dates';
import { formatCents } from './money';
import { removalCreditTitle } from './removeCoverCopy';

export type Tone = 'ink' | 'good' | 'wait' | 'bad';

/** A ledger line ready to show: every amount carries its working ("calc") and its sources ("refs"). */
export type LedgerRow = {
  key: string;
  date: string;
  title: string;
  /** Small second line, e.g. "Reversed by correction · LE-0006". */
  note: string;
  /** How the amount was worked out, or what happened to the money. */
  calc: string;
  calcTone: Tone;
  amount: string;
  balance: string;
  /** Ledger id first, then where it came from: the policy transaction, the Stripe payment, or the refund. */
  refs: string[];
  /**
   * The reversal that later cancelled this line, and the anchor of its correction (the reversal row's id in the same
   * table). The line still counted until then, so it stays at full weight with a tag, never struck through.
   */
  reversedBy: { displayId: string; anchor: string } | null;
  /** The line is part of a correction (an undo and its replacement). */
  correction: boolean;
};

function calculation(l: LedgerLine): { calc: string; calcTone: Tone } {
  switch (l.type) {
    case 'CHARGE':
    case 'CREDIT':
      return l.calcAnnualCents != null && l.calcDays != null && l.calcTermDays != null
        ? { calc: `${formatCents(l.calcAnnualCents)} × ${l.calcDays} ÷ ${l.calcTermDays}`, calcTone: 'ink' }
        : { calc: '', calcTone: 'ink' };
    case 'REVERSAL':
      return { calc: l.reversesDisplayId ? `Reversal of ${l.reversesDisplayId}` : 'Reversal', calcTone: 'ink' };
    case 'PAYMENT':
      // A $0.00 payment line is a declined attempt: kept on the ledger so the attempt stays visible.
      return l.amountCents === 0 ? { calc: "Didn't go through", calcTone: 'bad' } : { calc: 'Paid', calcTone: 'good' };
    case 'REFUND':
      return { calc: 'Refunded', calcTone: 'good' };
  }
}

/**
 * A cancellation's return premium as posted. Lines posted before the standard terms read "Base cover, unused days",
 * which still needs its "refunded". A removal's credit is named as a removal.
 */
function title(l: LedgerLine, removals: Set<string>): string {
  if (l.type !== 'CREDIT') return l.description;
  if (l.policyTransactionDisplayId && removals.has(l.policyTransactionDisplayId)) return removalCreditTitle(l.description);
  return l.description.endsWith('unused days') ? `${l.description} refunded` : l.description;
}

/**
 * Ledger lines (oldest first) to display rows. Lines from a correcting transaction are flagged together.
 * {@code transactions}: the policy's changes, so a removal's credit is titled as one, not as a refund.
 */
export function toLedgerRows(
  lines: LedgerLine[],
  transactions: Pick<PolicyTransaction, 'displayId' | 'type'>[] = [],
): LedgerRow[] {
  const removals = new Set(transactions.filter((t) => t.type === 'REMOVE_COVERAGE').map((t) => t.displayId));
  const correctingTx = new Set(
    lines.filter((l) => l.type === 'REVERSAL' && l.policyTransactionDisplayId).map((l) => l.policyTransactionDisplayId),
  );
  const byId = new Map(lines.map((l) => [l.displayId, l]));
  const reversedBy = (l: LedgerLine): LedgerRow['reversedBy'] => {
    if (!l.reversedByDisplayId) return null;
    const ptx = byId.get(l.reversedByDisplayId)?.policyTransactionDisplayId;
    return { displayId: l.reversedByDisplayId, anchor: ptx ? `correction-${ptx}` : `line-${l.reversedByDisplayId}` };
  };
  return lines.map((l) => ({
    key: l.displayId,
    date: formatDate(l.entryDate),
    title: title(l, removals),
    note: l.reversedByDisplayId
      ? `Reversed by correction · ${l.reversedByDisplayId}`
      : l.type === 'PAYMENT' && l.amountCents === 0
        ? 'No money moved'
        : '',
    ...calculation(l),
    amount: formatCents(l.amountCents, { signed: true }),
    balance: formatCents(l.balanceAfterCents),
    refs: [l.displayId, l.policyTransactionDisplayId, l.stripePaymentIntentId, l.refundDisplayId, l.stripeRefundId]
      .filter((r): r is string => !!r),
    reversedBy: reversedBy(l),
    correction: l.policyTransactionDisplayId != null && correctingTx.has(l.policyTransactionDisplayId),
  }));
}
