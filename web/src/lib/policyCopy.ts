import type { PolicyStatus } from '../api/types';
import type { PillTone } from '../components/StatusPill';
import { formatShortDate, type IsoDate } from './dates';
import { formatCents } from './money';

/** The status pill for a policy: its state plus, while active, whether money is owed. */
export function policyStatus(status: PolicyStatus, balanceCents: number): { label: string; tone: PillTone } {
  if (status === 'CANCELLED') return { label: 'Cancelled', tone: 'neutral' };
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
  ADD_COVERAGE: 'Added extra cover',
  CORRECT: 'Corrected',
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

/** The small line under the balance on B3. */
export function balanceNote(balanceCents: number): string {
  if (balanceCents > 0) return 'Not paid yet';
  if (balanceCents < 0) return 'We owe the customer this';
  return 'Nothing owed';
}
