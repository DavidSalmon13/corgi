import { describe, expect, it } from 'vitest';
import type { Coverage, CoverRemoved, PolicyDetail, RemoveCoverPlan } from '../api/types';
import {
  KEEP_LABEL,
  END_DATE_HINT,
  coverRemovedSentence,
  customerWillSeeRemoval,
  removeBlockedReason,
  removeConfirm,
  removePreviewHeading,
  removePreviewRows,
  removeRefundPieces,
  removeSaveLabel,
  removeSaveProblems,
  removedBanner,
  removedBarLabel,
  removedCoverReason,
  refundAfterRemoval,
  removedOn,
} from './removeCoverCopy';

// The worked example (spec AC3): a Sep 1, 2026 → Sep 1, 2027 policy (365 days). Product liability at $730.00 a year
// from Oct 8, paid ($656.00). Removed from Dec 1: 274 unused days, $730.00 × 274 ÷ 365 = $548.00 back to the card.

const credit = {
  coverageId: 21,
  kind: 'PRODUCT_LIABILITY' as const,
  label: 'Product liability return premium',
  from: '2026-12-01',
  to: '2027-09-01',
  calc: { annualCents: 73_000, days: 274, termDays: 365, amountCents: 54_800 },
  amountCents: -54_800,
};

const plan = (o: Partial<RemoveCoverPlan> = {}): RemoveCoverPlan => ({
  policyId: 1,
  coverageId: 21,
  endDate: '2026-12-01',
  coverFrom: '2026-10-08',
  policyEnd: '2027-09-01',
  credit,
  creditCents: -54_800,
  balanceBeforeCents: 0,
  keptBackCents: 0,
  keptBackFor: null,
  balanceAfterCents: -54_800,
  stillOwedCents: 0,
  refunds: [{ paymentId: 9, paymentDisplayId: 'PAY-0002', cardLast4: '4242', amountCents: 54_800 }],
  refundTotalCents: 54_800,
  ...o,
});

const refunded = plan();

// The endorsement was never paid: $656.00 owed, so the $548.00 credit pays most of it and $108.00 is still owed.
const stillOwes = plan({
  balanceBeforeCents: 65_600,
  keptBackCents: 54_800,
  keptBackFor: 'ADD_COVERAGE',
  balanceAfterCents: 10_800,
  stillOwedCents: 10_800,
  refunds: [],
  refundTotalCents: 0,
});

// $14.00 was owed from a correction: it comes off the credit and the rest goes back.
const keptBack = plan({
  balanceBeforeCents: 1_400,
  keptBackCents: 1_400,
  keptBackFor: 'CORRECT',
  balanceAfterCents: -53_400,
  refunds: [{ paymentId: 9, paymentDisplayId: 'PAY-0002', cardLast4: '4242', amountCents: 53_400 }],
  refundTotalCents: 53_400,
});

// The credit exactly pays off what was owed: nothing back, nothing left.
const evens = plan({
  balanceBeforeCents: 54_800,
  keptBackCents: 54_800,
  keptBackFor: 'ADD_COVERAGE',
  balanceAfterCents: 0,
  refunds: [],
  refundTotalCents: 0,
});

const TODAY = '2026-11-22';

describe('the Remove tab is blocked with a reason (AC1, AC5)', () => {
  // covers AC1, T11
  const base: Coverage = {
    id: 10,
    kind: 'BASE',
    label: 'General liability',
    annualPremiumCents: 120_000,
    from: '2026-09-01',
    to: '2027-09-01',
  };
  const extra: Coverage = {
    id: 21,
    kind: 'PRODUCT_LIABILITY',
    label: 'Product liability',
    annualPremiumCents: 73_000,
    from: '2026-10-08',
    to: '2027-09-01',
    endedOn: null,
  };
  const policy = (o: Partial<PolicyDetail> = {}): PolicyDetail =>
    ({ status: 'ACTIVE', paymentWaiting: false, coverages: [base, extra], ...o }) as PolicyDetail;

  it('is open on an active policy with live product liability', () => {
    expect(removeBlockedReason(policy())).toBeNull();
  });

  it('names a cancelled policy first', () => {
    expect(removeBlockedReason(policy({ status: 'CANCELLED' }))).toBe('This policy is cancelled.');
  });

  it('says there is nothing to remove without product liability', () => {
    expect(removeBlockedReason(policy({ coverages: [base] }))).toBe('This policy has no product liability to remove.');
  });

  it('says when product liability was already removed', () => {
    const removed = { ...extra, endedOn: '2026-12-01' };
    expect(removeBlockedReason(policy({ coverages: [base, removed] }))).toBe(
      'Product liability was removed on Dec 1, 2026.',
    );
  });

  it('waits for a payment with the bank', () => {
    expect(removeBlockedReason(policy({ paymentWaiting: true }))).toBe(
      'A payment is waiting for the bank. Remove this once it settles.',
    );
  });

  // covers AC5, T11 — the same words the API returns under `policy`
  it('blocks adding, correcting and removing again after a removal', () => {
    expect(removedCoverReason('add', '2026-12-01')).toBe(
      "Product liability was removed from this policy on Dec 1, 2026, so it can't be added again.",
    );
    expect(removedCoverReason('correct', '2026-12-01')).toBe(
      "Product liability was removed on Dec 1, 2026, so its effective date can't be corrected.",
    );
    expect(removedCoverReason('remove', '2026-12-01')).toBe('Product liability was removed on Dec 1, 2026.');
  });
});

describe('the Remove form (AC2)', () => {
  // covers AC2, T11
  it('explains the date field: from the start of the day, no fee', () => {
    expect(END_DATE_HINT).toBe('Product liability stops at the start of this day. No short-rate penalty.');
  });

  it('needs a day and a reason, with the server’s own words', () => {
    expect(removeSaveProblems('', '')).toEqual({
      message: '2 things to fix before you can save.',
      fields: {
        endDate: 'Pick the day product liability ends.',
        reason: 'Say why product liability is being removed. The customer sees this.',
      },
    });
  });

  it('measures the reason without the spaces around it, up to 500 characters', () => {
    expect(removeSaveProblems('2026-12-01', '   ')?.fields.reason).toBe(
      'Say why product liability is being removed. The customer sees this.',
    );
    expect(removeSaveProblems('2026-12-01', ` ${'a'.repeat(500)} `)).toBeNull();
    expect(removeSaveProblems('2026-12-01', 'a'.repeat(501))).toEqual({
      message: '1 thing to fix before you can save.',
      fields: { reason: 'Keep the reason under 500 characters.' },
    });
  });
});

describe('the preview (AC3)', () => {
  // covers AC3, T11
  it('shows the return premium and the refund to the card for the worked example', () => {
    expect(removePreviewRows(refunded)).toEqual([
      { label: 'Product liability return premium', detail: '$730.00 × 274 ÷ 365', amount: '−$548.00', total: false },
      {
        label: 'Return premium to card ending 4242',
        detail: 'Sent through Stripe when you confirm',
        amount: '$548.00',
        total: true,
      },
    ]);
  });

  it('keeps back what was still owed, naming the change that left it owing', () => {
    expect(removePreviewRows(keptBack)).toEqual([
      { label: 'Product liability return premium', detail: '$730.00 × 274 ÷ 365', amount: '−$548.00', total: false },
      { label: 'Unpaid balance kept back', detail: 'Correction still owed', amount: '+$14.00', total: false },
      {
        label: 'Return premium to card ending 4242',
        detail: 'Sent through Stripe when you confirm',
        amount: '$534.00',
        total: true,
      },
    ]);
  });

  it('shows the balance after when nothing was paid', () => {
    expect(removePreviewRows(stillOwes)).toEqual([
      { label: 'Product liability return premium', detail: '$730.00 × 274 ÷ 365', amount: '−$548.00', total: false },
      { label: 'Unpaid balance kept back', detail: 'Endorsement still owed', amount: '+$548.00', total: false },
      { label: 'Balance after', detail: 'Still owed for the days it covered', amount: '$108.00', total: true },
    ]);
  });

  it('says nothing changes hands when the credit pays off the balance exactly', () => {
    expect(removePreviewRows(evens).at(-1)).toEqual({
      label: 'Nothing to refund or pay',
      detail: 'The return premium pays off what was owed',
      amount: '$0.00',
      total: true,
    });
  });

  it('labels the refund line "card" when the card is unknown', () => {
    const noCard = plan({ refunds: [{ ...refunded.refunds[0], cardLast4: null }] });
    expect(removePreviewRows(noCard).at(-1)?.label).toBe('Return premium to card');
  });

  it('lists each payment the refund goes back to, latest first', () => {
    expect(removeRefundPieces(refunded)).toBe('$548.00 from the latest payment');
    const split = plan({
      refunds: [
        { paymentId: 9, paymentDisplayId: 'PAY-0002', cardLast4: '4242', amountCents: 40_000 },
        { paymentId: 7, paymentDisplayId: 'PAY-0001', cardLast4: '4242', amountCents: 14_800 },
      ],
    });
    expect(removeRefundPieces(split)).toBe('$400.00 from the latest payment, then $148.00 from the first');
    expect(removeRefundPieces(stillOwes)).toBeNull();
  });

  it('heads the preview with what the customer gets back, or still owes', () => {
    expect(removePreviewHeading(refunded, 'Maya Okafor')).toBe('What Maya gets back');
    expect(removePreviewHeading(stillOwes, 'Maya Okafor')).toBe('What Maya still owes');
    expect(removePreviewHeading(null, '')).toBe('What the customer gets back');
  });

  it('gives the customer one sentence about the removal', () => {
    expect(customerWillSeeRemoval(refunded, TODAY)).toBe(
      "Your product liability ends on Dec 1, 2026. We're sending $548.00 back to your card ending 4242.",
    );
    expect(customerWillSeeRemoval(stillOwes, TODAY)).toBe(
      'Your product liability ends on Dec 1, 2026. You now owe $108.00 for the days it covered.',
    );
    expect(customerWillSeeRemoval(evens, TODAY)).toBe(
      'Your product liability ends on Dec 1, 2026. You have nothing to pay.',
    );
  });

  it('labels the save with the refund, and offers to keep the cover', () => {
    expect(removeSaveLabel(refunded)).toBe('Remove product liability and refund $548.00');
    expect(removeSaveLabel(stillOwes)).toBe('Remove product liability');
    expect(KEEP_LABEL).toBe('Keep product liability');
  });
});

describe('confirm and saved (AC3, AC4)', () => {
  // covers AC3, AC4, T11
  it('asks before saving, with the money and the reason', () => {
    expect(removeConfirm(refunded, 'Maya Okafor')).toEqual({
      title: 'Remove product liability?',
      sentence: 'Product liability ends on Dec 1, 2026. Maya gets $548.00 back and sees your reason.',
    });
    expect(removeConfirm(stillOwes, 'Maya Okafor').sentence).toBe(
      'Product liability ends on Dec 1, 2026. Maya still owes $108.00 for the days it covered and sees your reason.',
    );
    expect(removeConfirm(evens, '').sentence).toBe(
      'Product liability ends on Dec 1, 2026. The customer owes nothing and sees your reason.',
    );
  });

  const saved: CoverRemoved = {
    policyId: 1,
    endDate: '2026-12-01',
    transactionDisplayId: 'PTX-0005',
    creditDisplayId: 'LE-0012',
    balanceCents: -54_800,
    refundTotalCents: 54_800,
    refunds: [{ displayId: 'RF-0003', paymentId: 9, amountCents: 54_800 }],
  };

  it('shows a banner with the refund on its way and the ids it posted', () => {
    expect(removedBanner('Maya Okafor', saved, TODAY)).toEqual({
      message: "Product liability removed. It ends on Dec 1, 2026 and $548.00 is on its way to Maya's card.",
      refs: 'Endorsement PTX-0005 · LE-0012',
    });
  });

  it('says what is still owed, or that nothing is', () => {
    const owes = { ...saved, balanceCents: 10_800, refundTotalCents: 0, refunds: [] };
    expect(removedBanner('Maya Okafor', owes, TODAY).message).toBe(
      'Product liability removed. It ends on Dec 1, 2026. Maya still owes $108.00 for the days it covered.',
    );
    const even = { ...owes, balanceCents: 0 };
    expect(removedBanner('', even, '2026-12-02').message).toBe(
      'Product liability removed. It ended on Dec 1, 2026. The customer has nothing to pay.',
    );
  });
});

describe('what the policyholder reads on P1 (AC7)', () => {
  // covers AC7, T11
  it('says when product liability ends, in the past once the day has come', () => {
    expect(coverRemovedSentence('2026-12-01', TODAY)).toBe('Your product liability ends on Dec 1, 2026.');
    expect(coverRemovedSentence('2026-12-01', '2026-12-01')).toBe('Your product liability ended on Dec 1, 2026.');
  });
});

describe('the removed cover on B3 and P2 (AC1, AC7)', () => {
  // covers AC7, T13
  it('labels the bar with where it stopped, keeping an earlier correction', () => {
    expect(removedBarLabel('2026-10-08', null, '2026-12-01')).toBe('from Oct 8 · removed Dec 1');
    expect(removedBarLabel('2026-10-08', '2026-10-15', '2026-12-01')).toBe('from Oct 8 · was Oct 15 · removed Dec 1');
  });
});

describe('P1 after a removal (AC7)', () => {
  const base: Coverage = { id: 1, kind: 'BASE', label: 'General liability', annualPremiumCents: 120_000, from: '2026-09-01', to: '2027-09-01' };
  const pl: Coverage = { ...base, id: 2, kind: 'PRODUCT_LIABILITY', label: 'Product liability', annualPremiumCents: 73_000, from: '2026-10-08' };

  // covers AC7, T13.1
  it('finds the day product liability was removed, or null while it runs', () => {
    expect(removedOn([base, pl])).toBeNull();
    expect(removedOn([base, { ...pl, endedOn: null }])).toBeNull();
    expect(removedOn([base, { ...pl, endedOn: '2026-12-01' }])).toBe('2026-12-01');
  });

  // covers AC7, T13.1
  it('gives the removal as why money is coming back, not a corrected date', () => {
    expect(refundAfterRemoval('2026-12-01', TODAY)).toBe(
      "Your product liability ends on Dec 1, 2026, so you'd paid for days it won't cover. That return premium is on its way. Banks take 5–10 days to show a refund.",
    );
    expect(refundAfterRemoval('2026-12-01', '2026-12-03')).toBe(
      "Your product liability ended on Dec 1, 2026, so you'd paid for days it didn't cover. That return premium is on its way. Banks take 5–10 days to show a refund.",
    );
  });
});
