import { describe, expect, it } from 'vitest';
import type { CancelPlan, Cancellation, Cancelled } from '../api/types';
import {
  cancelConfirm,
  cancelPreviewHeading,
  cancelPreviewRows,
  cancelSaveLabel,
  cancelledBanner,
  cancelledCard,
  cancellationTitle,
  coverEndsSentence,
  customerWillSeeCancel,
  refundPieces,
  refundPill,
  unusedDaysCaption,
  workedOutRows,
} from './cancelCopy';

// The worked example: a Sep 1, 2026 → Sep 1, 2027 policy (365 days), base cover $1,200.00 a year and extra cover
// $730.00 a year from Oct 8. $14.00 from the correction is still owed. Cancelled from Dec 1: 91 days used, 274 unused.
const base = {
  coverageId: 10,
  kind: 'BASE' as const,
  label: 'Base cover, unused days',
  from: '2026-12-01',
  to: '2027-09-01',
  calc: { annualCents: 120_000, days: 274, termDays: 365, amountCents: 90_082 },
  amountCents: -90_082,
};
const extra = {
  coverageId: 21,
  kind: 'PRODUCT_LIABILITY' as const,
  label: 'Extra cover, unused days',
  from: '2026-12-01',
  to: '2027-09-01',
  calc: { annualCents: 73_000, days: 274, termDays: 365, amountCents: 54_800 },
  amountCents: -54_800,
};

const plan = (o: Partial<CancelPlan>): CancelPlan => ({
  policyId: 1,
  cancelDate: '2026-12-01',
  policyStart: '2026-09-01',
  policyEnd: '2027-09-01',
  usedDays: 91,
  unusedDays: 274,
  termDays: 365,
  credits: [base, extra],
  balanceBeforeCents: 1_400,
  keptBackCents: 1_400,
  keptBackFor: 'CORRECT',
  balanceAfterCents: -143_482,
  stillOwedCents: 0,
  refunds: [
    { paymentId: 9, paymentDisplayId: 'PAY-0002', cardLast4: '4242', amountCents: 64_200 },
    { paymentId: 7, paymentDisplayId: 'PAY-0001', cardLast4: '4242', amountCents: 79_282 },
  ],
  creditTotalCents: -144_882,
  refundTotalCents: 143_482,
  ...o,
});

const refunded = plan({});

// Never paid: $1,200.00 owed, so the $900.82 credit leaves $299.18 for the 91 days covered.
const stillOwes = plan({
  credits: [base],
  balanceBeforeCents: 120_000,
  keptBackCents: 90_082,
  keptBackFor: 'ISSUE',
  balanceAfterCents: 29_918,
  stillOwedCents: 29_918,
  refunds: [],
  creditTotalCents: -90_082,
  refundTotalCents: 0,
});

// The credit exactly pays off what was owed: nothing back, nothing left.
const evens = plan({
  credits: [base],
  balanceBeforeCents: 90_082,
  keptBackCents: 90_082,
  keptBackFor: 'ISSUE',
  balanceAfterCents: 0,
  refunds: [],
  creditTotalCents: -90_082,
  refundTotalCents: 0,
});

const TODAY = '2026-11-22';

// covers AC3, AC5, T8 — B4's preview: each credit with its arithmetic, what's kept back, then the refund or what's still owed
describe('cancel preview rows', () => {
  const lines = (p: CancelPlan) => cancelPreviewRows(p).map((r) => [r.label, r.detail, r.amount].join(' · '));

  it('shows each cover credit, the amount kept back and the refund', () => {
    expect(lines(refunded)).toEqual([
      'Base cover, unused days · $1,200.00 × 274 ÷ 365 · −$900.82',
      'Extra cover, unused days · $730.00 × 274 ÷ 365 · −$548.00',
      'Unpaid balance kept back · Correction still owed · +$14.00',
      'Refund to card ending 4242 · Sent through Stripe when you confirm · $1,434.82',
    ]);
  });

  it('marks the refund as the total line', () => {
    expect(cancelPreviewRows(refunded).map((r) => r.total)).toEqual([false, false, false, true]);
  });

  it('shows what is still owed for the days covered when the credit is smaller than the balance', () => {
    expect(lines(stillOwes)).toEqual([
      'Base cover, unused days · $1,200.00 × 274 ÷ 365 · −$900.82',
      'Still owed for the 91 days covered · What the credit doesn\'t cover · $299.18',
    ]);
  });

  it('says nothing is refunded or owed when the credit exactly pays off the balance', () => {
    expect(lines(evens)).toEqual([
      'Base cover, unused days · $1,200.00 × 274 ÷ 365 · −$900.82',
      'Unpaid balance kept back · First year still owed · +$900.82',
      'Nothing to refund or pay · The credit pays off what was owed · $0.00',
    ]);
  });

  it('names what left the balance owing', () => {
    const keptFor = (keptBackFor: CancelPlan['keptBackFor']) =>
      cancelPreviewRows(plan({ keptBackFor })).find((r) => r.label === 'Unpaid balance kept back')?.detail;
    expect(keptFor('ADD_COVERAGE')).toBe('Extra cover still owed');
    expect(keptFor('ISSUE')).toBe('First year still owed');
    expect(keptFor(null)).toBe('Still owed');
  });

  it('lists each piece of the refund, latest payment first', () => {
    expect(refundPieces(refunded)).toBe('$642.00 from the latest payment, then $792.82 from the first');
    expect(
      refundPieces(plan({ refunds: [refunded.refunds[0]], refundTotalCents: 64_200 })),
    ).toBe('$642.00 from the latest payment');
    expect(refundPieces(stillOwes)).toBeNull();
  });
});

// covers AC3, AC5, AC7, T8 — the sentence the customer gets, the save label and the confirm dialog
describe('what the cancellation tells the customer', () => {
  it('says when cover ends and what comes back', () => {
    expect(customerWillSeeCancel(refunded, TODAY)).toBe(
      "Your cover ends on Dec 1, 2026. We're sending $1,434.82 back to your card ending 4242.",
    );
  });

  it('says what is still owed for the days covered', () => {
    expect(customerWillSeeCancel(stillOwes, TODAY)).toBe(
      'Your cover ends on Dec 1, 2026. You owe $299.18 for the days you were covered.',
    );
    expect(customerWillSeeCancel(evens, TODAY)).toBe('Your cover ends on Dec 1, 2026. You have nothing to pay.');
  });

  it('uses the past tense once the cancel day has come', () => {
    expect(customerWillSeeCancel(refunded, '2026-12-01')).toBe(
      "Your cover ended on Dec 1, 2026. We're sending $1,434.82 back to your card ending 4242.",
    );
    expect(coverEndsSentence('2026-12-01', '2026-11-30')).toBe('Your cover ends on Dec 1, 2026.');
  });

  it('heads the preview with what the customer gets back, or still owes', () => {
    expect(cancelPreviewHeading(refunded, 'Maya Okafor')).toBe('What Maya gets back');
    expect(cancelPreviewHeading(stillOwes, 'Maya Okafor')).toBe('What Maya still owes');
    expect(cancelPreviewHeading(null, '')).toBe('What the customer gets back');
  });

  it('labels the save button with the refund when there is one', () => {
    expect(cancelSaveLabel(refunded)).toBe('Cancel policy and refund $1,434.82');
    expect(cancelSaveLabel(stillOwes)).toBe('Cancel policy');
  });

  it('asks once more before cancelling, in one line', () => {
    expect(cancelConfirm(refunded, 'Maya Okafor')).toEqual({
      title: 'Cancel this policy?',
      sentence: 'All cover ends on Dec 1, 2026. Maya gets $1,434.82 back and sees your reason.',
    });
    expect(cancelConfirm(stillOwes, 'Maya Okafor').sentence).toBe(
      'All cover ends on Dec 1, 2026. Maya still owes $299.18 for the days covered and sees your reason.',
    );
    expect(cancelConfirm(evens, ' ').sentence).toBe(
      'All cover ends on Dec 1, 2026. The customer owes nothing and sees your reason.',
    );
  });
});

// covers AC7, T8 — the once-only banner on B3 with the ids the cancellation created
describe('the saved banner', () => {
  const saved = (o: Partial<Cancelled>): Cancelled => ({
    policyId: 1,
    cancelDate: '2026-12-01',
    transactionDisplayId: 'PTX-0004',
    creditDisplayIds: ['LE-0009', 'LE-0010'],
    balanceCents: -143_482,
    refundTotalCents: 143_482,
    refunds: [
      { displayId: 'RF-0002', paymentId: 9, amountCents: 64_200 },
      { displayId: 'RF-0003', paymentId: 7, amountCents: 79_282 },
    ],
    ...o,
  });

  it('says cover ends and the refund is on its way', () => {
    expect(cancelledBanner('Maya Okafor', saved({}), TODAY)).toEqual({
      message: "Cancelled. Cover ends on Dec 1, 2026 and $1,434.82 is on its way to Maya's card.",
      refs: 'Cancellation PTX-0004 · LE-0009, LE-0010',
    });
  });

  it('says what is still owed, or that nothing is', () => {
    expect(
      cancelledBanner('Maya Okafor', saved({ balanceCents: 29_918, refundTotalCents: 0, refunds: [] }), TODAY).message,
    ).toBe('Cancelled. Cover ends on Dec 1, 2026. Maya still owes $299.18 for the days covered.');
    expect(cancelledBanner('Maya Okafor', saved({ balanceCents: 0, refundTotalCents: 0, refunds: [] }), TODAY).message).toBe(
      'Cancelled. Cover ends on Dec 1, 2026. Maya has nothing to pay.',
    );
    expect(cancelledBanner('Maya Okafor', saved({ cancelDate: '2026-09-01' }), TODAY).message).toBe(
      "Cancelled. Cover ended on Sep 1, 2026 and $1,434.82 is on its way to Maya's card.",
    );
  });
});

// covers AC10, AC11, T8 — P1's cancelled card and P6, read back from what the cancellation posted
describe('the customer side', () => {
  const cancellation = (o: Partial<Cancellation>): Cancellation => ({
    endsOn: '2026-12-01',
    reason: 'Closing the bakery at the end of November.',
    usedDays: 91,
    unusedDays: 274,
    termDays: 365,
    credits: [
      { kind: 'BASE', annualCents: 120_000, days: 274, amountCents: -90_082 },
      { kind: 'PRODUCT_LIABILITY', annualCents: 73_000, days: 274, amountCents: -54_800 },
    ],
    keptBackCents: 1_400,
    keptBackFor: 'CORRECT',
    refundCents: 143_482,
    refundStatus: 'PENDING',
    cardLast4: '4242',
    settledOn: null,
    ...o,
  });
  const pending = cancellation({});
  const settled = cancellation({ refundStatus: 'SUCCEEDED', settledOn: '2026-11-24' });
  const owing = cancellation({
    credits: [{ kind: 'BASE', annualCents: 120_000, days: 274, amountCents: -90_082 }],
    keptBackCents: 90_082,
    keptBackFor: 'ISSUE',
    refundCents: 0,
    refundStatus: null,
    cardLast4: null,
  });

  it('P1: says the refund is on its way, then that it was sent', () => {
    expect(cancelledCard(pending, 0, TODAY)).toEqual({
      sentence: "Your cover ends on Dec 1, 2026. We're sending $1,434.82 back to your card ending 4242.",
      due: null,
    });
    expect(cancelledCard(pending, 0, '2026-12-02').sentence).toBe(
      "Your cover ended on Dec 1, 2026. We're sending $1,434.82 back to your card ending 4242.",
    );
    expect(cancelledCard(settled, 0, TODAY).sentence).toBe('We sent $1,434.82 back to your card ending 4242.');
  });

  it('P1: shows what is still owed for the days covered', () => {
    expect(cancelledCard(owing, 29_918, TODAY)).toEqual({
      sentence: 'Your cover ends on Dec 1, 2026.',
      due: '$299.18 due · for the 91 days you were covered',
    });
  });

  it('P6: titles the page by whether cover has ended yet', () => {
    expect(cancellationTitle(pending, TODAY)).toBe('Your cover ends on Dec 1, 2026');
    expect(cancellationTitle(pending, '2026-12-01')).toBe('Your cover has ended');
  });

  it('P6: explains the striped days under the bar', () => {
    expect(unusedDaysCaption(pending)).toBe('91 days used. The striped part, 274 unused days, comes back to you.');
    expect(unusedDaysCaption(owing)).toBe(
      '91 days used. The striped part, 274 unused days, comes off what you owed.',
    );
    expect(unusedDaysCaption(cancellation({ usedDays: 1, unusedDays: 1 }))).toBe(
      '1 day used. The striped part, 1 unused day, comes back to you.',
    );
  });

  it('P6: shows each credit, what was kept back and the money back', () => {
    const lines = workedOutRows(pending).map((r) => [r.label, r.detail, r.amount].join(' · '));
    expect(lines).toEqual([
      'Base cover, unused days · $1,200.00 × 274 ÷ 365 · −$900.82',
      'Extra cover, unused days · $730.00 × 274 ÷ 365 · −$548.00',
      'Unpaid balance kept back · The $14.00 from the start-date fix, still owed · +$14.00',
      'Money back to card ending 4242 · No cancellation fee · $1,434.82',
    ]);
    expect(workedOutRows(owing, 29_918).map((r) => [r.label, r.detail, r.amount].join(' · '))).toEqual([
      'Base cover, unused days · $1,200.00 × 274 ÷ 365 · −$900.82',
      'Still owed for the 91 days you were covered · No cancellation fee · $299.18',
    ]);
  });

  it('P6: says where the refund is, with the Stripe and bank timing', () => {
    expect(refundPill(pending)).toEqual({
      label: 'Refund on its way',
      tone: 'wait',
      sentence: "We've asked Stripe to send it. Once it's sent, banks take 5–10 days to show it.",
    });
    expect(refundPill(settled)).toEqual({
      label: 'Refunded',
      tone: 'good',
      sentence: 'Stripe confirmed the refund on Nov 24. Your bank may take 5–10 days to show it.',
    });
    expect(refundPill(owing)).toBeNull();
  });
});
