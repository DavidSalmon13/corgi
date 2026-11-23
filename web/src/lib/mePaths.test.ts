import { describe, expect, it } from 'vitest';
import type { MyPolicyRow } from '../api/types';
import {
  legacyRedirect,
  meApiPath,
  meLanding,
  mePolicyPath,
  myCoverKey,
  myStatementKey,
  myTimelineKey,
  newPolicyPath,
} from './mePaths';

// The worked example: GL-2026-0013 flat-cancelled and rewritten as GL-2026-0014 (newest first, as the API lists them).
const rewrite: MyPolicyRow = {
  policyId: 14,
  policyNumber: 'GL-2026-0014',
  start: '2026-10-01',
  end: '2027-10-01',
  status: 'ACTIVE',
  balanceCents: 120_000,
  paymentWaiting: false,
  refundWaiting: null,
  replaces: { policyId: 13, policyNumber: 'GL-2026-0013' },
  replacedBy: null,
};
const old: MyPolicyRow = {
  policyId: 13,
  policyNumber: 'GL-2026-0013',
  start: '2026-09-01',
  end: '2027-09-01',
  status: 'CANCELLED',
  balanceCents: -120_000,
  paymentWaiting: false,
  refundWaiting: { amountCents: 120_000, cardLast4: '4242' },
  replaces: null,
  replacedBy: { policyId: 14, policyNumber: 'GL-2026-0014' },
};

// covers AC8, T2 — every policyholder page lives under its policy
describe('per-policy pages', () => {
  it('puts the home and each page under /me/policies/:id', () => {
    expect(mePolicyPath(14)).toBe('/me/policies/14');
    expect(mePolicyPath(14, 'home')).toBe('/me/policies/14');
    expect(mePolicyPath(14, 'pay')).toBe('/me/policies/14/pay');
    expect(mePolicyPath(14, 'statement')).toBe('/me/policies/14/statement');
    expect(mePolicyPath(14, 'timeline')).toBe('/me/policies/14/timeline');
    expect(mePolicyPath(13, 'cancellation')).toBe('/me/policies/13/cancellation');
  });

  // covers AC9 — each read names its policy, so the API can check the owner holds it
  it('reads each policy from its own API route', () => {
    expect(meApiPath(14, 'cover')).toBe('/api/me/policies/14/cover');
    expect(meApiPath(14, 'statement')).toBe('/api/me/policies/14/statement');
    expect(meApiPath(14, 'timeline')).toBe('/api/me/policies/14/timeline');
  });

  it('keeps each policy in its own cache, so one policy never shows another one’s numbers', () => {
    expect(myCoverKey(14)).toEqual(['my-cover', 14]);
    expect(myStatementKey(14)).toEqual(['my-statement', 14]);
    expect(myTimelineKey(14)).toEqual(['my-timeline', 14]);
    expect(myCoverKey(13)).not.toEqual(myCoverKey(14));
  });
});

// covers AC7 — one policy is today's home; two or more open "Your policies"
describe('what /me shows', () => {
  it("opens the only policy's home when there is one", () => {
    expect(meLanding([old])).toEqual({ kind: 'home', policyId: 13 });
  });

  it('shows the overview when there are two or more', () => {
    expect(meLanding([rewrite, old])).toEqual({ kind: 'overview' });
  });

  it('has nothing to open before the first policy', () => {
    expect(meLanding([])).toEqual({ kind: 'none' });
  });
});

// covers AC8 — old bookmarks and email links still land somewhere sensible
describe('the old single-policy routes', () => {
  it("go to that page of the newest policy", () => {
    expect(legacyRedirect('pay', [rewrite, old])).toBe('/me/policies/14/pay');
    expect(legacyRedirect('statement', [rewrite, old])).toBe('/me/policies/14/statement');
    expect(legacyRedirect('timeline', [old])).toBe('/me/policies/13/timeline');
    expect(legacyRedirect('cancellation', [old])).toBe('/me/policies/13/cancellation');
  });

  it('go back to /me when there is no policy yet', () => {
    expect(legacyRedirect('pay', [])).toBe('/me');
  });
});

// covers AC1, AC2 — B2 in customer mode, and a rewrite of a cancelled policy
describe('new policy for an existing customer', () => {
  it('opens B2 for the customer, naming the policy it replaces when it is a rewrite', () => {
    expect(newPolicyPath(3)).toBe('/broker/customers/3/policies/new');
    expect(newPolicyPath(3, null)).toBe('/broker/customers/3/policies/new');
    expect(newPolicyPath(3, 13)).toBe('/broker/customers/3/policies/new?replaces=13');
  });
});
