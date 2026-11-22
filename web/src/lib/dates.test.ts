import { describe, expect, it } from 'vitest';
import { daysBetween, formatDate, formatShortDate } from './dates';

// covers AC9, AC10, T13
describe('dates', () => {
  it('formats ISO dates the way the designs do, without timezone drift', () => {
    expect(formatDate('2026-09-01')).toBe('Sep 1, 2026');
    expect(formatDate('2027-01-31')).toBe('Jan 31, 2027');
    expect(formatShortDate('2026-11-22')).toBe('Nov 22');
  });

  it('counts days end-exclusive, matching pricing', () => {
    expect(daysBetween('2026-09-01', '2027-09-01')).toBe(365);
    expect(daysBetween('2026-10-08', '2027-09-01')).toBe(328);
    expect(daysBetween('2027-09-01', '2028-09-01')).toBe(366);
    expect(daysBetween('2026-09-01', '2026-09-01')).toBe(0);
  });
});
