import type { PolicyStatus } from '../api/types';
import type { PillTone } from '../components/StatusPill';
import { formatDate, formatShortDate, type IsoDate } from './dates';
import { formatCents } from './money';

/** The status pill for a policy: its state plus, while active, whether money is owed or on its way. */
export function policyStatus(
  status: PolicyStatus,
  balanceCents: number,
  paymentWaiting = false,
): { label: string; tone: PillTone } {
  if (status === 'CANCELLED') return { label: 'Cancelled', tone: 'neutral' };
  if (paymentWaiting) return { label: 'Active · waiting for the bank', tone: 'wait' };
  return balanceCents > 0 ? { label: 'Active · balance due', tone: 'wait' } : { label: 'Active · paid up', tone: 'good' };
}

/** "$1,200.00 due" when the customer owes money, "$642.00 credit" when we owe them, else "$0.00". */
export function balanceText(balanceCents: number): string {
  if (balanceCents > 0) return `${formatCents(balanceCents)} due`;
  if (balanceCents < 0) return `${formatCents(balanceCents, { abs: true })} credit`;
  return formatCents(0);
}

const ACTIONS: Record<string, string> = {
  ISSUE: 'Issued',
  ADD_COVERAGE: 'Endorsement added',
  CORRECT: 'Effective date corrected',
  CANCEL: 'Cancelled',
};

function humanise(type: string): string {
  const words = type.toLowerCase().replace(/_/g, ' ');
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** "Issued · Sep 1" */
export function lastActionText(action: { type: string; recordedOn: IsoDate } | null): string {
  if (!action) return '—';
  return `${ACTIONS[action.type] ?? humanise(action.type)} · ${formatShortDate(action.recordedOn)}`;
}

/** "5 policies · 2 owe money" */
export function listSummary(total: number, owing: number): string {
  const policies = `${total} ${total === 1 ? 'policy' : 'policies'}`;
  const owe = owing === 0 ? 'nobody owes money' : `${owing} ${owing === 1 ? 'owes' : 'owe'} money`;
  return `${policies} · ${owe}`;
}

/** "Sep 1, 2026 → Sep 1, 2027", or "Sep 1, 2026 → Dec 1, 2026 (cancelled)": a cancelled policy ends on its cancel day. */
export function policyPeriod(start: IsoDate, end: IsoDate, cancelledOn: IsoDate | null | undefined): string {
  return cancelledOn
    ? `${formatDate(start)} → ${formatDate(cancelledOn)} (cancelled)`
    : `${formatDate(start)} → ${formatDate(end)}`;
}

/** The small line under the balance on B3. {@code refundPendingCents}: refunds sent to Stripe and not yet settled. */
export function balanceNote(balanceCents: number, paymentWaiting = false, refundPendingCents = 0): string {
  if (paymentWaiting) return 'Waiting for the bank';
  if (refundPendingCents > 0) return `Refund of ${formatCents(refundPendingCents)} on its way`;
  if (balanceCents > 0) return 'Not paid yet';
  if (balanceCents < 0) return 'We owe the customer this';
  return 'Nothing owed';
}
