import { useNavigate } from 'react-router';
import { useSignOut } from '../api/client';

/** Ends the session on the server, forgets this person's cached data and returns to sign-in. */
export function SignOutButton({ className = '' }: { className?: string }) {
  const navigate = useNavigate();
  const signOut = useSignOut();
  return (
    <button
      type="button"
      disabled={signOut.isPending}
      onClick={() => signOut.mutate(undefined, { onSettled: () => navigate('/signin', { replace: true }) })}
      className={`min-h-11 rounded-control px-2 text-[15px] font-semibold text-accent-ink hover:bg-accent-soft disabled:opacity-60 ${className}`}
    >
      {signOut.isPending ? 'Signing out…' : 'Sign out'}
    </button>
  );
}
