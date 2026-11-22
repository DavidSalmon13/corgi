import { describe, expect, it } from 'vitest';
import {
  calcLine,
  coverOptionLabel,
  customerWillSeeAddCover,
  defaultStart,
  saveLabel,
  savedBanner,
  startsOnHint,
} from './addCoverCopy';

// covers AC2, AC3, AC6, T3 — what the broker reads on B4 before and after adding extra cover
describe('add cover copy', () => {
  const oct15 = { annualCents: 73_000, days: 321, termDays: 365, amountCents: 64_200 };

  it('names the one cover on offer with its yearly and daily price', () => {
    expect(coverOptionLabel(73_000, 365)).toBe('Product liability ($730.00 a year, $2.00 a day)');
  });

  it('leaves out the daily price when it is not a whole number of cents', () => {
    expect(coverOptionLabel(73_000, 366)).toBe('Product liability ($730.00 a year)');
  });

  it('shows the arithmetic behind the charge in plain words', () => {
    expect(calcLine(oct15)).toBe('$730.00 × 321 ÷ 365 · charged by the day');
  });

  it('tells the customer what they owe, for how many days, at what daily price', () => {
    expect(customerWillSeeAddCover('2026-10-15', oct15)).toBe(
      'You owe $642.00 for your new extra cover: 321 days from Oct 15 at $2.00 a day.',
    );
  });

  it('says "1 day" for a start on the last day', () => {
    expect(customerWillSeeAddCover('2027-08-31', { annualCents: 73_000, days: 1, termDays: 365, amountCents: 200 })).toBe(
      'You owe $2.00 for your new extra cover: 1 day from Aug 31 at $2.00 a day.',
    );
  });

  it('drops the daily price in a 366-day year, where it would not be exact', () => {
    expect(customerWillSeeAddCover('2027-10-15', { annualCents: 73_000, days: 322, termDays: 366, amountCents: 64_224 })).toBe(
      'You owe $642.24 for your new extra cover: 322 days from Oct 15.',
    );
  });

  it('asks the broker to confirm by the owner’s first name', () => {
    expect(saveLabel('Maya Okafor')).toBe('Add cover and ask Maya to pay');
    expect(saveLabel('  ')).toBe('Add cover and ask the customer to pay');
  });

  it('confirms the save with the new balance', () => {
    expect(savedBanner('Maya Okafor', 184_200)).toBe('Extra cover added. Maya now owes $1,842.00.');
    expect(savedBanner('Maya Okafor', 0)).toBe('Extra cover added. Maya has nothing to pay right now.');
  });

  it('says when the extra cover runs to', () => {
    expect(startsOnHint('2027-09-01')).toBe('Runs to the end of the cover period, Sep 1, 2027.');
  });

  it('starts on today when today is inside the cover period, else on the policy start', () => {
    expect(defaultStart('2026-11-22', '2026-09-01', '2027-09-01')).toBe('2026-11-22');
    expect(defaultStart('2026-08-01', '2026-09-01', '2027-09-01')).toBe('2026-09-01');
    expect(defaultStart('2027-09-01', '2026-09-01', '2027-09-01')).toBe('2026-09-01');
  });
});
