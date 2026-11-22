import type { MoneyStatus, RefKind, Statement, StatementCorrection, StatementRow, StatementCause } from '../api/types';
import { formatDate, formatShortDate } from './dates';
import { formatCents } from './money';
import { dueReasonText, refundOnItsWay } from './myCoverCopy';

// Every sentence on P3 "Your statement" and P4 "How we got this". The customer never reads "reversal" or a
// bookkeeping word: an undo is "an undo", the arithmetic is days × price, and every id is labelled in plain words.

/** P3's banner: the one thing that matters now. */
export function bannerCopy(banner: Statement['banner']): { title: string; detail: string } {
  switch (banner.state) {
    case 'OWES':
      return {
        title: `You owe ${formatCents(banner.amountCents)}`,
        detail: banner.dueReason ? dueReasonText(banner.dueReason) : '',
      };
    case 'PAYMENT_WAITING':
      return {
        title: 'Waiting for your bank',
        detail: `You paid ${formatCents(banner.amountCents)}. We'll show it as paid once your bank confirms.`,
      };
    case 'REFUND_WAITING':
      return {
        title: refundOnItsWay({ amountCents: banner.amountCents, cardLast4: banner.cardLast4 }),
        detail: 'Banks take 5–10 days to show a refund once it has been sent.',
      };
    case 'PAID_UP':
      return { title: "You're all paid up", detail: 'Nothing to pay right now.' };
  }
}

export function rowTitle(row: StatementRow): string {
  return row.title;
}

const ROW_STATUS: Record<MoneyStatus, string> = {
  PAID: 'Paid',
  DECLINED: "Didn't go through · no money moved",
  WAITING: 'Waiting for your bank',
  REFUND_SENT: 'Refund sent · banks take 5–10 days to show it',
  REFUND_ON_ITS_WAY: 'Refund on its way',
};

/** The small line under a row's title: its arithmetic, or where the money stands. */
export function rowNote(row: StatementRow): string {
  if (row.status) return ROW_STATUS[row.status];
  const m = row.trace.maths;
  if (row.kind === 'REVERSAL') {
    return m.from ? `Undoes the ${formatShortDate(m.from)} line in full` : 'Undoes an earlier line in full';
  }
  const calc = m.annualCents != null ? `${formatCents(m.annualCents)} × ${m.days} ÷ ${m.termDays}` : '';
  const undone = row.undoneOn ? `undone ${formatShortDate(row.undoneOn)}` : '';
  return [calc, undone].filter(Boolean).join(' · ');
}

/** "Oct 15 → Oct 8 · 7 more days × $2.00"; the daily price only when it is a whole number of cents. */
export function correctionSummary(c: StatementCorrection): string {
  const n = Math.abs(c.dayDelta);
  const days =
    c.dayDelta === 0 ? 'same number of days' : `${n} ${c.dayDelta > 0 ? 'more' : 'fewer'} ${n === 1 ? 'day' : 'days'}`;
  const perDay = c.perDayCents != null && c.dayDelta !== 0 ? ` × ${formatCents(c.perDayCents)}` : '';
  return `${formatShortDate(c.oldStart)} → ${formatShortDate(c.newStart)} · ${days}${perDay}`;
}

/** "−$642.00 + $656.00 = +$14.00": the undo plus the corrected line. */
export function correctionNetMaths(undoCents: number, rebillCents: number): string {
  return `${formatCents(undoCents)} + ${formatCents(rebillCents)} = ${formatCents(undoCents + rebillCents, { signed: true })}`;
}

const REF_LABEL: Record<RefKind, string> = {
  CHANGE: 'Change',
  LINE: 'Line',
  UNDOES: 'Undoes',
  STRIPE_PAYMENT: 'Stripe payment',
  REFUND: 'Refund',
  STRIPE_REFUND: 'Stripe refund',
  STRIPE_EVENT: 'Stripe event',
};

const MONEY_STATUS: Record<MoneyStatus, string> = {
  PAID: 'Paid',
  DECLINED: "Didn't go through",
  WAITING: 'Waiting for your bank',
  REFUND_SENT: 'Refund sent',
  REFUND_ON_ITS_WAY: 'Refund on its way',
};

const MONEY_NOTE: Record<MoneyStatus, string> = {
  PAID: 'Stripe confirmed the payment, so we marked it paid.',
  DECLINED: 'No money moved. You can try again with another card.',
  WAITING: "We'll show it as paid once your bank confirms.",
  REFUND_SENT: 'Stripe has sent it. Banks take 5–10 days to show a refund.',
  REFUND_ON_ITS_WAY: "We've asked Stripe to send it. We'll show it as sent once they confirm.",
};

export type DrawerCopy = {
  line: string;
  amount: string;
  plain: string;
  formula: string;
  formulaNote: string;
  actionWhat: string;
  actionWho: string;
  /** The broker's words for a correction or cancellation, shown as written; null otherwise. */
  reason: string | null;
  payment: { what: string; status: string; note: string } | null;
  refs: { label: string; id: string }[];
};

function plainSentence(row: StatementRow): string {
  const { maths: m, cause } = row.trace;
  switch (row.kind) {
    case 'CHARGE':
      if (cause.transactionType === 'CORRECT' && cause.newStart) {
        return `The right price for your extra cover, now that it starts on ${formatShortDate(cause.newStart)}.`;
      }
      return 'The price of this cover for the days it runs, charged by the day.';
    case 'CREDIT':
      return "Money back for the days you won't be covered, because your policy ends early.";
    case 'REVERSAL':
      return `An undo of the ${m.from ? formatShortDate(m.from) + ' ' : ''}line, in full. We don't edit lines, so the original stays on your statement.`;
    case 'PAYMENT':
      if (row.status === 'DECLINED') return 'Your bank said no, so no money moved.';
      if (row.status === 'WAITING') return 'Your payment is with your bank.';
      return 'You paid by card.';
    case 'REFUND':
      return 'Money we owed you, going back to your card.';
  }
}

function formula(row: StatementRow): { formula: string; note: string } {
  const m = row.trace.maths;
  if (m.annualCents != null) {
    const result = formatCents(m.resultCents, { abs: true });
    if (m.undoes) {
      return {
        formula: `Undoes ${formatCents(m.annualCents)} × ${m.days} ÷ ${m.termDays} = ${result}`,
        note: 'The same numbers as the original line, with the sign flipped.',
      };
    }
    const span = m.from && m.to ? `, ${formatDate(m.from)} to ${formatDate(m.to)}` : '';
    return {
      formula: `${formatCents(m.annualCents)} a year × ${m.days} days ÷ ${m.termDays} = ${result}`,
      note: `Charged by the day${span}. The end date isn't counted. Rounded to the cent once.`,
    };
  }
  if (m.balanceBeforeCents != null && m.balanceAfterCents != null) {
    const note = row.pending ? "Not counted in your balance until it's confirmed." : '';
    return { formula: `Balance ${formatCents(m.balanceBeforeCents)} → ${formatCents(m.balanceAfterCents)}`, note };
  }
  return { formula: formatCents(m.resultCents, { signed: true }), note: '' };
}

function changeWhat(cause: StatementCause): string {
  const on = cause.effectiveDate;
  switch (cause.transactionType) {
    case 'ISSUE':
      return on ? `Policy started on ${formatDate(on)}` : 'Policy started';
    case 'ADD_COVERAGE':
      return on ? `Extra cover added from ${formatShortDate(on)}` : 'Extra cover added';
    case 'CORRECT':
      return cause.oldStart && cause.newStart
        ? `Start date corrected: ${formatShortDate(cause.oldStart)} → ${formatShortDate(cause.newStart)}`
        : 'Start date corrected';
    case 'CANCEL':
      return on ? `Policy cancelled from ${formatDate(on)}` : 'Policy cancelled';
    default:
      return 'A change to your policy';
  }
}

/** P4 for one row: the plain reason, the maths, what caused it, the money and the references. */
export function drawerCopy(row: StatementRow): DrawerCopy {
  const { cause, money, refs } = row.trace;
  const isMoney = row.kind === 'PAYMENT';
  const when = cause.recordedOn ? ` · ${formatDate(cause.recordedOn)}` : '';
  const who = isMoney
    ? `${cause.actorName ?? 'You'}${when}`
    : `${cause.actorName ? `${cause.actorName}, your broker` : 'Your broker'}${when}`;
  const f = formula(row);
  return {
    line: `${row.title} · ${formatDate(row.date)}`,
    amount: formatCents(row.amountCents, { signed: true }),
    plain: plainSentence(row),
    formula: f.formula,
    formulaNote: f.note,
    actionWhat: isMoney ? 'You paid by card' : changeWhat(cause),
    actionWho: who,
    reason: cause.reason,
    payment: money
      ? {
          what: `${money.cardLast4 ? `Card ending ${money.cardLast4}` : 'Your card'} · ${formatDate(money.date)}`,
          status: MONEY_STATUS[money.status],
          note: MONEY_NOTE[money.status],
        }
      : null,
    refs: refs.map((r) => ({ label: REF_LABEL[r.kind], id: r.id })),
  };
}
