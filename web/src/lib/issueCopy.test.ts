import { describe, expect, it } from 'vitest';
import { customerWillSee } from './issueCopy';

// covers AC3, T15 — the sentence the broker sees is what the customer will read
describe('customerWillSee', () => {
  it('states the cover dates and the price for the year', () => {
    expect(customerWillSee({ start: '2026-09-01', end: '2027-09-01', totalCents: 120_000, extra: false })).toBe(
      "You're covered from Sep 1, 2026 to Sep 1, 2027. Your premium is $1,200.00 for the policy period.",
    );
  });

  it('mentions the extra cover when it is included', () => {
    expect(customerWillSee({ start: '2026-09-01', end: '2027-09-01', totalCents: 193_000, extra: true })).toBe(
      "You're covered from Sep 1, 2026 to Sep 1, 2027, including the products you sell. Your premium is $1,930.00 for the policy period.",
    );
  });
});
