import { describe, expect, it } from 'vitest';
import type { Corrected, CorrectionPlan, RefundView } from '../api/types';
import {
  confirmSummary,
  correctSaveLabel,
  correctedBanner,
  correctedLine,
  correctingWhich,
  coverWasLabel,
  customerWillSeeCorrection,
  failedRefundsBanner,
  netChange,
  netWhy,
  settleLine,
  undoLine,
} from './correctCopy';

// The worked example: extra cover at $730.00 a year ($2.00 a day) from Oct 15 on a Sep 1, 2026 → Sep 1, 2027 policy,
// $642.00, paid by card ending 4242.
const plan = (o: Partial<CorrectionPlan>): CorrectionPlan => ({
  policyId: 1,
  coverageId: 20,
  oldStart: '2026-10-15',
  newStart: '2026-10-08',
  undo: {
    entryId: 3,
    entryDisplayId: 'LE-0003',
    label: 'Extra cover from Oct 15',
    calc: { annualCents: 73_000, days: 321, termDays: 365, amountCents: 64_200 },
    amountCents: -64_200,
  },
  rebill: {
    label: 'Extra cover from Oct 8',
    from: '2026-10-08',
    to: '2027-09-01',
    calc: { annualCents: 73_000, days: 328, termDays: 365, amountCents: 65_600 },
  },
  netCents: 1_400,
  balanceAfterCents: 1_400,
  refunds: [],
  refundTotalCents: 0,
  ...o,
});

const owesMore = plan({});

const refundDue = plan({
  newStart: '2026-10-22',
  rebill: {
    label: 'Extra cover from Oct 22',
    from: '2026-10-22',
    to: '2027-09-01',
    calc: { annualCents: 73_000, days: 314, termDays: 365, amountCents: 62_800 },
  },
  netCents: -1_400,
  balanceAfterCents: -1_400,
  refunds: [{ paymentId: 7, paymentDisplayId: 'PAY-0007', cardLast4: '4242', amountCents: 1_400 }],
  refundTotalCents: 1_400,
});

// $5.00 was still owed, so $5.00 of the $14.00 credit pays that and $9.00 goes back to the card.
const partlyRefunded = plan({
  ...refundDue,
  balanceAfterCents: -900,
  refunds: [{ paymentId: 7, paymentDisplayId: 'PAY-0007', cardLast4: '4242', amountCents: 900 }],
  refundTotalCents: 900,
});

// $642.00 was never paid, so the $14.00 credit only lowers what's owed.
const creditKept = plan({ ...refundDue, balanceAfterCents: 62_800, refunds: [], refundTotalCents: 0 });

const saved = (o: Partial<Corrected>): Corrected => ({
  policyId: 1,
  transactionDisplayId: 'PTX-0003',
  undoDisplayId: 'LE-0006',
  rebillDisplayId: 'LE-0007',
  netCents: 1_400,
  balanceCents: 1_400,
  refunds: [],
  ...o,
});

// covers AC2, AC3, T3 — the broker sees the undo, the corrected charge and the net change before saving
describe('correction preview lines', () => {
  it('names the endorsement being corrected with its annual premium', () => {
    expect(correctingWhich(73_000)).toBe('Product liability endorsement ($730.00 annual premium)');
  });

  it('shows the reversal and the rebill with their arithmetic', () => {
    expect(undoLine(owesMore)).toBe('Reversal: product liability from Oct 15 · $730.00 × 321 ÷ 365 · −$642.00');
    expect(correctedLine(owesMore)).toBe('Rebill: product liability from Oct 8 · $730.00 × 328 ÷ 365 · +$656.00');
    expect(correctedLine(refundDue)).toBe('Rebill: product liability from Oct 22 · $730.00 × 314 ÷ 365 · +$628.00');
  });

  it('gives the net change as days times the daily price', () => {
    expect(netChange(owesMore)).toBe('+$14.00 (7 more days × $2.00)');
    expect(netChange(refundDue)).toBe('−$14.00 (7 fewer days × $2.00)');
  });

  it('shows how the net change adds up', () => {
    expect(netWhy(owesMore)).toBe('7 more days × $2.00 · −$642.00 + $656.00');
    expect(netWhy(refundDue)).toBe('7 fewer days × $2.00 · −$642.00 + $628.00');
  });

  it('says "1 more day" and leaves out a daily price that is not exact', () => {
    const leap = plan({
      undo: { ...owesMore.undo, calc: { annualCents: 73_000, days: 321, termDays: 366, amountCents: 64_022 }, amountCents: -64_022 },
      rebill: { ...owesMore.rebill, calc: { annualCents: 73_000, days: 322, termDays: 366, amountCents: 64_222 } },
      netCents: 200,
    });
    expect(netChange(leap)).toBe('+$2.00 (1 more day)');
    expect(netWhy(leap)).toBe('1 more day · −$640.22 + $642.22');
  });
});

// covers AC3, AC4, AC5, T3 — what the customer reads, and where the money goes
describe('what the correction does to the bill', () => {
  it('tells the customer they owe more, and why', () => {
    expect(customerWillSeeCorrection(owesMore)).toBe('You owe $14.00 because we corrected the effective date of your product liability endorsement.');
  });

  it('tells the customer a refund is on its way, and to which card', () => {
    expect(customerWillSeeCorrection(refundDue)).toBe("We owe you $14.00. It's on its way to your card ending 4242.");
    expect(customerWillSeeCorrection(partlyRefunded)).toBe("We owe you $9.00. It's on its way to your card ending 4242.");
  });

  it('names every card a split refund goes back to', () => {
    const split = plan({
      ...refundDue,
      refunds: [
        { paymentId: 9, paymentDisplayId: 'PAY-0009', cardLast4: '0005', amountCents: 1_000 },
        { paymentId: 7, paymentDisplayId: 'PAY-0007', cardLast4: '4242', amountCents: 400 },
      ],
    });
    expect(customerWillSeeCorrection(split)).toBe("We owe you $14.00. It's on its way to your cards ending 0005 and 4242.");
  });

  it('says the balance went down when the credit only lowers what is owed', () => {
    expect(customerWillSeeCorrection(creditKept)).toBe(
      'You owe $14.00 less because we corrected the effective date of your product liability endorsement.',
    );
  });

  it('says what happens to the money when the broker saves', () => {
    expect(settleLine(owesMore, 'Maya Okafor')).toBe(
      "Added to Maya's balance. Maya is asked to pay it; no card is charged automatically.",
    );
    expect(settleLine(refundDue, 'Maya Okafor')).toBe(
      'Nothing is owed, so the $14.00 is refunded to card ending 4242 through Stripe as soon as you save.',
    );
    expect(settleLine(partlyRefunded, 'Maya Okafor')).toBe(
      '$5.00 pays off what Maya still owes, and $9.00 is refunded to card ending 4242 through Stripe as soon as you save.',
    );
    expect(settleLine(creditKept, 'Maya Okafor')).toBe("Taken off what Maya still owes. Nothing is refunded.");
    expect(settleLine(owesMore, '  ')).toBe(
      "Added to the customer's balance. The customer is asked to pay it; no card is charged automatically.",
    );
  });

  it('labels the save button with the refund when there is one', () => {
    expect(correctSaveLabel(owesMore)).toBe('Save correction');
    expect(correctSaveLabel(creditKept)).toBe('Save correction');
    expect(correctSaveLabel(refundDue)).toBe('Save and refund $14.00');
  });
});

// covers AC7, AC11, AC12, T3 — confirming, the saved banner, a failed refund and the timeline
describe('saving a correction', () => {
  it('sums the change up in one line before saving', () => {
    expect(confirmSummary(owesMore, 'Maya Okafor')).toEqual({
      sentence: "The effective date of the product liability endorsement becomes Oct 8 instead of Oct 15. Maya will owe $14.00 more and see your reason word for word.",
      amounts: 'Reversal −$642.00 · Rebill +$656.00 · Net +$14.00',
    });
    expect(confirmSummary(refundDue, 'Maya Okafor')).toEqual({
      sentence: "The effective date of the product liability endorsement becomes Oct 22 instead of Oct 15. $14.00 goes back to card ending 4242 and Maya sees your reason word for word.",
      amounts: 'Reversal −$642.00 · Rebill +$628.00 · Net −$14.00',
    });
  });

  it('confirms the save once, with the ids it created', () => {
    expect(correctedBanner('Maya Okafor', saved({}), null)).toEqual({
      message: 'Saved. Maya now owes $14.00 and can see why.',
      refs: 'Correction PTX-0003 · reversal LE-0006 · rebill LE-0007',
    });
    expect(
      correctedBanner('Maya Okafor', saved({
        netCents: -1_400,
        balanceCents: -1_400,
        refunds: [{ displayId: 'RF-0001', paymentId: 7, amountCents: 1_400 }],
      }), '4242'),
    ).toEqual({
      message: "Saved. $14.00 is on its way back to Maya's card ending 4242.",
      refs: 'Correction PTX-0003 · reversal LE-0006 · rebill LE-0007 · refund RF-0001',
    });
    expect(correctedBanner('Maya Okafor', saved({ netCents: -1_400, balanceCents: 62_800 }), null).message).toBe(
      'Saved. Maya now owes $628.00 and can see why.',
    );
  });

  const failed: RefundView = {
    id: 1, displayId: 'RF-0001', status: 'FAILED', amountCents: 1_400, cardLast4: '4242', attempt: 1,
    policyTransactionDisplayId: 'PTX-0003', stripeRefundId: null, failureReason: 'expired_or_canceled_card',
  };
  const txs = [
    { displayId: 'PTX-0003', type: 'CORRECT' },
    { displayId: 'PTX-0004', type: 'CANCEL' },
  ];

  it('says a refund failed, where it was going, and that the correction stands', () => {
    expect(failedRefundsBanner([failed], txs, 'Maya Okafor')).toEqual({
      heading: "The $14.00 refund didn't go through. Stripe couldn't send the money to card ending 4242.",
      detail: 'The correction is saved (PTX-0003). Maya still sees the refund as on its way until it goes through.',
      retryLabel: 'Retry refund',
    });
    expect(failedRefundsBanner([{ ...failed, cardLast4: null, policyTransactionDisplayId: null }], txs, ' ')).toEqual({
      heading: "The $14.00 refund didn't go through. Stripe couldn't send the money to the card.",
      detail: 'The change is saved. The customer still sees the refund as on its way until it goes through.',
      retryLabel: 'Retry refund',
    });
  });

  it('groups a cancellation refund split over several payments, and never calls it a correction', () => {
    const split = [
      { ...failed, id: 1, displayId: 'RF-0001', amountCents: 1_400, policyTransactionDisplayId: 'PTX-0004' },
      { ...failed, id: 2, displayId: 'RF-0002', amountCents: 70_200, policyTransactionDisplayId: 'PTX-0004' },
      { ...failed, id: 3, displayId: 'RF-0003', amountCents: 106_066, policyTransactionDisplayId: 'PTX-0004' },
    ];
    expect(failedRefundsBanner(split, txs, 'Maya Okafor')).toEqual({
      heading: "$1,776.66 in refunds didn't go through. Stripe couldn't send the money to card ending 4242.",
      detail: 'The cancellation is saved (PTX-0004). Maya still sees the money as on its way until it goes through.',
      retryLabel: 'Retry all 3 refunds',
    });
  });

  it('names both changes when failed refunds come from more than one', () => {
    const mixed = [failed, { ...failed, id: 2, displayId: 'RF-0002', cardLast4: '1881', policyTransactionDisplayId: 'PTX-0004' }];
    expect(failedRefundsBanner(mixed, txs, 'Maya Okafor')).toMatchObject({
      heading: "$28.00 in refunds didn't go through. Stripe couldn't send the money to the cards.",
      detail: 'The changes are saved (PTX-0003, PTX-0004). Maya still sees the money as on its way until it goes through.',
    });
  });

  it('shows the corrected start on the timeline with what it was', () => {
    expect(coverWasLabel('2026-10-08', '2026-10-15')).toBe('from Oct 8 · was Oct 15');
    expect(coverWasLabel('2026-10-08', null)).toBe('from Oct 8');
  });
});
