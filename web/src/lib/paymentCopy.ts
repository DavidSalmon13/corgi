import type { PaymentStatus, PaymentSummary } from '../api/types';
import { formatCents } from './money';

/** The words on P1 (Home) and P5 (Pay), from design/plainly/P1-Home.dc.html and P5-Pay.dc.html. */

export const STRIPE_NOTE = 'Card payments are handled by Stripe. We never see your full card number.';

/** "Pay $14.00", on the P1 button and as the P5 heading. */
export function payLabel(amountCents: number): string {
  return `Pay ${formatCents(amountCents)}`;
}

export function cardLabel(last4: string): string {
  return `Card ending ${last4}`;
}

export function savedCardLabel(last4: string): { card: string; note: string } {
  return { card: cardLabel(last4), note: 'Used last time' };
}

export function firstName(fullName: string): string {
  return fullName.trim().split(/\s+/)[0] ?? '';
}

/** Policy dates are New York calendar days (DECISIONS), so a payment's day is read there too. */
const DAY = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: 'numeric',
  year: 'numeric',
  timeZone: 'America/New_York',
});

/** "Nov 22, 2026" from an ISO instant. */
export function paymentDay(instant: string): string {
  return DAY.format(new Date(instant));
}

export type PayPhase = 'form' | 'waiting' | 'paid' | 'failed';

/**
 * Which P5 state to show. Once the card is submitted the page waits for the bank: the browser is never
 * trusted to say a payment worked, only the status Stripe's webhook set (DECISIONS, 2026-11-22).
 */
export function payPagePhase({ submitted, status }: { submitted: boolean; status: PaymentStatus }): PayPhase {
  if (status === 'SUCCEEDED') return 'paid';
  if (status === 'FAILED') return 'failed';
  if (status === 'PROCESSING') return 'waiting';
  return submitted ? 'waiting' : 'form';
}

export type StateCopy = { title: string; headline: string; body: string };

export function payWaitingCopy(
  amountCents: number,
  last4: string | null,
  submittedAt: string,
): StateCopy & { meta: string } {
  const card = last4 ? cardLabel(last4) : 'Your card';
  return {
    title: 'Waiting for your bank',
    headline: `Your ${formatCents(amountCents)} payment is on its way.`,
    body: "Your bank hasn't confirmed it yet. We'll show it as paid as soon as it does. You don't need to pay again.",
    meta: `${card} · ${paymentDay(submittedAt)}`,
  };
}

/** P5's line under the heading when the owner has several policies: which one this payment is for. */
export function payingFor(policyNumber: string): string {
  return `For policy ${policyNumber}`;
}

/**
 * After a payment settles. With several policies, pass the policy number: the payment only paid that one, so
 * "You owe nothing now" could be wrong, and the copy says what is left on that policy instead (more-policies AC9).
 */
export function payPaidCopy(ownerName: string, stillDueCents: number, policyNumber: string | null = null): StateCopy {
  const on = policyNumber ? ` on ${policyNumber}` : '';
  return {
    title: 'Paid',
    headline: `You're all set, ${firstName(ownerName)}.`,
    body:
      stillDueCents > 0
        ? `Your bank confirmed the payment. ${formatCents(stillDueCents)} is still due${on}.`
        : policyNumber
          ? `Your bank confirmed the payment. Nothing more is due${on}.`
          : 'Your bank confirmed the payment. You owe nothing now.',
  };
}

export function payFailedCopy(amountCents: number): StateCopy {
  return {
    title: "Didn't go through",
    headline: `Your ${formatCents(amountCents)} payment didn't go through.`,
    body: 'Your bank declined it, so no money moved. You can try the same card again or use a different one.',
  };
}

export type HomePaymentState = 'due' | 'waiting' | 'failed' | 'nothing' | 'cancelled';

/** Which P1 amount card to show, from what's due and the latest payment sent to the bank. */
export function homePaymentState({
  cancelled,
  amountDueCents,
  payment,
}: {
  cancelled: boolean;
  amountDueCents: number;
  payment: PaymentSummary | null;
}): HomePaymentState {
  if (payment?.status === 'PROCESSING') return 'waiting';
  if (cancelled) return 'cancelled';
  if (amountDueCents <= 0) return 'nothing';
  if (payment?.status === 'FAILED') return 'failed';
  return 'due';
}

export function homeWaitingCopy(amountCents: number): StateCopy {
  return {
    title: 'Waiting for your bank',
    headline: `Your ${formatCents(amountCents)} payment is on its way.`,
    body: "We'll show it as paid once your bank confirms. You don't need to do anything.",
  };
}

export function homeFailedCopy(amountCents: number): StateCopy {
  return {
    title: "Didn't go through",
    headline: `Your ${formatCents(amountCents)} payment didn't go through. No money moved.`,
    body: 'Your bank declined it. You can try again, or use a different card.',
  };
}

/** Shown instead of the card form when the demo has no Stripe keys. */
/**
 * When sending the card failed and we don't know why. The bank may already have the money, so this never says
 * nothing was charged.
 */
export const SEND_UNKNOWN = "We couldn't hear back from your bank. Check your cover in a minute before you try again.";

export const PAYMENTS_OFF = "Card payments aren't set up on this demo yet, so you can't pay here right now.";
