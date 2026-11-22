import { describe, expect, it } from 'vitest';
import { coverDates, dueReasonText, periodLine, protectionSentence } from './myCoverCopy';

// covers AC10, T18 — the policyholder reads what they owe, why, and what they're protected for
describe('policyholder home copy', () => {
  it('explains why money is due in plain words', () => {
    expect(dueReasonText('FIRST_YEAR')).toBe('Your first year of cover.');
    expect(dueReasonText('CHANGE')).toBe('A change to your cover left a balance to pay.');
    expect(dueReasonText('NOTHING_DUE')).toBe('');
  });

  it('builds the protection sentence from what the business does', () => {
    expect(protectionSentence('Bakery and café', false)).toBe(
      "You're covered if someone is hurt or their property is damaged because of your business (bakery and café).",
    );
    expect(protectionSentence('Bakery and café', true)).toBe(
      "You're covered if someone is hurt or their property is damaged because of your business (bakery and café), or because of something you sell.",
    );
  });

  it('falls back gracefully when the description is blank', () => {
    expect(protectionSentence('  ', false)).toBe(
      "You're covered if someone is hurt or their property is damaged because of your business.",
    );
  });

  it('keeps a short form of the description and the cover dates', () => {
    expect(periodLine('2026-09-01', '2027-09-01')).toBe('Sep 1, 2026 – Sep 1, 2027');
    expect(coverDates('2026-10-08')).toBe('from Oct 8');
  });
});
