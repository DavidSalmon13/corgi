import { describe, expect, it } from 'vitest';
import type { PaymentSummary } from '../api/types';
import {
  SEND_UNKNOWN,
  STRIPE_NOTE,
  firstName,
  homePaymentState,
  homeFailedCopy,
  homeWaitingCopy,
  payFailedCopy,
  payLabel,
  payPagePhase,
  payPaidCopy,
  payWaitingCopy,
  savedCardLabel,
} from './paymentCopy';

const payment = (o: Partial<PaymentSummary>): PaymentSummary => ({
  status: 'PROCESSING',
  amountCents: 1400,
  cardLast4: '4242',
  createdAt: '2026-11-22T10:00:00Z',
  ...o,
});

// covers AC1, AC2, AC3 — the pay button and the card form (P1, P5)
describe('pay page basics', () => {
  it('names the amount on the button and the heading', () => {
    expect(payLabel(1400)).toBe('Pay $14.00');
    expect(payLabel(120_000)).toBe('Pay $1,200.00');
  });

  it('offers the card used last time by its last four digits', () => {
    expect(savedCardLabel('4242')).toEqual({ card: 'Card ending 4242', note: 'Used last time' });
  });

  it('says who handles the card', () => {
    expect(STRIPE_NOTE).toBe('Card payments are handled by Stripe. We never see your full card number.');
  });

  it('greets the owner by first name', () => {
    expect(firstName('Maya Okafor')).toBe('Maya');
    expect(firstName('  Cher ')).toBe('Cher');
  });
});

// covers AC4, AC5, AC6 — the P5 states after the card is submitted
describe('payPagePhase', () => {
  it('shows the card form until the owner submits', () => {
    expect(payPagePhase({ submitted: false, status: 'OPEN' })).toBe('form');
  });

  it('waits for the bank once submitted, whatever the browser was told', () => {
    expect(payPagePhase({ submitted: true, status: 'OPEN' })).toBe('waiting');
    expect(payPagePhase({ submitted: true, status: 'PROCESSING' })).toBe('waiting');
  });

  it('only shows paid or declined once Stripe says so', () => {
    expect(payPagePhase({ submitted: true, status: 'SUCCEEDED' })).toBe('paid');
    expect(payPagePhase({ submitted: true, status: 'FAILED' })).toBe('failed');
  });
});

describe('P5 state copy', () => {
  it('waiting for your bank', () => {
    expect(payWaitingCopy(1400, '4242', '2026-11-22T10:00:00Z')).toEqual({
      title: 'Waiting for your bank',
      headline: 'Your $14.00 payment is on its way.',
      body: "Your bank hasn't confirmed it yet. We'll show it as paid as soon as it does. You don't need to pay again.",
      meta: 'Card ending 4242 · Nov 22, 2026',
    });
  });

  it('paid, with nothing left to pay', () => {
    expect(payPaidCopy('Maya Okafor', 0)).toEqual({
      title: 'Paid',
      headline: "You're all set, Maya.",
      body: 'Your bank confirmed the payment. You owe nothing now.',
    });
  });

  it('paid, when something new came due meanwhile', () => {
    expect(payPaidCopy('Maya Okafor', 1400).body).toBe('Your bank confirmed the payment. $14.00 is still due.');
  });

  it("didn't go through", () => {
    expect(payFailedCopy(1400)).toEqual({
      title: "Didn't go through",
      headline: "Your $14.00 payment didn't go through.",
      body: 'Your bank declined it, so no money moved. You can try the same card again or use a different one.',
    });
  });
});

// covers AC1, AC5, AC6, AC7 — which P1 card the owner sees
describe('homePaymentState', () => {
  const base = { cancelled: false, amountDueCents: 1400, payment: null };

  it('asks for payment when money is due and nothing is in flight', () => {
    expect(homePaymentState(base)).toBe('due');
  });

  it('is all paid up when nothing is due', () => {
    expect(homePaymentState({ ...base, amountDueCents: 0, payment: payment({ status: 'SUCCEEDED' }) })).toBe('nothing');
  });

  it('waits for the bank while a payment is processing, even though money still shows as due', () => {
    expect(homePaymentState({ ...base, payment: payment({ status: 'PROCESSING' }) })).toBe('waiting');
  });

  it('says the last payment failed while the amount is still due', () => {
    expect(homePaymentState({ ...base, payment: payment({ status: 'FAILED' }) })).toBe('failed');
  });

  it('goes back to plain "due" when a new amount comes due after a successful payment', () => {
    expect(homePaymentState({ ...base, payment: payment({ status: 'SUCCEEDED' }) })).toBe('due');
  });

  it('shows the cancellation instead of any payment state', () => {
    expect(homePaymentState({ ...base, cancelled: true, payment: payment({ status: 'FAILED' }) })).toBe('cancelled');
  });
});

describe('P1 payment copy', () => {
  it('waiting', () => {
    expect(homeWaitingCopy(1400)).toEqual({
      title: 'Waiting for your bank',
      headline: 'Your $14.00 payment is on its way.',
      body: "We'll show it as paid once your bank confirms. You don't need to do anything.",
    });
  });

  it('failed', () => {
    expect(homeFailedCopy(1400)).toEqual({
      title: "Didn't go through",
      headline: "Your $14.00 payment didn't go through. No money moved.",
      body: 'Your bank declined it. You can try again, or use a different card.',
    });
  });
});

// hotfix payment-review-fixes — after the card was sent, an unknown failure may still have moved money
describe('when sending the card fails for an unknown reason', () => {
  it("doesn't claim nothing was charged, and says to check before paying again", () => {
    expect(SEND_UNKNOWN).not.toMatch(/nothing was charged/i);
    expect(SEND_UNKNOWN).toMatch(/check your cover/i);
  });
});
