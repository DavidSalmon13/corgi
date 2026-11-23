import { describe, expect, it } from 'vitest';
import type { StatementCorrection, TimelineCover, TimelineTerm } from '../api/types';
import {
  chargedText,
  correctionReason,
  correctionSentence,
  coverName,
  coverWhat,
  endsText,
  ghostLabel,
  priceText,
  stripedNote,
  timelineAnswer,
  todayLabel,
} from './timelineCopy';

const TERM: TimelineTerm = { start: '2026-09-01', end: '2027-09-01', cancelledOn: null, today: '2026-11-22' };
const CANCELLED: TimelineTerm = { ...TERM, cancelledOn: '2026-12-01' };

function cover(over: Partial<TimelineCover>): TimelineCover {
  return {
    id: 1,
    kind: 'BASE',
    from: '2026-09-01',
    to: '2027-09-01',
    previousStart: null,
    annualCents: 120_000,
    perDayCents: null,
    daysKept: 365,
    chargedCents: 120_000,
    rows: [],
    correction: null,
    ...over,
  };
}

const BASE = cover({});
const EXTRA = cover({ id: 2, kind: 'PRODUCT_LIABILITY', from: '2026-10-15', annualCents: 73_000, perDayCents: 200, daysKept: 321, chargedCents: 64_200 });

const CORRECTION: StatementCorrection = {
  transactionDisplayId: 'PTX-0003',
  recordedOn: '2026-11-05',
  oldStart: '2026-10-15',
  newStart: '2026-10-08',
  dayDelta: 7,
  netCents: 1_400,
  perDayCents: 200,
  reason: "Customer's signed request was dated Oct 8; cover was entered a week late.",
  actorName: 'Sam Reyes',
  originalLineId: 'LE-0004',
  reversalLineId: 'LE-0006',
  rebillLineId: 'LE-0007',
};

// covers AC2, T4
describe('timelineAnswer', () => {
  it('says a base-only policy covers the full year', () => {
    expect(timelineAnswer(TERM, [BASE])).toBe('You’re covered for a full year, Sep 1, 2026 to Sep 1, 2027.');
  });

  it('adds the extra cover and when it starts', () => {
    expect(timelineAnswer(TERM, [BASE, EXTRA])).toBe(
      'You’re covered all year, plus extra cover for products you sell from Oct 15.',
    );
  });

  it('uses the corrected start after a correction', () => {
    expect(timelineAnswer(TERM, [BASE, { ...EXTRA, from: '2026-10-08', previousStart: '2026-10-15' }])).toBe(
      'You’re covered all year, plus extra cover for products you sell from Oct 8.',
    );
  });

  it('says when a cancelled policy ended', () => {
    expect(timelineAnswer(CANCELLED, [BASE, EXTRA])).toBe('Your cover ended on Dec 1, 2026.');
  });
});

// covers AC6, T4
describe('the detail card', () => {
  it('names each cover and says what it pays for', () => {
    expect(coverName('BASE')).toBe('Base cover');
    expect(coverName('PRODUCT_LIABILITY')).toBe('Extra cover: products you sell');
    expect(coverWhat('BASE')).toBe('Pays if someone is hurt or their property is damaged because of your business.');
    expect(coverWhat('PRODUCT_LIABILITY')).toBe('Pays if something you sell hurts someone or damages their property.');
  });

  it('shows the end, marked when the policy was cancelled', () => {
    expect(endsText(EXTRA, TERM)).toBe('Sep 1, 2027');
    expect(endsText(EXTRA, CANCELLED)).toBe('Dec 1, 2026 (cancelled)');
  });

  it('shows the yearly price, with the daily price when it is whole cents', () => {
    expect(priceText(EXTRA)).toBe('$730.00 a year ($2.00 a day)');
    expect(priceText(BASE)).toBe('$1,200.00 a year');
  });

  it('shows what was charged by the day, and the days kept after a cancellation', () => {
    expect(chargedText(EXTRA, TERM)).toBe('321 days · $642.00');
    expect(chargedText(cover({ daysKept: 1, chargedCents: 329 }), TERM)).toBe('1 day · $3.29');
    expect(chargedText(cover({ daysKept: 91, chargedCents: 29_918 }), CANCELLED)).toBe('91 days kept · $299.18');
  });
});

// covers AC3, AC4, AC5, T4
describe('the timeline drawing', () => {
  it('labels today and the old start', () => {
    expect(todayLabel('2026-11-22')).toBe('Today, Nov 22');
    expect(ghostLabel('2026-10-15')).toBe('Before the fix: from Oct 15');
  });

  it('explains the striped days after a cancellation', () => {
    expect(stripedNote('2026-12-01')).toBe('Striped: days after your cover ended on Dec 1. You got those back.');
  });
});

// covers AC4, T4
describe('correctionSentence', () => {
  it('says when and how far the start moved, in words', () => {
    expect(correctionSentence(CORRECTION, 'PRODUCT_LIABILITY')).toBe(
      'On Nov 5 we moved your extra cover’s start a week earlier, from Oct 15 to Oct 8.',
    );
  });

  it('counts days when it is not a week, and says later when cover starts later', () => {
    expect(correctionSentence({ ...CORRECTION, oldStart: '2026-10-08', newStart: '2026-10-22', dayDelta: -14 }, 'PRODUCT_LIABILITY')).toBe(
      'On Nov 5 we moved your extra cover’s start 14 days later, from Oct 8 to Oct 22.',
    );
    expect(correctionSentence({ ...CORRECTION, newStart: '2026-10-14', dayDelta: 1 }, 'BASE')).toBe(
      'On Nov 5 we moved your base cover’s start 1 day earlier, from Oct 15 to Oct 14.',
    );
  });

  it('gives the reason as written, or nothing when there is none', () => {
    expect(correctionReason(CORRECTION)).toBe(
      "The reason: Customer's signed request was dated Oct 8; cover was entered a week late.",
    );
    expect(correctionReason({ ...CORRECTION, reason: null })).toBeNull();
  });
});
