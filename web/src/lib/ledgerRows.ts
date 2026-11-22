import type { LedgerLine } from '../api/types';
import { formatDate } from './dates';
import { formatCents } from './money';

export type Tone = 'ink' | 'good' | 'wait' | 'bad';

/** A ledger line ready to show: every amount carries its working ("calc") and its sources ("refs"). */
export type LedgerRow = {
  key: string;
  date: string;
  title: string;
  /** Small second line, e.g. "Undone by LE-0006". */
  note: string;
  /** How the amount was worked out, or what happened to the money. */
  calc: string;
  calcTone: Tone;
  amount: string;
  balance: string;
  /** Ledger id first, then where it came from: the policy transaction, the Stripe payment, or the refund. */
  refs: string[];
  /** The line was later undone by a reversal. It stays visible, struck through. */
  struck: boolean;
  /** The line is part of a correction (an undo and its replacement). */
  correction: boolean;
};

function calculation(l: LedgerLine): { calc: string; calcTone: Tone } {
  switch (l.type) {
    case 'CHARGE':
      return l.calcAnnualCents != null && l.calcDays != null && l.calcTermDays != null
        ? { calc: `${formatCents(l.calcAnnualCents)} × ${l.calcDays} ÷ ${l.calcTermDays}`, calcTone: 'ink' }
        : { calc: '', calcTone: 'ink' };
    case 'REVERSAL':
      return { calc: l.reversesDisplayId ? `Undo of ${l.reversesDisplayId}` : 'Undo', calcTone: 'ink' };
    case 'PAYMENT':
      // A $0.00 payment line is a declined attempt: kept on the ledger so the attempt stays visible.
      return l.amountCents === 0 ? { calc: "Didn't go through", calcTone: 'bad' } : { calc: 'Paid', calcTone: 'good' };
    case 'REFUND':
      return { calc: 'Refunded', calcTone: 'good' };
  }
}

/** Ledger lines (oldest first) to display rows. Lines from a correcting transaction are flagged together. */
export function toLedgerRows(lines: LedgerLine[]): LedgerRow[] {
  const correctingTx = new Set(
    lines.filter((l) => l.type === 'REVERSAL' && l.policyTransactionDisplayId).map((l) => l.policyTransactionDisplayId),
  );
  return lines.map((l) => ({
    key: l.displayId,
    date: formatDate(l.entryDate),
    title: l.description,
    note: l.reversedByDisplayId
      ? `Undone by ${l.reversedByDisplayId}`
      : l.type === 'PAYMENT' && l.amountCents === 0
        ? 'No money moved'
        : '',
    ...calculation(l),
    amount: formatCents(l.amountCents, { signed: true }),
    balance: formatCents(l.balanceAfterCents),
    refs: [l.displayId, l.policyTransactionDisplayId, l.stripePaymentIntentId, l.refundDisplayId, l.stripeRefundId]
      .filter((r): r is string => !!r),
    struck: l.reversedByDisplayId != null,
    correction: l.policyTransactionDisplayId != null && correctingTx.has(l.policyTransactionDisplayId),
  }));
}
