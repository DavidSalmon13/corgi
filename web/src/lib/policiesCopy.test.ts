import { describe, expect, it } from 'vitest';
import type { MyPolicyRow } from '../api/types';
import { glossParts } from '../components/Glossed';
import { GLOSSARY, TERM_PATTERNS } from './glossary';
import {
  ALL_POLICIES,
  NEW_POLICY_FOR_CUSTOMER,
  REWRITE_POLICY,
  YOUR_POLICIES,
  canRewrite,
  policyCardAmount,
  policyCardStatus,
  NO_NETTING,
  replacedNote,
  rewriteNote,
  showAllPolicies,
  totalDueText,
} from './policiesCopy';

// The worked example: GL-2026-0013 was issued from Sep 1 instead of Oct 1 and paid, flat-cancelled with $1,200.00
// on its way back to card 4242, then rewritten as GL-2026-0014 from Oct 1, which is due.
const row = (o: Partial<MyPolicyRow>): MyPolicyRow => ({
  policyId: 14,
  policyNumber: 'GL-2026-0014',
  start: '2026-10-01',
  end: '2027-10-01',
  status: 'ACTIVE',
  balanceCents: 120_000,
  paymentWaiting: false,
  refundWaiting: null,
  replaces: null,
  replacedBy: null,
  ...o,
});
const rewrite = row({ replaces: { policyId: 13, policyNumber: 'GL-2026-0013' } });
const old = row({
  policyId: 13,
  policyNumber: 'GL-2026-0013',
  start: '2026-09-01',
  end: '2027-09-01',
  status: 'CANCELLED',
  balanceCents: -120_000,
  refundWaiting: { amountCents: 120_000, cardLast4: '4242' },
  replacedBy: { policyId: 14, policyNumber: 'GL-2026-0014' },
});

// covers AC7, T2 — "Your policies": the total due, then one card per policy
describe('the policies overview', () => {
  it('names the page and the way back to it', () => {
    expect(YOUR_POLICIES).toBe('Your policies');
    expect(ALL_POLICIES).toBe('All policies');
  });

  it('leads with what is due across every policy', () => {
    expect(totalDueText(120_000)).toEqual({ amount: '$1,200.00', note: 'Due across your policies' });
    expect(totalDueText(0)).toEqual({ amount: '$0.00', note: 'Nothing due on any policy' });
  });

  // covers AC7, T15 — money doesn't move between policies, and the overview says so
  it('says a refund on one policy is not taken off another', () => {
    expect(NO_NETTING).toBe("Each policy is paid on its own: a refund on one isn't taken off what's due on another.");
  });

  it("shows each policy's own state on its card", () => {
    expect(policyCardStatus(rewrite)).toEqual({ label: 'Active · payment due', tone: 'wait' });
    expect(policyCardStatus(row({ balanceCents: 0 }))).toEqual({ label: 'Active · paid up', tone: 'good' });
    expect(policyCardStatus(row({ paymentWaiting: true }))).toEqual({ label: 'Active · waiting for your bank', tone: 'wait' });
    expect(policyCardStatus(row({ status: 'CANCELLED', balanceCents: 0 }))).toEqual({ label: 'Cancelled', tone: 'neutral' });
  });

  // covers AC4, AC7 — a replaced policy says by which
  it('says which policy replaced a cancelled one', () => {
    expect(policyCardStatus(old)).toEqual({ label: 'Cancelled · replaced by GL-2026-0014', tone: 'neutral' });
  });

  it('shows the amount due, or the refund on its way, never one netted against the other', () => {
    expect(policyCardAmount(rewrite)).toEqual({ amount: '$1,200.00', note: 'Due now' });
    expect(policyCardAmount(old)).toEqual({ amount: '$1,200.00', note: 'Refund on its way to your card ending 4242' });
    expect(policyCardAmount(row({ status: 'CANCELLED', balanceCents: -500, refundWaiting: { amountCents: 500, cardLast4: null } }))).toEqual({
      amount: '$5.00',
      note: 'Refund on its way to your card',
    });
    expect(policyCardAmount(row({ paymentWaiting: true }))).toEqual({ amount: '$1,200.00', note: 'Payment waiting for your bank' });
    expect(policyCardAmount(row({ balanceCents: 0 }))).toEqual({ amount: '$0.00', note: 'Nothing due' });
  });

  it('links a rewrite and the policy it replaced, both ways', () => {
    expect(replacedNote(rewrite)).toBe('Replaces GL-2026-0013');
    expect(replacedNote(old)).toBe('Replaced by GL-2026-0014');
    expect(replacedNote(row({}))).toBeNull();
    expect(
      replacedNote(row({ replaces: { policyId: 13, policyNumber: 'GL-2026-0013' }, replacedBy: { policyId: 15, policyNumber: 'GL-2026-0015' } })),
    ).toBe('Replaces GL-2026-0013 · replaced by GL-2026-0015');
  });
});

// covers AC8 — the "All policies" link shows only to owners with two or more
describe('the All policies link', () => {
  it('shows from two policies up', () => {
    expect(showAllPolicies(1)).toBe(false);
    expect(showAllPolicies(2)).toBe(true);
    expect(showAllPolicies(3)).toBe(true);
  });
});

// covers AC1, AC2, AC3 — the broker issues another policy, or rewrites a cancelled one
describe('rewrite on B2 and B3', () => {
  it('names the two actions', () => {
    expect(NEW_POLICY_FOR_CUSTOMER).toBe('New policy for this customer');
    expect(REWRITE_POLICY).toBe('Rewrite this policy');
  });

  it("notes on B2 which policy is replaced and when it was cancelled", () => {
    expect(rewriteNote('GL-2026-0013', '2026-09-01')).toBe('Replaces GL-2026-0013 (cancelled Sep 1, 2026)');
  });

  it('offers a rewrite only for a cancelled policy not yet replaced', () => {
    expect(canRewrite({ status: 'CANCELLED', replacedBy: null })).toBe(true);
    expect(canRewrite({ status: 'CANCELLED', replacedBy: { policyId: 14, policyNumber: 'GL-2026-0014' } })).toBe(false);
    expect(canRewrite({ status: 'ACTIVE', replacedBy: null })).toBe(false);
  });
});

// covers AC11 — Rewrite and Flat cancellation are standard terms, each with its "?"
describe('the new terms', () => {
  it('explains rewrite and flat cancellation', () => {
    const terms = Object.fromEntries(TERM_PATTERNS) as Record<string, string>;
    expect(terms.rewrite).toBeDefined();
    expect(terms.flatCancellation).toBeDefined();
    const glossary = GLOSSARY as Record<string, string>;
    expect(glossary.rewrite).toMatch(/cancelled/);
    expect(glossary.flatCancellation).toMatch(/start date/);
  });

  it('spots them inside a sentence', () => {
    const found = glossParts('Rewrite it after a flat cancellation. Two rewrites, one rewritten.').filter((p) => typeof p !== 'string');
    expect(found).toEqual([
      { word: 'Rewrite', term: 'rewrite' },
      { word: 'flat cancellation', term: 'flatCancellation' },
    ]);
  });
});
