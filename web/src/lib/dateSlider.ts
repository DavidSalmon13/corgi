import { addDays, daysBetween, formatDate, type IsoDate } from './dates';

function clamp(date: IsoDate, min: IsoDate, max: IsoDate): IsoDate {
  if (date < min) return min;
  if (date > max) return max;
  return date;
}

/**
 * The day under a point on the bar, which spans the policy period [start, end). `fraction` is 0 at the left edge and 1
 * at the right; it snaps to the nearest whole day and stays within [min, max], the limits the date field allows.
 */
export function dayAtFraction(start: IsoDate, end: IsoDate, min: IsoDate, max: IsoDate, fraction: number): IsoDate {
  const day = Math.round(fraction * daysBetween(start, end));
  return clamp(addDays(start, day), min, max);
}

/** The same day one calendar month later or earlier, kept to the last day of a shorter month (Jan 31 → Feb 28). */
function addMonths(date: IsoDate, n: number): IsoDate {
  const [y, m, d] = date.split('-').map(Number);
  const first = new Date(Date.UTC(y, m - 1 + n, 1));
  const lastDay = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + 1, 0)).getUTCDate();
  first.setUTCDate(Math.min(d, lastDay));
  return first.toISOString().slice(0, 10);
}

/** Where a key moves the handle, as a slider does: a day, a month, or to a limit. Null for any other key. */
export function stepDate(date: IsoDate, key: string, min: IsoDate, max: IsoDate): IsoDate | null {
  const next: Record<string, () => IsoDate> = {
    ArrowRight: () => addDays(date, 1),
    ArrowUp: () => addDays(date, 1),
    ArrowLeft: () => addDays(date, -1),
    ArrowDown: () => addDays(date, -1),
    PageUp: () => addMonths(date, 1),
    PageDown: () => addMonths(date, -1),
    Home: () => min,
    End: () => max,
  };
  const move = next[key];
  return move ? clamp(move(), min, max) : null;
}

/** "328 of 365 days · 7 more than now": the days the corrected cover is charged for, the pro-rata share of the year. */
export function coveredDaysLine(start: IsoDate, end: IsoDate, currentFrom: IsoDate, newFrom: IsoDate): string {
  const days = daysBetween(newFrom, end);
  const diff = daysBetween(newFrom, currentFrom);
  const compare = diff === 0 ? 'same as now' : `${Math.abs(diff)} ${diff > 0 ? 'more' : 'fewer'} than now`;
  return `${days} of ${daysBetween(start, end)} days · ${compare}`;
}

/** What a screen reader says for the handle: "Oct 8, 2026, 328 of 365 days". */
export function sliderValueText(start: IsoDate, end: IsoDate, date: IsoDate): string {
  return `${formatDate(date)}, ${daysBetween(date, end)} of ${daysBetween(start, end)} days`;
}
