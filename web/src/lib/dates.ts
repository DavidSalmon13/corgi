/**
 * Calendar dates arrive from the API as ISO `YYYY-MM-DD` with no time of day. They are handled in UTC
 * so a viewer's timezone can never move Sep 1 to Aug 31.
 */
export type IsoDate = string;

const DAY_MS = 86_400_000;
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

export function toUtcMs(date: IsoDate): number {
  const [y, m, d] = date.split('-').map(Number);
  return Date.UTC(y, m - 1, d);
}

/** Days from `from` up to but not including `to`, the way pricing counts them. */
export function daysBetween(from: IsoDate, to: IsoDate): number {
  return Math.round((toUtcMs(to) - toUtcMs(from)) / DAY_MS);
}

/** "Sep 1, 2026" */
export function formatDate(date: IsoDate): string {
  const d = new Date(toUtcMs(date));
  return `${MONTHS[d.getUTCMonth()]} ${d.getUTCDate()}, ${d.getUTCFullYear()}`;
}

/** "Sep 1" */
export function formatShortDate(date: IsoDate): string {
  const d = new Date(toUtcMs(date));
  return `${MONTHS[d.getUTCMonth()]} ${d.getUTCDate()}`;
}

export function monthLabel(monthIndex: number): string {
  return MONTHS[monthIndex];
}
