import { describe, expect, it } from 'vitest';
import { coverDates, dueReasonText, periodLine, protectionSentence, refundOnItsWay } from './myCoverCopy';

// covers AC10, T18 — the policyholder reads what they owe, why, and what they're protected for
describe('policyholder home copy', () => {
  it('explains why money is due in plain words', () => {
    expect(dueReasonText('FIRST_YEAR')).toBe('Your first year of cover.');
    expect(dueReasonText('CHANGE')).toBe('A change to your cover left a balance to pay.');
    expect(dueReasonText('NOTHING_DUE')).toBe('');
  });

  // covers add-coverage AC9, T3
  it('says new extra cover is why more is due, and from when', () => {
    expect(dueReasonText('EXTRA_COVER', '2026-10-15')).toBe('Your new extra cover, charged by the day from Oct 15.');
    expect(dueReasonText('EXTRA_COVER', null)).toBe('Your new extra cover, charged by the day.');
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

  // covers correct-effective-date AC13, T3 — after a correction, P1 says why, or that money is coming back
  it('says a corrected start date is why more is due', () => {
    expect(dueReasonText('CORRECTION', '2026-10-08')).toBe('We fixed when your extra cover started.');
  });

  it('says a refund is on its way, and to which card', () => {
    expect(refundOnItsWay({ amountCents: 1_400, cardLast4: '4242' })).toBe(
      "We owe you $14.00. It's on its way to your card ending 4242.",
    );
    expect(refundOnItsWay({ amountCents: 1_400, cardLast4: null })).toBe("We owe you $14.00. It's on its way to your card.");
  });
});
