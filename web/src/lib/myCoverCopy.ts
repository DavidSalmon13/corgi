import type { DueReason } from '../api/types';
import { formatDate, formatShortDate, type IsoDate } from './dates';

/** Why the policyholder owes money, as a short sentence under the amount. */
export function dueReasonText(reason: DueReason): string {
  switch (reason) {
    case 'FIRST_YEAR':
      return 'Your first year of cover.';
    case 'CHANGE':
      return 'A change to your cover left a balance to pay.';
    case 'NOTHING_DUE':
      return '';
  }
}

/** What they're protected for, built from what the business does and whether extra cover is on. */
export function protectionSentence(businessDescription: string, hasExtra: boolean): string {
  const what = businessDescription.trim();
  const business = what ? `your business (${what.charAt(0).toLowerCase()}${what.slice(1)})` : 'your business';
  const extra = hasExtra ? ', or because of something you sell' : '';
  return `You're covered if someone is hurt or their property is damaged because of ${business}${extra}.`;
}

/** "Sep 1, 2026 – Sep 1, 2027" */
export function periodLine(start: IsoDate, end: IsoDate): string {
  return `${formatDate(start)} – ${formatDate(end)}`;
}

/** "from Oct 8" */
export function coverDates(from: IsoDate): string {
  return `from ${formatShortDate(from)}`;
}
