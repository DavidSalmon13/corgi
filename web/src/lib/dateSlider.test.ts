import { describe, expect, it } from 'vitest';
import { coveredDaysLine, dayAtFraction, sliderValueText, stepDate } from './dateSlider';

// The worked example: a policy from Sep 1, 2026 to Sep 1, 2027 (365 days); the field allows Sep 1 to Aug 31.
const START = '2026-09-01';
const END = '2027-09-01';
const MIN = '2026-09-01';
const MAX = '2027-08-31';

describe('dayAtFraction', () => {
  it('snaps a point on the bar to the nearest whole day', () => {
    expect(dayAtFraction(START, END, MIN, MAX, 0)).toBe('2026-09-01');
    expect(dayAtFraction(START, END, MIN, MAX, 37 / 365)).toBe('2026-10-08');
    expect(dayAtFraction(START, END, MIN, MAX, 37.4 / 365)).toBe('2026-10-08');
    expect(dayAtFraction(START, END, MIN, MAX, 37.6 / 365)).toBe('2026-10-09');
  });

  it('never goes past the limits the date field allows', () => {
    expect(dayAtFraction(START, END, MIN, MAX, -0.2)).toBe(MIN);
    expect(dayAtFraction(START, END, MIN, MAX, 1)).toBe(MAX);
    expect(dayAtFraction(START, END, MIN, MAX, 1.5)).toBe(MAX);
  });
});

describe('stepDate', () => {
  const step = (date: string, key: string) => stepDate(date, key, MIN, MAX);

  it('moves one day with the arrow keys', () => {
    expect(step('2026-10-08', 'ArrowRight')).toBe('2026-10-09');
    expect(step('2026-10-08', 'ArrowUp')).toBe('2026-10-09');
    expect(step('2026-10-08', 'ArrowLeft')).toBe('2026-10-07');
    expect(step('2026-10-08', 'ArrowDown')).toBe('2026-10-07');
  });

  it('moves one calendar month with Page Up and Page Down, keeping to the end of a shorter month', () => {
    expect(step('2026-10-08', 'PageUp')).toBe('2026-11-08');
    expect(step('2026-10-08', 'PageDown')).toBe('2026-09-08');
    expect(step('2027-01-31', 'PageUp')).toBe('2027-02-28');
    expect(step('2026-12-15', 'PageUp')).toBe('2027-01-15');
  });

  it('jumps to the limits with Home and End and stops at them', () => {
    expect(step('2026-10-08', 'Home')).toBe(MIN);
    expect(step('2026-10-08', 'End')).toBe(MAX);
    expect(step(MIN, 'ArrowLeft')).toBe(MIN);
    expect(step('2027-08-15', 'PageUp')).toBe(MAX);
  });

  it('ignores other keys', () => {
    expect(step('2026-10-08', 'a')).toBeNull();
  });
});

describe('coveredDaysLine', () => {
  it('counts the corrected days out of the policy period and compares them with now', () => {
    expect(coveredDaysLine(START, END, '2026-10-15', '2026-10-08')).toBe('328 of 365 days · 7 more than now');
    expect(coveredDaysLine(START, END, '2026-10-15', '2026-10-22')).toBe('314 of 365 days · 7 fewer than now');
    expect(coveredDaysLine(START, END, '2026-10-15', '2026-10-16')).toBe('320 of 365 days · 1 fewer than now');
    expect(coveredDaysLine(START, END, '2026-10-15', '2026-10-15')).toBe('321 of 365 days · same as now');
  });
});

describe('sliderValueText', () => {
  it('reads the date and the day count', () => {
    expect(sliderValueText(START, END, '2026-10-08')).toBe('Oct 8, 2026, 328 of 365 days');
  });
});
