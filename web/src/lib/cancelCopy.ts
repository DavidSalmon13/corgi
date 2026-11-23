import type { CancelPlan, Cancellation, Cancelled, CoverKind, KeptBackFor, PlannedRefund } from '../api/types';
import { calcArithmetic } from './addCoverCopy';
import { formatDate, formatShortDate, type IsoDate } from './dates';
import { formatCents } from './money';

// What the broker reads on B4's Cancel tab and B3 after cancelling, and what the customer reads on P1 and P6.
// Standard terms (return premium, short-rate, pro-rata) throughout; screens render these through `Glossed` for the "?".

function firstName(ownerName: string): string | null {
  return ownerName.trim().split(/\s+/)[0] || null;
}

function plural(n: number, word: string): string {
  return `${n} ${n === 1 ? word : `${word}s`}`;
}

/** Cover stops at the start of the cancel day, so on that day it has already ended. */
function hasEnded(endsOn: IsoDate, today: IsoDate): boolean {
  return endsOn <= today;
}

const COVER_NAME: Record<CoverKind, string> = { BASE: 'General liability', PRODUCT_LIABILITY: 'Product liability' };

/** B4: which change left money owing, kept back from the credit. */
const KEPT_BACK_B4: Record<KeptBackFor, string> = {
  ISSUE: 'First year still owed',
  ADD_COVERAGE: 'Endorsement still owed',
  CORRECT: 'Correction still owed',
};

/** P6: the same, as the customer knows it. */
const KEPT_BACK_P6: Record<KeptBackFor, string> = {
  ISSUE: 'from your first year',
  ADD_COVERAGE: 'for your product liability endorsement',
  CORRECT: 'from the effective-date correction',
};

/** One line of "What Maya gets back" (B4) or "How we worked it out" (P6). {@code total} is the bottom line. */
export type MoneyRow = { label: string; detail: string; amount: string; total: boolean };

function row(label: string, detail: string, amount: string, total = false): MoneyRow {
  return { label, detail, amount, total };
}

/** "card ending 4242", or "card" when we don't know it. */
function card(last4: string | null): string {
  return last4 ? `card ending ${last4}` : 'card';
}

/** The card the most money goes back to, as the refund line and the customer sentence name it. */
function mainCard(refunds: PlannedRefund[]): string | null {
  const biggest = [...refunds].sort((a, b) => b.amountCents - a.amountCents)[0];
  return biggest?.cardLast4 ?? null;
}

/** "Your cover ends on Dec 1, 2026." or, from that day on, "Your cover ended on Dec 1, 2026." */
export function coverEndsSentence(endsOn: IsoDate, today: IsoDate): string {
  return `Your cover ${hasEnded(endsOn, today) ? 'ended' : 'ends'} on ${formatDate(endsOn)}.`;
}

/**
 * B4's preview: one row of return premium per coverage, what it keeps back to pay off what was owed, then the bottom line:
 * the refund, what's still owed for the days covered, or nothing either way.
 */
export function cancelPreviewRows(plan: CancelPlan): MoneyRow[] {
  const rows = plan.credits.map((c) => row(c.label, calcArithmetic(c.calc), formatCents(c.amountCents)));
  if (plan.stillOwedCents > 0) {
    rows.push(
      row(
        `Still owed for the ${plural(plan.usedDays, 'day')} covered`,
        "What the return premium doesn't cover",
        formatCents(plan.stillOwedCents),
        true,
      ),
    );
    return rows;
  }
  if (plan.keptBackCents > 0) {
    const why = plan.keptBackFor ? KEPT_BACK_B4[plan.keptBackFor] : 'Still owed';
    rows.push(row('Unpaid balance kept back', why, formatCents(plan.keptBackCents, { signed: true })));
  }
  if (plan.refundTotalCents > 0) {
    rows.push(
      row(
        `Refund to ${card(mainCard(plan.refunds))}`,
        'Sent through Stripe when you confirm',
        formatCents(plan.refundTotalCents),
        true,
      ),
    );
  } else {
    rows.push(row('Nothing to refund or pay', 'The return premium pays off what was owed', formatCents(0), true));
  }
  return rows;
}

/** "$642.00 from the latest payment, then $792.82 from the first": each payment the refund goes back to. */
export function refundPieces(plan: CancelPlan): string | null {
  const n = plan.refunds.length;
  if (n === 0) return null;
  return plan.refunds
    .map((r, i) => {
      const which = i === 0 ? 'the latest payment' : i === n - 1 ? 'the first' : 'the one before';
      return `${formatCents(r.amountCents)} from ${which}`;
    })
    .join(', then ');
}

/** The one sentence the customer gets about this cancellation. */
export function customerWillSeeCancel(plan: CancelPlan, today: IsoDate): string {
  const ends = coverEndsSentence(plan.cancelDate, today);
  if (plan.refundTotalCents > 0) {
    return `${ends} We're sending ${formatCents(plan.refundTotalCents)} back to your ${card(mainCard(plan.refunds))}.`;
  }
  if (plan.stillOwedCents > 0) {
    return `${ends} You owe ${formatCents(plan.stillOwedCents)} for the days you were covered.`;
  }
  return `${ends} You have nothing to pay.`;
}

/** B4's preview heading: "What Maya gets back", or "What Maya still owes" when the credit falls short. */
export function cancelPreviewHeading(plan: CancelPlan | null, ownerName: string): string {
  const who = firstName(ownerName) ?? 'the customer';
  return plan && plan.stillOwedCents > 0 ? `What ${who} still owes` : `What ${who} gets back`;
}

/** "Cancel policy and refund $1,434.82", or "Cancel policy" when nothing goes back to a card. */
export function cancelSaveLabel(plan: CancelPlan): string {
  return plan.refundTotalCents > 0 ? `Cancel policy and refund ${formatCents(plan.refundTotalCents)}` : 'Cancel policy';
}

/** The confirm dialog: "All cover ends on Dec 1, 2026. Maya gets $1,434.82 back and sees your reason." */
export function cancelConfirm(plan: CancelPlan, ownerName: string): { title: string; sentence: string } {
  const who = firstName(ownerName) ?? 'The customer';
  let money: string;
  if (plan.refundTotalCents > 0) money = `${who} gets ${formatCents(plan.refundTotalCents)} back`;
  else if (plan.stillOwedCents > 0) money = `${who} still owes ${formatCents(plan.stillOwedCents)} for the days covered`;
  else money = `${who} owes nothing`;
  return {
    title: 'Cancel this policy?',
    sentence: `All cover ends on ${formatDate(plan.cancelDate)}. ${money} and sees your reason.`,
  };
}

/** The once-only banner on B3 after cancelling, and the ids it links. */
export function cancelledBanner(ownerName: string, saved: Cancelled, today: IsoDate): { message: string; refs: string } {
  const who = firstName(ownerName);
  const ends = `Cover ${hasEnded(saved.cancelDate, today) ? 'ended' : 'ends'} on ${formatDate(saved.cancelDate)}`;
  let message: string;
  if (saved.refundTotalCents > 0) {
    const whose = who ? `${who}'s` : "the customer's";
    message = `Cancelled. ${ends} and ${formatCents(saved.refundTotalCents)} is on its way to ${whose} card.`;
  } else if (saved.balanceCents > 0) {
    message = `Cancelled. ${ends}. ${who ?? 'The customer'} still owes ${formatCents(saved.balanceCents)} for the days covered.`;
  } else {
    message = `Cancelled. ${ends}. ${who ?? 'The customer'} has nothing to pay.`;
  }
  const refs = [`Cancellation ${saved.transactionDisplayId}`, saved.creditDisplayIds.join(', ')]
    .filter((s) => s.length > 0)
    .join(' · ');
  return { message, refs };
}

/** P1's Cancelled card: the refund on its way or sent, or what's still due for the days covered. */
export function cancelledCard(
  c: Cancellation,
  amountDueCents: number,
  today: IsoDate,
): { sentence: string; due: string | null } {
  const ends = coverEndsSentence(c.endsOn, today);
  if (amountDueCents > 0) {
    return {
      sentence: ends,
      due: `${formatCents(amountDueCents)} due · for the ${plural(c.usedDays, 'day')} you were covered`,
    };
  }
  if (c.refundStatus === 'SUCCEEDED') {
    return { sentence: `We sent ${formatCents(c.refundCents)} back to your ${card(c.cardLast4)}.`, due: null };
  }
  if (c.refundStatus === 'PENDING') {
    return { sentence: `${ends} We're sending ${formatCents(c.refundCents)} back to your ${card(c.cardLast4)}.`, due: null };
  }
  return { sentence: ends, due: null };
}

/** P6's heading: "Your cover has ended", or "Your cover ends on Dec 1, 2026" before that day. */
export function cancellationTitle(c: Cancellation, today: IsoDate): string {
  return hasEnded(c.endsOn, today) ? 'Your cover has ended' : `Your cover ends on ${formatDate(c.endsOn)}`;
}

/** Under P6's bar: "91 days used. The striped part, 274 unused days, comes back to you." */
export function unusedDaysCaption(c: Cancellation): string {
  const where = c.refundCents > 0 ? 'comes back to you' : 'comes off what you owed';
  return `${plural(c.usedDays, 'day')} used. The striped part, ${plural(c.unusedDays, 'unused day')}, ${where}.`;
}

/** P6 "How we worked it out": each return premium, what was kept back, then the refund or what's still due. */
export function workedOutRows(c: Cancellation, amountDueCents = 0): MoneyRow[] {
  const rows = c.credits.map((credit) =>
    row(
      `${COVER_NAME[credit.kind]} return premium`,
      calcArithmetic({ annualCents: credit.annualCents, days: credit.days, termDays: c.termDays, amountCents: -credit.amountCents }),
      formatCents(credit.amountCents),
    ),
  );
  if (amountDueCents > 0) {
    rows.push(
      row(
        `Still owed for the ${plural(c.usedDays, 'day')} you were covered`,
        'No short-rate penalty',
        formatCents(amountDueCents),
        true,
      ),
    );
    return rows;
  }
  if (c.keptBackCents > 0) {
    const amount = `The ${formatCents(c.keptBackCents)}`;
    const what = c.keptBackFor ? `${amount} ${KEPT_BACK_P6[c.keptBackFor]}` : amount;
    rows.push(row('Unpaid balance kept back', `${what}, still owed`, formatCents(c.keptBackCents, { signed: true })));
  }
  if (c.refundCents > 0) {
    rows.push(row(`Refund to ${card(c.cardLast4)}`, 'No short-rate penalty', formatCents(c.refundCents), true));
  } else {
    rows.push(row('Nothing more to pay', 'No short-rate penalty', formatCents(0), true));
  }
  return rows;
}

/** P6's refund pill and the sentence about Stripe and the bank; null when nothing goes back to a card. */
export function refundPill(
  c: Cancellation,
): { label: string; tone: 'wait' | 'good'; sentence: string } | null {
  if (c.refundStatus === 'SUCCEEDED') {
    const when = c.settledOn ? ` on ${formatShortDate(c.settledOn)}` : '';
    return {
      label: 'Refunded',
      tone: 'good',
      sentence: `Stripe confirmed the refund${when}. Your bank may take 5–10 days to show it.`,
    };
  }
  if (c.refundStatus === 'PENDING') {
    return {
      label: 'Refund on its way',
      tone: 'wait',
      sentence: "We've asked Stripe to send it. Once it's sent, banks take 5–10 days to show it.",
    };
  }
  return null;
}

const REASON_MAX = 500;

/**
 * What to fix before the Cancel tab may ask "Cancel this policy?", with the server's own words and rules (the reason
 * is measured without the spaces around it). The form checks this instead of sending the save to find out, because
 * a save the server accepts cancels the policy and sends the refund without the confirm.
 */
export function cancelSaveProblems(
  cancelDate: string,
  reason: string,
): { message: string; fields: { cancelDate?: string; reason?: string } } | null {
  const fields: { cancelDate?: string; reason?: string } = {};
  if (cancelDate === '') fields.cancelDate = 'Pick the day cover ends.';
  const trimmed = reason.trim();
  if (trimmed === '') fields.reason = 'Say why the policy is being cancelled. The customer sees this.';
  else if (trimmed.length > REASON_MAX) fields.reason = `Keep the reason under ${REASON_MAX} characters.`;
  const n = Object.keys(fields).length;
  return n === 0 ? null : { message: `${n} ${n === 1 ? 'thing' : 'things'} to fix before you can save.`, fields };
}
