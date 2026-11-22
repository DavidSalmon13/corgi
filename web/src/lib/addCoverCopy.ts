import type { ChargeCalc } from '../api/types';
import { formatDate, formatShortDate, toUtcMs, type IsoDate } from './dates';
import { formatCents } from './money';

/** The daily price, only when the yearly price divides into whole cents: $730.00 over 365 days is $2.00. */
function perDayCents(annualCents: number, termDays: number): number | null {
  return annualCents % termDays === 0 ? annualCents / termDays : null;
}

function firstName(ownerName: string): string | null {
  return ownerName.trim().split(/\s+/)[0] || null;
}

/** "Product liability ($730.00 a year, $2.00 a day)" — the one extra cover B4 offers. */
export function coverOptionLabel(annualCents: number, termDays: number): string {
  const perDay = perDayCents(annualCents, termDays);
  const daily = perDay === null ? '' : `, ${formatCents(perDay)} a day`;
  return `Product liability (${formatCents(annualCents)} a year${daily})`;
}

/** "$730.00 × 321 ÷ 365 · charged by the day": the yearly price × days covered ÷ days in the policy year. */
export function calcLine(calc: ChargeCalc): string {
  return `${formatCents(calc.annualCents)} × ${calc.days} ÷ ${calc.termDays} · charged by the day`;
}

/** The sentence the customer will read. The daily price appears only when it's exact, so the numbers always add up. */
export function customerWillSeeAddCover(from: IsoDate, calc: ChargeCalc): string {
  const perDay = perDayCents(calc.annualCents, calc.termDays);
  const days = `${calc.days} ${calc.days === 1 ? 'day' : 'days'}`;
  const atPerDay = perDay === null ? '' : ` at ${formatCents(perDay)} a day`;
  return `You owe ${formatCents(calc.amountCents)} for your new extra cover: ${days} from ${formatShortDate(from)}${atPerDay}.`;
}

/** "Add cover and ask Maya to pay" */
export function saveLabel(ownerName: string): string {
  return `Add cover and ask ${firstName(ownerName) ?? 'the customer'} to pay`;
}

/** The banner on B3 after saving: "Extra cover added. Maya now owes $1,842.00." */
export function savedBanner(ownerName: string, balanceCents: number): string {
  const who = firstName(ownerName) ?? 'The customer';
  return balanceCents > 0
    ? `Extra cover added. ${who} now owes ${formatCents(balanceCents)}.`
    : `Extra cover added. ${who} has nothing to pay right now.`;
}

/** "Runs to the end of the cover period, Sep 1, 2027." */
export function startsOnHint(end: IsoDate): string {
  return `Runs to the end of the cover period, ${formatDate(end)}.`;
}

/** Today if it falls inside the cover period (start inclusive, end exclusive), otherwise the policy start. */
export function defaultStart(today: IsoDate, start: IsoDate, end: IsoDate): IsoDate {
  const t = toUtcMs(today);
  return t >= toUtcMs(start) && t < toUtcMs(end) ? today : start;
}
