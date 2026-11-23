import type { ReactNode } from 'react';
import { Navigate } from 'react-router';
import { useSession } from '../api/client';
import type { Role, SignedInUser } from '../api/types';
import { Landing } from '../pages/Landing';

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

/** What `/` shows: the landing page for a guest, or the signed-in person's home. */
export function homeTarget(user: SignedInUser | null | undefined): 'landing' | string {
  return user ? homeFor(user.role) : 'landing';
}

/**
 * `/`: the landing page for guests (L1), everyone signed in straight to their home. While the session loads it
 * shows the calm loading line, so a signed-in person never sees the landing page flash first. If the session
 * check fails, the landing page still shows: it needs no account, and its Sign in link is the way on.
 */
export function Home() {
  const session = useSession();
  if (session.isPending) return <Waiting />;
  const target = homeTarget(session.data);
  return target === 'landing' ? <Landing /> : <Navigate to={target} replace />;
}
