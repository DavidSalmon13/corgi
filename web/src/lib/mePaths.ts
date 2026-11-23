import type { MyPolicyRow } from '../api/types';

// Where each policy's pages and API reads live, now that an owner can hold several policies (more-policies spec).

/** The policyholder's pages for one policy: P1 home, P5 pay, P3 statement, P2 timeline, P6 cancellation. */
export type MePage = 'home' | 'pay' | 'statement' | 'timeline' | 'cancellation';

/** "/me/policies/7", or "/me/policies/7/pay" for one of its pages. */
export function mePolicyPath(policyId: number, page: MePage = 'home'): string {
  const home = `/me/policies/${policyId}`;
  return page === 'home' ? home : `${home}/${page}`;
}

/** "/api/me/policies/7/cover", "/statement" or "/timeline". */
export function meApiPath(policyId: number, read: 'cover' | 'statement' | 'timeline'): string {
  return `/api/me/policies/${policyId}/${read}`;
}

/** B2 in customer mode: "/broker/customers/3/policies/new", with "?replaces=9" for a rewrite. */
export function newPolicyPath(customerId: number, replacesPolicyId?: number | null): string {
  const path = `/broker/customers/${customerId}/policies/new`;
  return replacesPolicyId == null ? path : `${path}?replaces=${replacesPolicyId}`;
}

/** What `/me` shows: the single policy's home, the "Your policies" overview, or nothing to show yet. */
export type MeLanding = { kind: 'home'; policyId: number } | { kind: 'overview' } | { kind: 'none' };

export function meLanding(policies: MyPolicyRow[]): MeLanding {
  if (policies.length === 0) return { kind: 'none' };
  if (policies.length === 1) return { kind: 'home', policyId: policies[0].policyId };
  return { kind: 'overview' };
}

/** Where an old single-policy route (/me/pay, /me/statement…) goes: that page of the newest policy. */
export function legacyRedirect(page: Exclude<MePage, 'home'>, policies: MyPolicyRow[]): string {
  // The API lists policies newest first.
  return policies.length === 0 ? '/me' : mePolicyPath(policies[0].policyId, page);
}

/** TanStack Query keys, one per policy, so paying one policy never shows another's numbers. */
export function myCoverKey(policyId: number): readonly unknown[] {
  return ['my-cover', policyId];
}
export function myStatementKey(policyId: number): readonly unknown[] {
  return ['my-statement', policyId];
}
export function myTimelineKey(policyId: number): readonly unknown[] {
  return ['my-timeline', policyId];
}
export const MY_POLICIES_KEY = ['my-policies'] as const;
