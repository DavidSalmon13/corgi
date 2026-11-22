import { describe, expect, it } from 'vitest';
import { monthTicks, positionPct, spanPct } from './timeline';

const START = '2026-09-01';
const END = '2027-09-01';

// covers AC9, AC10, T13 — percentages match design/plainly/B3-Workspace.dc.html
describe('timeline geometry', () => {
  it('places a date as a share of the cover period', () => {
    expect(positionPct(START, END, '2026-09-01')).toBe(0);
    expect(positionPct(START, END, '2026-11-22')).toBeCloseTo(22.47, 2);
    expect(positionPct(START, END, '2026-10-08')).toBeCloseTo(10.14, 2);
    expect(positionPct(START, END, '2027-09-01')).toBe(100);
  });

  it('clamps dates outside the period', () => {
    expect(positionPct(START, END, '2026-01-01')).toBe(0);
    expect(positionPct(START, END, '2028-01-01')).toBe(100);
  });

  it('turns a cover into left and width', () => {
    const s = spanPct(START, END, '2026-10-08', '2027-09-01');
    expect(s.left).toBeCloseTo(10.14, 2);
    expect(s.width).toBeCloseTo(89.86, 2);
  });

  it('puts a tick at the start of each month in the period', () => {
    const ticks = monthTicks(START, END);
    expect(ticks.map((t) => t.label)).toEqual(
      ['Sep', 'Oct', 'Nov', 'Dec', 'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug'],
    );
    expect(ticks[1].pct).toBeCloseTo(8.22, 2);
    expect(ticks[3].pct).toBeCloseTo(24.93, 2);
  });
});
