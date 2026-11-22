import type { ReactNode } from 'react';
import { Navigate } from 'react-router';
import { useSession } from '../api/client';
import type { Role } from '../api/types';

/** Where each role starts. */
export function homeFor(role: Role): string {
  return role === 'BROKER' ? '/broker/policies' : '/me';
}

function Waiting() {
  return <p className="px-5 py-10 text-[15px] text-ink-2">Loading…</p>;
}

/**
 * Only lets the given role through. Nobody signed in goes to sign-in; the other role goes to its own home.
 * The API enforces the same rule with a 403, so this is about not showing a page that can't load.
 */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const session = useSession();
  if (session.isPending) return <Waiting />;
  if (session.isError) {
    return <p className="px-5 py-10 text-[15px] text-bad">We couldn't reach Plainly. Check the API is running and reload.</p>;
  }
  if (!session.data) return <Navigate to="/signin" replace />;
  if (session.data.role !== role) return <Navigate to={homeFor(session.data.role)} replace />;
  return <>{children}</>;
}

/** `/`: send people to their home, or to sign-in. */
export function HomeRedirect() {
  const session = useSession();
  if (session.isPending) return <Waiting />;
  return <Navigate to={session.data ? homeFor(session.data.role) : '/signin'} replace />;
}
