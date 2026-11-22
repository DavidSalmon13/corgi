import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router';
import { api } from '../api/client';
import type { DemoUser } from '../api/types';
import { homeFor } from '../components/RouteGuards';
import { ThemeToggle } from '../components/ThemeToggle';

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]!.toUpperCase())
    .join('');
}

function firstName(name: string): string {
  return name.split(/\s+/)[0] ?? name;
}

/** The Plainly corgi mark, from the sign-in design. */
function Mark() {
  return (
    <svg width="64" height="64" viewBox="0 0 56 56" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" strokeLinecap="round" aria-hidden="true" className="text-ink">
      <path d="M14 27 L11 7 L25 18 Z" className="fill-accent" />
      <path d="M42 27 L45 7 L31 18 Z" className="fill-accent" />
      <path d="M12 30 C12 18 44 18 44 30 C44 41 37 48 28 48 C19 48 12 41 12 30 Z" className="fill-accent" />
      <path d="M21 39 C21 34 35 34 35 39 C35 44 21 44 21 39 Z" className="fill-card" />
      <circle cx="21" cy="30" r="1.6" fill="currentColor" />
      <circle cx="35" cy="30" r="1.6" fill="currentColor" />
      <path d="M26 37 h4 l-2 2 z" fill="currentColor" />
    </svg>
  );
}

function RoleButton({ user, busy, onChoose }: { user: DemoUser; busy: boolean; onChoose: () => void }) {
  const broker = user.role === 'BROKER';
  return (
    <button
      type="button"
      onClick={onChoose}
      disabled={busy}
      className="flex min-h-[76px] w-full items-center gap-3.5 rounded-[14px] border-[1.5px] border-line bg-card px-4 py-3.5 text-left text-ink hover:border-accent disabled:opacity-60"
    >
      <span
        aria-hidden="true"
        className={`flex size-11 flex-none items-center justify-center rounded-full font-bold ${broker ? 'bg-track text-ink' : 'bg-accent text-on-accent'}`}
      >
        {initials(user.displayName)}
      </span>
      <span className="flex flex-1 flex-col">
        <span className="text-[17px] font-bold">
          Continue as {firstName(user.displayName)}, the {broker ? 'broker' : 'policyholder'}
        </span>
        <span className="text-sm text-ink-2">{user.subtitle}</span>
      </span>
    </button>
  );
}

/** Demo sign-in: pick a person to be. Policyholders first, as in the design, then the broker. */
export function SignIn() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const users = useQuery({ queryKey: ['demo-users'], queryFn: () => api<DemoUser[]>('/api/demo/users') });
  const signIn = useMutation({
    mutationFn: (userId: number) => api<DemoUser>('/api/demo/session', { method: 'POST', body: JSON.stringify({ userId }) }),
    onSuccess: (user) => {
      // A different person now: drop anything cached for the previous one.
      queryClient.clear();
      queryClient.setQueryData(['session'], user);
      navigate(homeFor(user.role), { replace: true });
    },
  });

  const ordered = [...(users.data ?? [])].sort((a, b) => (a.role === b.role ? 0 : a.role === 'POLICYHOLDER' ? -1 : 1));

  return (
    <div className="flex min-h-dvh justify-center bg-bg">
      <main className="flex w-full max-w-[430px] flex-col px-5 py-6">
        <div className="flex items-center justify-between gap-3">
          <span className="rounded-full bg-accent-soft px-2.5 py-1 text-[13px] font-bold uppercase tracking-[.06em] text-accent-ink">
            Demo · fictional people and money
          </span>
          <ThemeToggle />
        </div>

        <div className="mt-16 flex flex-col gap-2.5 sm:mt-24">
          <Mark />
          <h1 className="m-0 text-[44px] font-bold leading-tight tracking-[-0.025em]">Plainly</h1>
          <p className="m-0 text-[19px] leading-snug text-ink-2">Business insurance, explained in plain language.</p>
        </div>

        <section aria-labelledby="choose" className="mt-12 flex flex-col gap-3">
          <h2 id="choose" className="m-0 text-[15px] font-semibold text-ink-2">
            Choose who to be
          </h2>
          {users.isPending && <p className="text-[15px] text-ink-2">Loading people…</p>}
          {users.isError && (
            <p role="alert" className="text-[15px] text-bad">
              We couldn't load the demo people. Check the API is running and reload.
            </p>
          )}
          {ordered.map((u) => (
            <RoleButton key={u.id} user={u} busy={signIn.isPending} onChoose={() => signIn.mutate(u.id)} />
          ))}
          {users.data && !users.data.some((u) => u.role === 'POLICYHOLDER') && (
            <p className="text-sm text-ink-2">
              No policyholders yet. Sign in as the broker and create a policy; its owner then appears here.
            </p>
          )}
          {signIn.isError && (
            <p role="alert" className="text-[15px] text-bad">
              That didn't work. Try again.
            </p>
          )}
        </section>

        <p className="mt-auto pt-12 text-sm leading-normal text-ink-2">
          This is a demo. No real insurance is sold, and payments use Stripe's test mode.
        </p>
      </main>
    </div>
  );
}
