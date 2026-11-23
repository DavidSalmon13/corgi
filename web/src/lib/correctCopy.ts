import type { Corrected, CorrectionPlan, PlannedRefund, RefundView } from '../api/types';
import { calcArithmetic } from './addCoverCopy';
import { formatShortDate, type IsoDate } from './dates';
import { formatCents } from './money';

// What the broker reads on B4's Correct tab and B3 after a correction, and the one sentence the customer gets.
// Standard terms (reversal, rebill, effective date) throughout; screens render these through `Glossed` for the "?".

function firstName(ownerName: string): string | null {
  return ownerName.trim().split(/\s+/)[0] || null;
}

/** The daily price, only when the annual premium divides into whole cents ($730.00 over 365 days is $2.00). */
function perDayCents(annualCents: number, termDays: number): number | null {
  return annualCents % termDays === 0 ? annualCents / termDays : null;
}

/** "7 more days × $2.00", "1 fewer day", or "7 more days" when the daily price isn't a whole number of cents. */
function dayChange(plan: CorrectionPlan): string {
  const diff = plan.rebill.calc.days - plan.undo.calc.days;
  if (diff === 0) return 'same number of days';
  const n = Math.abs(diff);
  const words = `${n} ${diff > 0 ? 'more' : 'fewer'} ${n === 1 ? 'day' : 'days'}`;
  const perDay = perDayCents(plan.rebill.calc.annualCents, plan.rebill.calc.termDays);
  return perDay === null ? words : `${words} × ${formatCents(perDay)}`;
}

function last4s(refunds: PlannedRefund[]): string[] {
  return [...new Set(refunds.map((r) => r.cardLast4).filter((c): c is string => !!c))];
}

/** "card ending 4242", "cards ending 0005 and 4242", or "the card" when we don't know it. */
function cardPhrase(refunds: PlannedRefund[]): string {
  const cards = last4s(refunds);
  if (cards.length === 0) return 'the card';
  if (cards.length === 1) return `card ending ${cards[0]}`;
  return `cards ending ${cards.slice(0, -1).join(', ')} and ${cards[cards.length - 1]}`;
}

/** "Product liability endorsement ($730.00 annual premium)": which coverage is being corrected. */
export function correctingWhich(annualCents: number): string {
  return `Product liability endorsement (${formatCents(annualCents)} annual premium)`;
}

/** "Reversal: product liability from Oct 15 · $730.00 × 321 ÷ 365 · −$642.00" */
export function undoLine(plan: CorrectionPlan): string {
  return `Reversal: product liability from ${formatShortDate(plan.oldStart)} · ${calcArithmetic(plan.undo.calc)} · ${formatCents(plan.undo.amountCents)}`;
}

/** "Rebill: product liability from Oct 8 · $730.00 × 328 ÷ 365 · +$656.00" */
export function correctedLine(plan: CorrectionPlan): string {
  const amount = formatCents(plan.rebill.calc.amountCents, { signed: true });
  return `Rebill: product liability from ${formatShortDate(plan.newStart)} · ${calcArithmetic(plan.rebill.calc)} · ${amount}`;
}

/** "+$14.00 (7 more days × $2.00)" */
export function netChange(plan: CorrectionPlan): string {
  return `${formatCents(plan.netCents, { signed: true })} (${dayChange(plan)})`;
}

/** "7 more days × $2.00 · −$642.00 + $656.00": how the net change adds up. */
export function netWhy(plan: CorrectionPlan): string {
  return `${dayChange(plan)} · ${formatCents(plan.undo.amountCents)} + ${formatCents(plan.rebill.calc.amountCents)}`;
}

/** The sentence the customer reads about this correction. */
export function customerWillSeeCorrection(plan: CorrectionPlan): string {
  const why = 'because we corrected the effective date of your product liability endorsement.';
  if (plan.netCents > 0) return `You owe ${formatCents(plan.netCents)} ${why}`;
  if (plan.refunds.length > 0) {
    const cards = last4s(plan.refunds);
    const where = cards.length === 0 ? 'your card' : `your ${cardPhrase(plan.refunds)}`;
    return `We owe you ${formatCents(plan.refundTotalCents)}. It's on its way to ${where}.`;
  }
  if (plan.netCents < 0) return `You owe ${formatCents(-plan.netCents)} less ${why}`;
  return `What you owe stays the same; we corrected the effective date of your product liability endorsement.`;
}

/** What happens to the money when the broker saves: added to the balance, or refunded. */
export function settleLine(plan: CorrectionPlan, ownerName: string): string {
  const who = firstName(ownerName);
  if (plan.netCents > 0) {
    return `Added to ${who ? `${who}'s` : "the customer's"} balance. ${who ?? 'The customer'} is asked to pay it; no card is charged automatically.`;
  }
  if (plan.refunds.length > 0) {
    const refunded = `${formatCents(plan.refundTotalCents)} is refunded to ${cardPhrase(plan.refunds)} through Stripe as soon as you save.`;
    const paysOff = -plan.netCents - plan.refundTotalCents;
    return paysOff > 0
      ? `${formatCents(paysOff)} pays off what ${who ?? 'the customer'} still owes, and ${refunded}`
      : `Nothing is owed, so the ${refunded}`;
  }
  if (plan.netCents < 0) return `Taken off what ${who ?? 'the customer'} still owes. Nothing is refunded.`;
  return 'The balance stays the same.';
}

/** "Save correction", or "Save and refund $14.00" when money goes back to a card. */
export function correctSaveLabel(plan: CorrectionPlan): string {
  return plan.refundTotalCents > 0 ? `Save and refund ${formatCents(plan.refundTotalCents)}` : 'Save correction';
}

/** The confirm dialog: what changes, then the three amounts. */
export function confirmSummary(plan: CorrectionPlan, ownerName: string): { sentence: string; amounts: string } {
  const who = firstName(ownerName);
  const moved = `The effective date of the product liability endorsement becomes ${formatShortDate(plan.newStart)} instead of ${formatShortDate(plan.oldStart)}.`;
  let money: string;
  if (plan.refunds.length > 0) {
    money = `${formatCents(plan.refundTotalCents)} goes back to ${cardPhrase(plan.refunds)} and ${who ?? 'the customer'} sees your reason word for word.`;
  } else if (plan.netCents !== 0) {
    const change = `${formatCents(Math.abs(plan.netCents))} ${plan.netCents > 0 ? 'more' : 'less'}`;
    money = `${who ?? 'The customer'} will owe ${change} and see your reason word for word.`;
  } else {
    money = `${who ?? 'The customer'} will see your reason word for word.`;
  }
  return {
    sentence: `${moved} ${money}`,
    amounts: [
      `Reversal ${formatCents(plan.undo.amountCents)}`,
      `Rebill ${formatCents(plan.rebill.calc.amountCents, { signed: true })}`,
      `Net ${formatCents(plan.netCents, { signed: true })}`,
    ].join(' · '),
  };
}

/** The once-only banner on B3 after saving, and the ids it links. */
export function correctedBanner(ownerName: string, saved: Corrected, cardLast4: string | null): {
  message: string;
  refs: string;
} {
  const who = firstName(ownerName);
  const refs = [
    `Correction ${saved.transactionDisplayId}`,
    `reversal ${saved.undoDisplayId}`,
    `rebill ${saved.rebillDisplayId}`,
    ...saved.refunds.map((r) => `refund ${r.displayId}`),
  ].join(' · ');
  let message: string;
  if (saved.refunds.length > 0) {
    const total = saved.refunds.reduce((sum, r) => sum + r.amountCents, 0);
    const card = cardLast4 ? `card ending ${cardLast4}` : 'card';
    message = `Saved. ${formatCents(total)} is on its way back to ${who ? `${who}'s` : "the customer's"} ${card}.`;
  } else if (saved.balanceCents > 0) {
    message = `Saved. ${who ?? 'The customer'} now owes ${formatCents(saved.balanceCents)} and can see why.`;
  } else {
    message = `Saved. ${who ?? 'The customer'} has nothing to pay right now and can see why.`;
  }
  return { message, refs };
}

/** What a policy change is called in a sentence ("The cancellation is saved"), from its transaction type. */
const CHANGE_NAME: Record<string, string> = {
  CORRECT: 'correction',
  CANCEL: 'cancellation',
  REMOVE_COVERAGE: 'removal',
};

/**
 * B3's one banner for every refund Stripe couldn't send, however many payments the change split it across. Names
 * the change that owed the money from its own transaction (a cancellation is never called a correction), and what
 * the customer sees meanwhile: P1, P3 and P6 keep showing the refund as on its way while the broker retries it.
 */
export function failedRefundsBanner(
  refunds: RefundView[],
  transactions: { displayId: string; type: string }[],
  ownerName: string,
): { heading: string; detail: string; retryLabel: string } {
  const total = refunds.reduce((sum, r) => sum + r.amountCents, 0);
  const cards = [...new Set(refunds.map((r) => r.cardLast4))];
  const to =
    cards.length === 1 && cards[0] ? `card ending ${cards[0]}` : cards.length === 1 ? 'the card' : 'the cards';
  const heading =
    refunds.length === 1
      ? `The ${formatCents(total)} refund didn't go through. Stripe couldn't send the money to ${to}.`
      : `${formatCents(total)} in refunds didn't go through. Stripe couldn't send the money to ${to}.`;

  const ids = [...new Set(refunds.map((r) => r.policyTransactionDisplayId).filter((id): id is string => !!id))];
  const names = [...new Set(ids.map((id) => CHANGE_NAME[transactions.find((t) => t.displayId === id)?.type ?? '']))];
  const what = names.length === 1 && names[0] ? `The ${names[0]} is` : ids.length > 1 ? 'The changes are' : 'The change is';
  const saved = ids.length > 0 ? `${what} saved (${ids.join(', ')}).` : `${what} saved.`;
  const who = firstName(ownerName) ?? 'The customer';
  const sees = refunds.length === 1 ? 'the refund' : 'the money';
  return {
    heading,
    detail: `${saved} ${who} still sees ${sees} as on its way until it goes through.`,
    retryLabel: refunds.length === 1 ? 'Retry refund' : `Retry all ${refunds.length} refunds`,
  };
}

/** B3 timeline: "from Oct 8 · was Oct 15", or "from Oct 8" when the effective date was never corrected. */
export function coverWasLabel(from: IsoDate, previousStart: IsoDate | null): string {
  const now = `from ${formatShortDate(from)}`;
  return previousStart ? `${now} · was ${formatShortDate(previousStart)}` : now;
}
