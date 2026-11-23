import type { DueReason, RefundWaiting } from '../api/types';
import { formatDate, formatShortDate, type IsoDate } from './dates';
import { formatCents } from './money';

/** Why the policyholder owes money, as a short sentence under the amount. */
export function dueReasonText(reason: DueReason, latestChangeFrom: IsoDate | null = null): string {
  switch (reason) {
    case 'FIRST_YEAR':
      return 'Your first year of coverage.';
    case 'EXTRA_COVER':
      return latestChangeFrom
        ? `Your new product liability endorsement, pro-rata from ${formatShortDate(latestChangeFrom)}.`
        : 'Your new product liability endorsement, pro-rata.';
    case 'CORRECTION':
      return 'We corrected the effective date of your product liability endorsement.';
    case 'CANCELLATION':
      return 'For the days you were covered before your policy was cancelled.';
    case 'COVER_REMOVED':
      return latestChangeFrom
        ? `For the days your product liability covered, because it was removed from ${formatShortDate(latestChangeFrom)}.`
        : 'For the days your product liability covered, because it was removed.';
    case 'CHANGE':
      return 'A mid-term change left a balance to pay.';
    case 'NOTHING_DUE':
      return '';
  }
}

/**
 * What they're protected for, built from what the business does and whether the product liability endorsement is on. On a cancelled policy
 * it also says when that cover ends, in the past tense once the day has come.
 */
export function protectionSentence(
  businessDescription: string,
  hasExtra: boolean,
  cancelled: { endsOn: IsoDate; today: IsoDate } | null = null,
): string {
  const what = businessDescription.trim();
  const business = what ? `your business (${what.charAt(0).toLowerCase()}${what.slice(1)})` : 'your business';
  const ended = cancelled != null && cancelled.endsOn <= cancelled.today;
  if (ended) {
    const extra = hasExtra ? ', or because of something you sold' : '';
    return `You were covered if someone was hurt or their property was damaged because of ${business}${extra}. That cover ended on ${formatDate(cancelled.endsOn)}.`;
  }
  const extra = hasExtra ? ', or because of something you sell' : '';
  const sentence = `You're covered if someone is hurt or their property is damaged because of ${business}${extra}.`;
  return cancelled ? `${sentence} That cover ends on ${formatDate(cancelled.endsOn)}.` : sentence;
}

/** "Sep 1, 2026 – Sep 1, 2027" */
export function periodLine(start: IsoDate, end: IsoDate): string {
  return `${formatDate(start)} – ${formatDate(end)}`;
}

/** "from Oct 8", or "Sep 1 – Dec 1" when the cover stops early ("Never started" if it stopped before its start). */
export function coverDates(from: IsoDate, to: IsoDate | null = null): string {
  if (to && to <= from) return 'Never started';
  return to ? `${formatShortDate(from)} – ${formatShortDate(to)}` : `from ${formatShortDate(from)}`;
}

/** P1's "Refund on its way" sentence: how much, and which card it goes back to. */
export function refundOnItsWay(refund: RefundWaiting): string {
  const card = refund.cardLast4 ? `your card ending ${refund.cardLast4}` : 'your card';
  return `We owe you ${formatCents(refund.amountCents)}. It's on its way to ${card}.`;
}
