import { daysBetween, monthLabel, toUtcMs, type IsoDate } from './dates';

/** Where `date` falls in the cover period, as a percentage from the left (clamped to 0–100). */
export function positionPct(start: IsoDate, end: IsoDate, date: IsoDate): number {
  const total = daysBetween(start, end);
  if (total <= 0) return 0;
  const pct = (daysBetween(start, date) / total) * 100;
  return Math.min(100, Math.max(0, pct));
}

/** A cover's bar: left edge and width, both as percentages of the period. */
export function spanPct(start: IsoDate, end: IsoDate, from: IsoDate, to: IsoDate): { left: number; width: number } {
  const left = positionPct(start, end, from);
  const right = positionPct(start, end, to);
  return { left, width: Math.max(0, right - left) };
}

/** One tick at the first day of each month inside [start, end). The period start always gets one. */
export function monthTicks(start: IsoDate, end: IsoDate): { label: string; pct: number }[] {
  const ticks: { label: string; pct: number }[] = [];
  const s = new Date(toUtcMs(start));
  let y = s.getUTCFullYear();
  let m = s.getUTCMonth();
  const endMs = toUtcMs(end);
  ticks.push({ label: monthLabel(m), pct: 0 });
  for (;;) {
    m += 1;
    if (m === 12) {
      m = 0;
      y += 1;
    }
    const iso = `${y}-${String(m + 1).padStart(2, '0')}-01`;
    if (toUtcMs(iso) >= endMs) break;
    ticks.push({ label: monthLabel(m), pct: positionPct(start, end, iso) });
  }
  return ticks;
}

export function pct(n: number): string {
  return `${n.toFixed(2)}%`;
}
