import { describe, expect, it } from 'vitest';
import type { LedgerLine } from '../api/types';
import { toLedgerRows } from './ledgerRows';

const line = (o: Partial<LedgerLine>): LedgerLine => ({
  id: 1,
  displayId: 'LE-0001',
  entryDate: '2026-09-01',
  type: 'CHARGE',
  description: 'Base cover, full year',
  amountCents: 120_000,
  calcAnnualCents: 120_000,
  calcDays: 365,
  calcTermDays: 365,
  coverFrom: '2026-09-01',
  coverTo: '2027-09-01',
  coverageId: 10,
  policyTransactionDisplayId: 'PTX-0001',
  reversesDisplayId: null,
  reversedByDisplayId: null,
  balanceAfterCents: 120_000,
  ...o,
});

// covers AC9, T13 — every amount shows how it was worked out and where it came from
describe('toLedgerRows', () => {
  it('shows a charge with its calculation, signed amount, balance and references', () => {
    const [row] = toLedgerRows([line({})]);
    expect(row).toMatchObject({
      key: 'LE-0001',
      date: 'Sep 1, 2026',
      title: 'Base cover, full year',
      calc: '$1,200.00 × 365 ÷ 365',
      amount: '+$1,200.00',
      balance: '$1,200.00',
      refs: ['LE-0001', 'PTX-0001'],
      struck: false,
      correction: false,
    });
  });

  it('strikes through a line that was undone and says what undid it', () => {
    const rows = toLedgerRows([
      line({ displayId: 'LE-0003', policyTransactionDisplayId: 'PTX-0002', reversedByDisplayId: 'LE-0006' }),
      line({
        id: 6, displayId: 'LE-0006', type: 'REVERSAL', amountCents: -64_200, balanceAfterCents: -64_200,
        calcAnnualCents: null, calcDays: null, calcTermDays: null,
        policyTransactionDisplayId: 'PTX-0003', reversesDisplayId: 'LE-0003',
      }),
      line({ id: 7, displayId: 'LE-0007', amountCents: 65_600, calcAnnualCents: 73_000, calcDays: 328, policyTransactionDisplayId: 'PTX-0003' }),
    ]);
    expect(rows[0]).toMatchObject({ struck: true, note: 'Undone by LE-0006', correction: false });
    expect(rows[1]).toMatchObject({ calc: 'Undo of LE-0003', amount: '−$642.00', balance: '−$642.00', correction: true });
    expect(rows[2]).toMatchObject({ calc: '$730.00 × 328 ÷ 365', correction: true });
  });

  it('marks payments as paid in the good tone', () => {
    const [row] = toLedgerRows([
      line({ type: 'PAYMENT', amountCents: -120_000, balanceAfterCents: 0, calcAnnualCents: null, policyTransactionDisplayId: null }),
    ]);
    expect(row).toMatchObject({ calc: 'Paid', calcTone: 'good', amount: '−$1,200.00', balance: '$0.00', refs: ['LE-0001'] });
  });

  // covers AC11, T4 — payments trace to Stripe, and a declined attempt stays visible at $0.00
  it('shows a payment with its Stripe PaymentIntent as a reference', () => {
    const [row] = toLedgerRows([
      line({
        type: 'PAYMENT', description: 'Payment, card ending 4242', amountCents: -1400, balanceAfterCents: 0,
        calcAnnualCents: null, calcDays: null, calcTermDays: null, coverageId: null, coverFrom: null, coverTo: null,
        policyTransactionDisplayId: null, stripePaymentIntentId: 'pi_3Qabc', stripeEventId: 'evt_1Qabc',
      }),
    ]);
    expect(row).toMatchObject({ calc: 'Paid', calcTone: 'good', amount: '−$14.00', note: '', refs: ['LE-0001', 'pi_3Qabc'] });
  });

  it("shows a declined attempt as didn't go through, with no money moved", () => {
    const [row] = toLedgerRows([
      line({
        displayId: 'LE-0004', type: 'PAYMENT', description: 'Payment attempt, card ending 0002', amountCents: 0,
        balanceAfterCents: 64_200, calcAnnualCents: null, calcDays: null, calcTermDays: null, coverageId: null,
        coverFrom: null, coverTo: null, policyTransactionDisplayId: null, stripePaymentIntentId: 'pi_3Qdecl',
      }),
    ]);
    expect(row).toMatchObject({
      title: 'Payment attempt, card ending 0002',
      note: 'No money moved',
      calc: "Didn't go through",
      calcTone: 'bad',
      amount: '$0.00',
      balance: '$642.00',
      refs: ['LE-0004', 'pi_3Qdecl'],
    });
  });

  // covers correct-effective-date AC12, T3 — a refund line names its refund and Stripe's id for it
  it('shows a refund with its refund id and Stripe refund as references', () => {
    const [row] = toLedgerRows([
      line({
        displayId: 'LE-0009', type: 'REFUND', description: 'Refund to card ending 4242', amountCents: 1_400,
        balanceAfterCents: 0, calcAnnualCents: null, calcDays: null, calcTermDays: null, coverageId: null,
        coverFrom: null, coverTo: null, policyTransactionDisplayId: null, stripeEventId: 'evt_1Qref',
        refundDisplayId: 'RF-0001', stripeRefundId: 're_3Qabc',
      }),
    ]);
    expect(row).toMatchObject({
      title: 'Refund to card ending 4242',
      calc: 'Refunded',
      calcTone: 'good',
      amount: '+$14.00',
      balance: '$0.00',
      refs: ['LE-0009', 'RF-0001', 're_3Qabc'],
      struck: false,
      correction: false,
    });
  });

  // covers cancel-refund AC9, T10 — a cancellation's credit line says what was given back and how
  it('shows a cancellation credit as unused days refunded, with its calculation', () => {
    const [row] = toLedgerRows([
      line({
        displayId: 'LE-0009', type: 'CREDIT', entryDate: '2026-11-22', description: 'Base cover, unused days',
        amountCents: -90_082, calcAnnualCents: 120_000, calcDays: 274, calcTermDays: 365,
        coverFrom: '2026-12-01', coverTo: '2027-09-01', policyTransactionDisplayId: 'PTX-0004', balanceAfterCents: -88_682,
      }),
    ]);
    expect(row).toMatchObject({
      title: 'Base cover, unused days refunded',
      calc: '$1,200.00 × 274 ÷ 365',
      calcTone: 'ink',
      amount: '−$900.82',
      balance: '−$886.82',
      refs: ['LE-0009', 'PTX-0004'],
      struck: false,
      correction: false,
    });
  });
});
