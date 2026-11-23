import { useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';
import { Link, useParams } from 'react-router';
import { ALL_POLICIES, showAllPolicies } from '../../lib/policiesCopy';
import { MY_POLICIES_KEY, myCoverKey, myStatementKey, myTimelineKey } from '../../lib/mePaths';
import { useMyPolicies } from './useMyPolicies';

/** The policy a `/me/policies/:policyId/…` page is about. The API checks the owner holds it (more-policies AC9). */
export function usePolicyId(): number {
  const { policyId } = useParams();
  return Number(policyId);
}

/** How many policies the owner holds, and whether pages should offer the way back to "Your policies". */
export function useSeveralPolicies(): boolean {
  const policies = useMyPolicies();
  return showAllPolicies(policies.data?.policies.length ?? 0);
}

/**
 * After money moves on one policy, its own reads and the overview are out of date. Other policies are untouched:
 * a payment pays only its own policy's balance (more-policies AC9).
 */
export function useRefreshPolicy(): (policyId: number) => void {
  const queryClient = useQueryClient();
  return useCallback(
    (policyId: number) => {
      for (const key of [myCoverKey(policyId), myStatementKey(policyId), myTimelineKey(policyId), MY_POLICIES_KEY]) {
        void queryClient.invalidateQueries({ queryKey: key });
      }
    },
    [queryClient],
  );
}

/** "‹ All policies", shown only to an owner with two or more (more-policies AC8). */
export function AllPoliciesLink() {
  if (!useSeveralPolicies()) return null;
  return (
    <Link
      to="/me"
      className="inline-flex min-h-11 items-center gap-1 self-start text-[15px] font-semibold text-accent-ink no-underline hover:text-ink"
    >
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
        <path d="M15 18l-6-6 6-6" />
      </svg>
      {ALL_POLICIES}
    </Link>
  );
}

/** The right of a page's header: "All policies" for an owner with two or more, else the Plainly name. */
export function HeaderEnd() {
  return useSeveralPolicies() ? <AllPoliciesLink /> : <span className="text-lg font-bold tracking-[-0.01em]">Plainly</span>;
}
