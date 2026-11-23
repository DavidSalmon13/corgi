import { describe, expect, it } from 'vitest';
import { coverDates, dueReasonText, periodLine, protectionSentence, refundOnItsWay } from './myCoverCopy';

// covers AC10, T18 — the policyholder reads what they owe, why, and what they're protected for
describe('policyholder home copy', () => {
  it('explains why money is due in plain words', () => {
    expect(dueReasonText('FIRST_YEAR')).toBe('Your first year of coverage.');
    expect(dueReasonText('CHANGE')).toBe('A mid-term change left a balance to pay.');
    expect(dueReasonText('NOTHING_DUE')).toBe('');
  });

  // covers add-coverage AC9, T3
  it('says new extra cover is why more is due, and from when', () => {
    expect(dueReasonText('EXTRA_COVER', '2026-10-15')).toBe('Your new product liability endorsement, pro-rata from Oct 15.');
    expect(dueReasonText('EXTRA_COVER', null)).toBe('Your new product liability endorsement, pro-rata.');
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
    expect(dueReasonText('CORRECTION', '2026-10-08')).toBe('We corrected the effective date of your product liability endorsement.');
    expect(dueReasonText('CANCELLATION')).toBe('For the days you were covered before your policy was cancelled.');
  });

  it('says a refund is on its way, and to which card', () => {
    expect(refundOnItsWay({ amountCents: 1_400, cardLast4: '4242' })).toBe(
      "We owe you $14.00. It's on its way to your card ending 4242.",
    );
    expect(refundOnItsWay({ amountCents: 1_400, cardLast4: null })).toBe("We owe you $14.00. It's on its way to your card.");
  });

  // covers cancel-refund AC10, T11 — a cancelled policy's protection line and cover dates end on the cancel day
  it('says what they were covered for, and when it ended, once the policy is cancelled', () => {
    expect(protectionSentence('Bakery and café', true, { endsOn: '2026-12-01', today: '2026-12-02' })).toBe(
      'You were covered if someone was hurt or their property was damaged because of your business (bakery and café), or because of something you sold. That cover ended on Dec 1, 2026.',
    );
    expect(protectionSentence('Bakery and café', false, { endsOn: '2026-12-01', today: '2026-11-22' })).toBe(
      "You're covered if someone is hurt or their property is damaged because of your business (bakery and café). That cover ends on Dec 1, 2026.",
    );
  });

  it('gives each cover an end date once the policy is cancelled', () => {
    expect(coverDates('2026-09-01', '2026-12-01')).toBe('Sep 1 – Dec 1');
    expect(coverDates('2026-12-15', '2026-12-01')).toBe('Never started');
  });
});
