import { describe, expect, it } from 'vitest';
import {
  billHeading,
  calcArithmetic,
  calcLine,
  coverOptionLabel,
  customerWillSeeAddCover,
  defaultStart,
  saveLabel,
  savedBanner,
  lastEffectiveDate,
  policyPeriodLine,
  startsOnHint,
} from './addCoverCopy';

// covers AC2, AC3, AC6, T3 — what the broker reads on B4 before and after adding an endorsement
describe('add cover copy', () => {
  const oct15 = { annualCents: 73_000, days: 321, termDays: 365, amountCents: 64_200 };

  it('names the one endorsement on offer with its annual premium and daily price', () => {
    expect(coverOptionLabel(73_000, 365)).toBe('Product liability ($730.00 annual premium, $2.00 a day)');
  });

  it('leaves out the daily price when it is not a whole number of cents', () => {
    expect(coverOptionLabel(73_000, 366)).toBe('Product liability ($730.00 annual premium)');
  });

  it('shows the arithmetic behind the pro-rata charge', () => {
    expect(calcLine(oct15)).toBe('$730.00 × 321 ÷ 365 · pro-rata');
    expect(calcArithmetic(oct15)).toBe('$730.00 × 321 ÷ 365');
  });

  it('heads the money preview with the owner’s first name', () => {
    expect(billHeading('Maya Okafor')).toBe("What this does to Maya's bill");
    expect(billHeading('')).toBe("What this does to the customer's bill");
  });

  it('tells the customer what they owe, for how many days, at what daily price', () => {
    expect(customerWillSeeAddCover('2026-10-15', oct15)).toBe(
      'You owe $642.00 for your new product liability endorsement: 321 days from Oct 15 at $2.00 a day.',
    );
  });

  it('says "1 day" for a start on the last day', () => {
    expect(customerWillSeeAddCover('2027-08-31', { annualCents: 73_000, days: 1, termDays: 365, amountCents: 200 })).toBe(
      'You owe $2.00 for your new product liability endorsement: 1 day from Aug 31 at $2.00 a day.',
    );
  });

  it('drops the daily price in a 366-day year, where it would not be exact', () => {
    expect(customerWillSeeAddCover('2027-10-15', { annualCents: 73_000, days: 322, termDays: 366, amountCents: 64_224 })).toBe(
      'You owe $642.24 for your new product liability endorsement: 322 days from Oct 15.',
    );
  });

  it('asks the broker to confirm by the owner’s first name', () => {
    expect(saveLabel('Maya Okafor')).toBe('Add endorsement and ask Maya to pay');
    expect(saveLabel('  ')).toBe('Add endorsement and ask the customer to pay');
  });

  it('confirms the save with the new balance', () => {
    expect(savedBanner('Maya Okafor', 184_200)).toBe('Endorsement added. Maya now owes $1,842.00.');
    expect(savedBanner('Maya Okafor', 0)).toBe('Endorsement added. Maya has nothing to pay right now.');
    expect(savedBanner('  ', 58_000)).toBe('Endorsement added. The customer now owes $580.00.');
  });

  it('gives the full range an effective date may fall in, and when the endorsement ends', () => {
    expect(startsOnHint('2026-09-01', '2027-09-01')).toBe(
      'Any day from Sep 1, 2026 to Aug 31, 2027. The endorsement runs to the end of the policy period, Sep 1, 2027.',
    );
  });

  it('stops effective dates the day before the policy period ends, across a month and year end', () => {
    expect(lastEffectiveDate('2027-09-01')).toBe('2027-08-31');
    expect(lastEffectiveDate('2027-01-01')).toBe('2026-12-31');
    expect(lastEffectiveDate('2028-03-01')).toBe('2028-02-29');
  });

  it('shows the policy period with the days left, or when it starts or ended', () => {
    expect(policyPeriodLine('2026-09-01', '2027-09-01', '2026-11-21')).toBe('Sep 1, 2026 → Sep 1, 2027 · 284 days left');
    expect(policyPeriodLine('2026-09-01', '2027-09-01', '2027-08-31')).toBe('Sep 1, 2026 → Sep 1, 2027 · 1 day left');
    expect(policyPeriodLine('2026-09-01', '2027-09-01', '2026-08-31')).toBe('Sep 1, 2026 → Sep 1, 2027 · starts in 1 day');
    expect(policyPeriodLine('2026-09-01', '2027-09-01', '2027-09-01')).toBe('Sep 1, 2026 → Sep 1, 2027 · ended');
  });

  it('starts on today when today is inside the policy period, else on the policy start', () => {
    expect(defaultStart('2026-11-22', '2026-09-01', '2027-09-01')).toBe('2026-11-22');
    expect(defaultStart('2026-08-01', '2026-09-01', '2027-09-01')).toBe('2026-09-01');
    expect(defaultStart('2027-09-01', '2026-09-01', '2027-09-01')).toBe('2026-09-01');
  });
});
