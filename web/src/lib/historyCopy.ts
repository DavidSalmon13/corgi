import type { HistoryAction, HistoryStatus, RefKind, StatementRow } from '../api/types';
import { formatDate, formatShortDate } from './dates';
import { formatCents } from './money';

// Every sentence on B5 "History". The broker's voice: no "you", the real state of money that went wrong (a failed
// refund, the bank's reason for a declined card), and every ID next to the action it belongs to.

const CHANGE_TITLE: Record<string, string> = {
  ISSUE: 'Policy created',
  ADD_COVERAGE: 'Extra cover added',
  CORRECT: 'Start date corrected',
  CANCEL: 'Policy cancelled',
};

function card(a: HistoryAction): string | null {
  return a.cardLast4 ? `card ending ${a.cardLast4}` : null;
}

/** "Start date corrected · PTX-0003", "Payment attempt, card ending 0002 · PAY-0002", "Refund failed, card ending 4242 · RF-0001". */
export function actionTitle(a: HistoryAction): string {
  switch (a.kind) {
    case 'CHANGE':
      return `${CHANGE_TITLE[a.changeType ?? ''] ?? 'Policy changed'} · ${a.id}`;
    case 'PAYMENT': {
      const lead = a.status === 'DECLINED' ? 'Payment attempt' : 'Payment';
      return `${card(a) ? `${lead}, ${card(a)}` : `${lead} by card`} · ${a.id}`;
    }
    case 'REFUND': {
      if (a.status === 'REFUND_FAILED') return `Refund failed${card(a) ? `, ${card(a)}` : ''} · ${a.id}`;
      return `Refund to ${card(a) ?? 'the card'} · ${a.id}`;
    }
  }
}

/** "+$14.00 net" for a correction, "−$14.00 pending" for money on its way, else the signed amount. */
export function actionMoney(a: HistoryAction): string {
  const amount = formatCents(a.amountCents, { signed: true });
  if (a.pending) return `${amount} pending`;
  if (a.changeType === 'CORRECT') return `${amount} net`;
  return amount;
}

const STATUS_WORD: Record<HistoryStatus, string> = {
  PAID: 'Paid',
  DECLINED: 'Didn’t go through',
  WAITING: 'Waiting for the bank',
  REFUND_SENT: 'Refund sent',
  REFUND_ON_ITS_WAY: 'Refund on its way',
  REFUND_FAILED: 'Refund failed',
};

/** Where a payment or refund stands, in a word or two; null for a policy change. */
export function actionStatus(a: HistoryAction): string | null {
  return a.status ? STATUS_WORD[a.status] : null;
}

export type Tone = 'neutral' | 'good' | 'bad' | 'wait' | 'accent';

/** The rail dot's colour, as in B5: neutral change, green paid, red declined/failed, amber correction/waiting. */
export function actionTone(a: HistoryAction): Tone {
  if (a.kind === 'CHANGE') return a.changeType === 'CORRECT' ? 'accent' : 'neutral';
  switch (a.status) {
    case 'PAID':
    case 'REFUND_SENT':
      return 'good';
    case 'DECLINED':
    case 'REFUND_FAILED':
      return 'bad';
    default:
      return 'wait';
  }
}

/** "$730.00 × 328 ÷ 365", or null when the row has no by-the-day sums. */
function calc(row: StatementRow): string | null {
  const m = row.trace.maths;
  return m.annualCents != null && m.days != null && m.termDays != null
    ? `${formatCents(m.annualCents)} × ${m.days} ÷ ${m.termDays}`
    : null;
}

function refsOf(a: HistoryAction, ...kinds: RefKind[]): string[] {
  const ids = a.rows.flatMap((r) => r.trace.refs.filter((x) => kinds.includes(x.kind)).map((x) => x.id));
  return [...new Set(ids)];
}

function inParens(ids: string[]): string {
  return ids.length ? ` (${ids.join(', ')})` : '';
}

function quoted(reason: string | null, lead: string): string {
  return reason ? `${lead}: “${reason}”` : `${lead}.`;
}

/**
 * "Base cover, full year: $1,200.00 × 365 ÷ 365 = +$1,200.00." — one per line, for a card made of several lines.
 * The signed amount is what HistoryPage turns into a "How we got this" button, so every line stays traceable.
 */
function eachLine(a: HistoryAction): string {
  return a.rows
    .map((r) => {
      const sums = calc(r);
      const amount = formatCents(r.amountCents, { signed: true });
      return `${r.title}: ${sums ? `${sums} = ${amount}` : amount}.`;
    })
    .join(' ');
}

function changeDetail(a: HistoryAction): string {
  if (a.changeType === 'ISSUE' && a.rows.length > 1) {
    const m = a.rows[0].trace.maths;
    const span = m.from && m.to ? `, ${formatDate(m.from)} to ${formatDate(m.to)}` : '';
    return `Cover for a year${span}. ${eachLine(a)}`;
  }
  if (a.changeType === 'ISSUE') {
    const first = a.rows[0];
    const m = first?.trace.maths;
    const span = m?.from && m.to ? `, ${formatDate(m.from)} to ${formatDate(m.to)}` : '';
    const sums = first ? calc(first) : null;
    return `Base cover for a year${span}.${sums ? ` ${sums}.` : ''}`;
  }
  if (a.changeType === 'CORRECT' && a.correction) {
    const c = a.correction;
    const parts = a.rows.map((r) => {
      if (r.kind === 'REVERSAL') {
        const day = r.trace.maths.from ? `${formatShortDate(r.trace.maths.from)} line` : 'Earlier line';
        return `${day} undone in full (${formatCents(r.amountCents, { signed: true })})`;
      }
      const sums = calc(r);
      const amount = formatCents(r.amountCents, { signed: true });
      return `corrected line added (${sums ? `${sums} = ${amount}` : amount})`;
    });
    return `Start moved from ${formatShortDate(c.oldStart)} to ${formatShortDate(c.newStart)}. ${parts.join(', ')}.`;
  }
  if (a.rows.length > 1) return eachLine(a);
  // one line of extra cover, a cancellation, or anything else: its title and its sums (the card total opens it)
  return a.rows.map((r) => `${r.title}.${calc(r) ? ` ${calc(r)}.` : ''}`).join(' ');
}

/** The line under a card's title: the sums for a change, or Stripe's status and refs for a payment or refund. */
export function actionDetail(a: HistoryAction): string {
  if (a.kind === 'CHANGE') return changeDetail(a);
  switch (a.status) {
    case 'PAID':
      return `Paid. Confirmed by Stripe${inParens(refsOf(a, 'STRIPE_PAYMENT', 'STRIPE_EVENT'))}.`;
    case 'DECLINED':
      return a.failureReason
        ? `Didn’t go through. Stripe said: “${a.failureReason}” No money moved.`
        : 'Didn’t go through. Card declined, no money moved.';
    case 'WAITING': {
      const refs = refsOf(a, 'STRIPE_EVENT');
      return `Waiting for the bank. Stripe reports it as processing${inParens(refs.length ? refs : refsOf(a, 'STRIPE_PAYMENT'))}. Shown as paid only once Stripe confirms.`;
    }
    case 'REFUND_SENT':
      return `Sent. Confirmed by Stripe${inParens(refsOf(a, 'STRIPE_REFUND', 'STRIPE_EVENT'))}. Banks take 5–10 days to show it.`;
    case 'REFUND_ON_ITS_WAY':
      return `On its way. Asked Stripe to send it${inParens(refsOf(a, 'STRIPE_REFUND'))}. Shown as sent once Stripe confirms.`;
    case 'REFUND_FAILED':
      return `${quoted(a.failureReason, 'Stripe couldn’t send it')} The customer still sees it as owed to them until a retry goes through.`;
    default:
      return '';
  }
}

export type LedgerChip = { id: string; undone: boolean; row: StatementRow };

/** The card's ledger lines (LE-…), each struck when a later line undid it. Money on its way has no line yet. */
export function ledgerChips(a: HistoryAction): LedgerChip[] {
  return a.rows.filter((r) => !r.pending).map((r) => ({ id: r.id, undone: r.undoneOn != null, row: r }));
}
