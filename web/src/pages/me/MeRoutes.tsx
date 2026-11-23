import { Navigate } from 'react-router';
import { legacyRedirect, meLanding, mePolicyPath, type MePage } from '../../lib/mePaths';
import { PoliciesPage } from './PoliciesPage';
import { useMyPolicies } from './useMyPolicies';

function Message({ children }: { children: string }) {
  return (
    <div className="flex min-h-dvh justify-center bg-bg">
      <p className="w-full max-w-[480px] px-5 py-10 text-[17px] leading-snug">{children}</p>
    </div>
  );
}

/**
 * `/me` (more-policies AC7): with one policy, its home, as before; with two or more, "Your policies". One policy is
 * redirected to `/me/policies/:id` so every page has one address, the same one the emails link to.
 */
export function MeLanding() {
  const policies = useMyPolicies();
  if (policies.isPending) return <Message>Loading your cover…</Message>;
  if (policies.isError) return <Message>We couldn't load your cover. Reload to try again.</Message>;
  const landing = meLanding(policies.data.policies);
  if (landing.kind === 'none') return <Message>You don't have a policy yet. Your broker will set one up for you.</Message>;
  if (landing.kind === 'home') return <Navigate to={mePolicyPath(landing.policyId)} replace />;
  return <PoliciesPage data={policies.data} />;
}

/** The old single-policy pages (`/me/pay`, `/me/statement`…) go to that page of the newest policy (more-policies AC8). */
export function LegacyRedirect({ page }: { page: Exclude<MePage, 'home'> }) {
  const policies = useMyPolicies();
  if (policies.isPending) return <Message>Loading your cover…</Message>;
  return <Navigate to={policies.isError ? '/me' : legacyRedirect(page, policies.data.policies)} replace />;
}
