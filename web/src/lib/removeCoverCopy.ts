import type { Coverage, CoverRemoved, PolicyDetail, PlannedRefund, RemoveCoverPlan } from '../api/types';
import { calcArithmetic } from './addCoverCopy';
import { KEPT_BACK_B4, type MoneyRow } from './cancelCopy';
import { coverWasLabel } from './correctCopy';
import { formatDate, formatShortDate, type IsoDate } from './dates';
import { formatCents } from './money';

// What the broker reads on B4's Remove tab and B3 after removing product liability, and what the customer reads on
// P1. Standard terms (return premium, short-rate, effective date) throughout; screens render these through `Glossed`.

const COVER = 'Product liability';
const REASON_MAX = 500;

function firstName(ownerName: string): string | null {
  return ownerName.trim().split(/\s+/)[0] || null;
}

/** Cover stops at the start of its end day, so on that day it has already ended. */
function hasEnded(endsOn: IsoDate, today: IsoDate): boolean {
  return endsOn <= today;
}

function row(label: string, detail: string, amount: string, total = false): MoneyRow {
  return { label, detail, amount, total };
}

/** "card ending 4242", or "card" when we don't know it. */
function card(last4: string | null): string {
  return last4 ? `card ending ${last4}` : 'card';
}

/** The card the most money goes back to. */
function mainCard(refunds: PlannedRefund[]): string | null {
  const biggest = [...refunds].sort((a, b) => b.amountCents - a.amountCents)[0];
  return biggest?.cardLast4 ?? null;
}

/** Under the "Cover ends on" field. */
export const END_DATE_HINT = `${COVER} stops at the start of this day. No short-rate penalty.`;

/** The button that closes the confirm without removing anything. */
export const KEEP_LABEL = `Keep ${COVER.toLowerCase()}`;

/** A removal's credit on P3, B3 and B5: "Product liability removed, return premium", never "refunded". */
export function removalCreditTitle(description: string): string {
  return description.endsWith(' return premium')
    ? `${description.slice(0, -' return premium'.length)} removed, return premium`
    : description;
}

/** Inside a removed cover's bar on B3: "from Oct 8 · removed Dec 1", keeping "was Oct 15" after a correction. */
export function removedBarLabel(from: IsoDate, previousStart: IsoDate | null, endedOn: IsoDate): string {
  return `${coverWasLabel(from, previousStart)} · removed ${formatShortDate(endedOn)}`;
}

/** After a removal, why Add, Correct or Remove is blocked: the same words the API returns under `policy`. */
export function removedCoverReason(tab: 'add' | 'correct' | 'remove', endedOn: IsoDate): string {
  const on = formatDate(endedOn);
  switch (tab) {
    case 'add':
      return `${COVER} was removed from this policy on ${on}, so it can't be added again.`;
    case 'correct':
      return `${COVER} was removed on ${on}, so its effective date can't be corrected.`;
    case 'remove':
      return `${COVER} was removed on ${on}.`;
  }
}

/** Why B4's Remove tab can't be used on this policy right now, or null when it can. The API's order and words. */
export function removeBlockedReason(p: PolicyDetail): string | null {
  if (p.status !== 'ACTIVE') return 'This policy is cancelled.';
  const extra = p.coverages.find((c) => c.kind === 'PRODUCT_LIABILITY');
  if (!extra) return `This policy has no ${COVER.toLowerCase()} to remove.`;
  if (extra.endedOn) return removedCoverReason('remove', extra.endedOn);
  if (p.paymentWaiting) return 'A payment is waiting for the bank. Remove this once it settles.';
  return null;
}

/**
 * What to fix before the Remove tab may ask "Remove product liability?", with the server's own words and rules (the
 * reason is measured without the spaces around it). Checked here because a save the server accepts sends the refund.
 */
export function removeSaveProblems(
  endDate: string,
  reason: string,
): { message: string; fields: { endDate?: string; reason?: string } } | null {
  const fields: { endDate?: string; reason?: string } = {};
  if (endDate === '') fields.endDate = `Pick the day ${COVER.toLowerCase()} ends.`;
  const trimmed = reason.trim();
  if (trimmed === '') fields.reason = `Say why ${COVER.toLowerCase()} is being removed. The customer sees this.`;
  else if (trimmed.length > REASON_MAX) fields.reason = `Keep the reason under ${REASON_MAX} characters.`;
  const n = Object.keys(fields).length;
  return n === 0 ? null : { message: `${n} ${n === 1 ? 'thing' : 'things'} to fix before you can save.`, fields };
}

/** B4's preview: the return premium, what it keeps back, then the refund, the balance after, or nothing either way. */
export function removePreviewRows(plan: RemoveCoverPlan): MoneyRow[] {
  const rows = [row(plan.credit.label, calcArithmetic(plan.credit.calc), formatCents(plan.creditCents))];
  if (plan.keptBackCents > 0) {
    const why = plan.keptBackFor ? KEPT_BACK_B4[plan.keptBackFor] : 'Still owed';
    rows.push(row('Unpaid balance kept back', why, formatCents(plan.keptBackCents, { signed: true })));
  }
  if (plan.refundTotalCents > 0) {
    rows.push(
      row(
        `Return premium to ${card(mainCard(plan.refunds))}`,
        'Sent through Stripe when you confirm',
        formatCents(plan.refundTotalCents),
        true,
      ),
    );
  } else if (plan.stillOwedCents > 0) {
    rows.push(row('Balance after', 'Still owed for the days it covered', formatCents(plan.stillOwedCents), true));
  } else {
    rows.push(row('Nothing to refund or pay', 'The return premium pays off what was owed', formatCents(0), true));
  }
  return rows;
}

/** "$400.00 from the latest payment, then $148.00 from the first"; null when nothing goes back. */
export function removeRefundPieces(plan: RemoveCoverPlan): string | null {
  const n = plan.refunds.length;
  if (n === 0) return null;
  return plan.refunds
    .map((r, i) => {
      const which = i === 0 ? 'the latest payment' : i === n - 1 ? 'the first' : 'the one before';
      return `${formatCents(r.amountCents)} from ${which}`;
    })
    .join(', then ');
}

/** "What Maya gets back", or "What Maya still owes". */
export function removePreviewHeading(plan: RemoveCoverPlan | null, ownerName: string): string {
  const who = firstName(ownerName) ?? 'the customer';
  return plan && plan.stillOwedCents > 0 ? `What ${who} still owes` : `What ${who} gets back`;
}

/** P1: when product liability ends, in the past tense once the day has come. */
export function coverRemovedSentence(endedOn: IsoDate, today: IsoDate): string {
  return `Your ${COVER.toLowerCase()} ${hasEnded(endedOn, today) ? 'ended' : 'ends'} on ${formatDate(endedOn)}.`;
}

/** The day product liability was removed from these covers, or null while it runs (or was never bought). */
export function removedOn(covers: Coverage[]): IsoDate | null {
  return covers.find((c) => c.kind === 'PRODUCT_LIABILITY' && c.endedOn)?.endedOn ?? null;
}

/** P1's "Refund on its way" card after a removal: why the money is coming back. */
export function refundAfterRemoval(endedOn: IsoDate, today: IsoDate): string {
  const days = hasEnded(endedOn, today) ? "days it didn't cover" : "days it won't cover";
  return `${coverRemovedSentence(endedOn, today).slice(0, -1)}, so you'd paid for ${days}. That return premium is on its way. Banks take 5–10 days to show a refund.`;
}

/** The one sentence the customer gets about this removal. */
export function customerWillSeeRemoval(plan: RemoveCoverPlan, today: IsoDate): string {
  const ends = coverRemovedSentence(plan.endDate, today);
  if (plan.refundTotalCents > 0) {
    return `${ends} We're sending ${formatCents(plan.refundTotalCents)} back to your ${card(mainCard(plan.refunds))}.`;
  }
  if (plan.stillOwedCents > 0) {
    return `${ends} You now owe ${formatCents(plan.stillOwedCents)} for the days it covered.`;
  }
  return `${ends} You have nothing to pay.`;
}

/** The save button, naming the refund when there is one. */
export function removeSaveLabel(plan: RemoveCoverPlan): string {
  const remove = `Remove ${COVER.toLowerCase()}`;
  return plan.refundTotalCents > 0 ? `${remove} and refund ${formatCents(plan.refundTotalCents)}` : remove;
}

/** The confirm dialog. */
export function removeConfirm(plan: RemoveCoverPlan, ownerName: string): { title: string; sentence: string } {
  const who = firstName(ownerName) ?? 'The customer';
  let money: string;
  if (plan.refundTotalCents > 0) money = `${who} gets ${formatCents(plan.refundTotalCents)} back`;
  else if (plan.stillOwedCents > 0) money = `${who} still owes ${formatCents(plan.stillOwedCents)} for the days it covered`;
  else money = `${who} owes nothing`;
  return {
    title: `Remove ${COVER.toLowerCase()}?`,
    sentence: `${COVER} ends on ${formatDate(plan.endDate)}. ${money} and sees your reason.`,
  };
}

/** The once-only banner on B3 after removing, and the ids it posted. */
export function removedBanner(ownerName: string, saved: CoverRemoved, today: IsoDate): { message: string; refs: string } {
  const who = firstName(ownerName);
  const ends = `It ${hasEnded(saved.endDate, today) ? 'ended' : 'ends'} on ${formatDate(saved.endDate)}`;
  let message: string;
  if (saved.refundTotalCents > 0) {
    const whose = who ? `${who}'s` : "the customer's";
    message = `${COVER} removed. ${ends} and ${formatCents(saved.refundTotalCents)} is on its way to ${whose} card.`;
  } else if (saved.balanceCents > 0) {
    message = `${COVER} removed. ${ends}. ${who ?? 'The customer'} still owes ${formatCents(saved.balanceCents)} for the days it covered.`;
  } else {
    message = `${COVER} removed. ${ends}. ${who ?? 'The customer'} has nothing to pay.`;
  }
  return { message, refs: `Endorsement ${saved.transactionDisplayId} · ${saved.creditDisplayId}` };
}
