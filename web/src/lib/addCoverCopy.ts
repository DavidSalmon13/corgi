import type { ChargeCalc } from '../api/types';
import { addDays, daysBetween, formatDate, formatShortDate, toUtcMs, type IsoDate } from './dates';
import { formatCents } from './money';

/** The daily price, only when the annual premium divides into whole cents: $730.00 over 365 days is $2.00. */
function perDayCents(annualCents: number, termDays: number): number | null {
  return annualCents % termDays === 0 ? annualCents / termDays : null;
}

function firstName(ownerName: string): string | null {
  return ownerName.trim().split(/\s+/)[0] || null;
}

/** "Product liability ($730.00 annual premium, $2.00 a day)": the one endorsement B4 offers. */
export function coverOptionLabel(annualCents: number, termDays: number): string {
  const perDay = perDayCents(annualCents, termDays);
  const daily = perDay === null ? '' : `, ${formatCents(perDay)} a day`;
  return `Product liability (${formatCents(annualCents)} annual premium${daily})`;
}

/** "$730.00 × 321 ÷ 365": the annual premium × days covered ÷ days in the policy period. */
export function calcArithmetic(calc: ChargeCalc): string {
  return `${formatCents(calc.annualCents)} × ${calc.days} ÷ ${calc.termDays}`;
}

/** "$730.00 × 321 ÷ 365 · pro-rata" */
export function calcLine(calc: ChargeCalc): string {
  return `${calcArithmetic(calc)} · pro-rata`;
}

/** "What this does to Maya's bill" */
export function billHeading(ownerName: string): string {
  const who = firstName(ownerName);
  return `What this does to ${who ? `${who}'s` : "the customer's"} bill`;
}

/** The sentence the customer will read. The daily price appears only when it's exact, so the numbers always add up. */
export function customerWillSeeAddCover(from: IsoDate, calc: ChargeCalc): string {
  const perDay = perDayCents(calc.annualCents, calc.termDays);
  const days = `${calc.days} ${calc.days === 1 ? 'day' : 'days'}`;
  const atPerDay = perDay === null ? '' : ` at ${formatCents(perDay)} a day`;
  return `You owe ${formatCents(calc.amountCents)} for your new product liability endorsement: ${days} from ${formatShortDate(from)}${atPerDay}.`;
}

/** "Add endorsement and ask Maya to pay" */
export function saveLabel(ownerName: string): string {
  return `Add endorsement and ask ${firstName(ownerName) ?? 'the customer'} to pay`;
}

/** The banner on B3 after saving: "Endorsement added. Maya now owes $1,842.00." */
export function savedBanner(ownerName: string, balanceCents: number): string {
  const who = firstName(ownerName) ?? 'The customer';
  return balanceCents > 0
    ? `Endorsement added. ${who} now owes ${formatCents(balanceCents)}.`
    : `Endorsement added. ${who} has nothing to pay right now.`;
}

/** The last day an effective date may fall on: the day before the policy period ends. */
export function lastEffectiveDate(end: IsoDate): IsoDate {
  return addDays(end, -1);
}

/** "Any day from Sep 1, 2026 to Aug 31, 2027. The endorsement runs to the end of the policy period, Sep 1, 2027." */
export function startsOnHint(start: IsoDate, end: IsoDate): string {
  return (
    `Any day from ${formatDate(start)} to ${formatDate(lastEffectiveDate(end))}. ` +
    `The endorsement runs to the end of the policy period, ${formatDate(end)}.`
  );
}

/** "Sep 1, 2026 → Sep 1, 2027 · 284 days left", or "· ended" / "· starts in 12 days" outside the policy period. */
export function policyPeriodLine(start: IsoDate, end: IsoDate, today: IsoDate): string {
  const period = `${formatDate(start)} → ${formatDate(end)}`;
  if (toUtcMs(today) < toUtcMs(start)) {
    const n = daysBetween(today, start);
    return `${period} · starts in ${n} ${n === 1 ? 'day' : 'days'}`;
  }
  if (toUtcMs(today) >= toUtcMs(end)) return `${period} · ended`;
  const left = daysBetween(today, end);
  return `${period} · ${left} ${left === 1 ? 'day' : 'days'} left`;
}

/** Today if it falls inside the policy period (start inclusive, end exclusive), otherwise the policy start. */
export function defaultStart(today: IsoDate, start: IsoDate, end: IsoDate): IsoDate {
  const t = toUtcMs(today);
  return t >= toUtcMs(start) && t < toUtcMs(end) ? today : start;
}
