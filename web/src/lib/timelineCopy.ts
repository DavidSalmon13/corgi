import type { CoverKind, StatementCorrection, TimelineCover, TimelineTerm } from '../api/types';
import { formatDate, formatShortDate, type IsoDate } from './dates';
import { formatCents } from './money';

// P2 "Coverage timeline" wording. Standard terms throughout; the page renders these through `Glossed`, so each
// term carries its "?" explanation.

const NAMES: Record<CoverKind, string> = {
  BASE: 'General liability',
  PRODUCT_LIABILITY: 'Product liability',
};

const WHAT: Record<CoverKind, string> = {
  BASE: 'Pays if someone is hurt or their property is damaged because of your business.',
  PRODUCT_LIABILITY: 'Pays if something you sell hurts someone or damages their property.',
};

/** The cover's name on its band's detail card. */
export function coverName(kind: CoverKind): string {
  return NAMES[kind];
}

/** What the cover pays for, in one sentence. */
export function coverWhat(kind: CoverKind): string {
  return WHAT[kind];
}

/** The one-line answer under the title: what you're covered for, from when, or when it ended. */
export function timelineAnswer(term: TimelineTerm, covers: TimelineCover[]): string {
  if (term.cancelledOn) return `Your cover ended on ${formatDate(term.cancelledOn)}.`;
  const extra = covers.find((c) => c.kind === 'PRODUCT_LIABILITY');
  if (extra) return `You’re covered all year, plus product liability from ${formatShortDate(extra.from)}.`;
  return `You’re covered for the full policy period, ${formatDate(term.start)} to ${formatDate(term.end)}.`;
}

/** "Sep 1, 2027", or "Dec 1, 2026 (cancelled)" when the policy stopped early. */
export function endsText(cover: TimelineCover, term: TimelineTerm): string {
  if (term.cancelledOn && term.cancelledOn < cover.to) return `${formatDate(term.cancelledOn)} (cancelled)`;
  return formatDate(cover.to);
}

/** "$730.00 a year ($2.00 a day)" under the "Annual premium" label, leaving the daily price out when it isn't whole cents. */
export function priceText(cover: TimelineCover): string {
  const year = `${formatCents(cover.annualCents)} a year`;
  return cover.perDayCents == null ? year : `${year} (${formatCents(cover.perDayCents)} a day)`;
}

/** "328 days · $656.00", or "91 days kept · $299.18" after a cancellation. */
export function chargedText(cover: TimelineCover, term: TimelineTerm): string {
  const days = `${cover.daysKept} ${cover.daysKept === 1 ? 'day' : 'days'}`;
  return `${days}${term.cancelledOn ? ' kept' : ''} · ${formatCents(cover.chargedCents)}`;
}

/** The caption under the "today" line. */
export function todayLabel(today: IsoDate): string {
  return `Today, ${formatShortDate(today)}`;
}

/** The dashed outline where a corrected cover used to start. */
export function ghostLabel(previousStart: IsoDate): string {
  return `Effective date before the correction: ${formatShortDate(previousStart)}`;
}

/** The note under a cancelled timeline. */
export function stripedNote(cancelledOn: IsoDate): string {
  return `Striped: days after your coverage ended on ${formatShortDate(cancelledOn)}. They came back to you as return premium.`;
}

function howFar(days: number): string {
  if (days === 7) return 'a week';
  return `${days} ${days === 1 ? 'day' : 'days'}`;
}

/** "On Nov 5 we moved your product liability endorsement's effective date a week earlier, from Oct 15 to Oct 8." */
export function correctionSentence(c: StatementCorrection, kind: CoverKind): string {
  const which = kind === 'BASE' ? 'general liability' : 'product liability endorsement';
  const direction = c.dayDelta >= 0 ? 'earlier' : 'later';
  return (
    `On ${formatShortDate(c.recordedOn)} we moved your ${which}’s effective date ${howFar(Math.abs(c.dayDelta))} ${direction}, ` +
    `from ${formatShortDate(c.oldStart)} to ${formatShortDate(c.newStart)}.`
  );
}

/** The broker's reason for the correction, as written; null when none was given. */
export function correctionReason(c: StatementCorrection): string | null {
  return c.reason ? `The reason: ${c.reason}` : null;
}
