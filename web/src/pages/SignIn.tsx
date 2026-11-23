import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router';
import { ApiError, signIn, useSession } from '../api/client';
import type { SignedInUser } from '../api/types';
import { AuthField } from '../components/AuthField';
import { AuthLayout } from '../components/AuthLayout';
import { homeFor } from '../components/RouteGuards';

/** Fictional demo accounts seeded for reviewers (V5 migration and DemoSeeder). Test credentials only. */
export const DEMO_PASSWORD = 'plainly-demo-2026';
export const DEMO_ACCOUNTS = [
  { name: 'Maya Okafor', role: 'Policyholder', detail: 'Owner, Juniper & Rye Bakery LLC', email: 'maya@juniperandrye.test' },
  { name: 'Sam Reyes', role: 'Broker', detail: 'Issues and manages policies', email: 'sam@plainly.test' },
] as const;

const WRONG = "That email and password don't match. Check them and try again.";

/** What to tell someone whose sign-in failed. A 401 never says which part was wrong. */
export function signInProblem(e: unknown): string {
  if (e instanceof ApiError && e.status === 401) return WRONG;
  return "We couldn't reach Plainly. Check your connection and try again.";
}

/**
 * The details to fill in when the landing page's "Try as …" link opened sign-in with `?demo=<email>`. Only the
 * seeded demo accounts count; any other value is ignored, and nothing is submitted for the person.
 */
export function demoPrefill(search: string): { email: string; password: string } | null {
  const email = new URLSearchParams(search).get('demo');
  const account = DEMO_ACCOUNTS.find((a) => a.email === email);
  return account ? { email: account.email, password: DEMO_PASSWORD } : null;
}

/** Sign in with email and password. Brokers' accounts are made for them; owners sign up first. */
export function SignIn() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const session = useSession();
  const { search } = useLocation();
  const [email, setEmail] = useState(() => demoPrefill(search)?.email ?? '');
  const [password, setPassword] = useState(() => demoPrefill(search)?.password ?? '');
  const alertRef = useRef<HTMLParagraphElement>(null);

  const submit = useMutation({
    mutationFn: signIn,
    onSuccess: (user: SignedInUser) => {
      // A different person now: drop anything cached for the previous one.
      queryClient.clear();
      queryClient.setQueryData(['session'], user);
      navigate(homeFor(user.role), { replace: true });
    },
  });

  useEffect(() => {
    if (submit.isError) alertRef.current?.focus();
  }, [submit.isError, submit.failureCount]);

  if (session.data) return <Navigate to={homeFor(session.data.role)} replace />;

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    submit.mutate({ email, password });
  }

  function fillDemo(demoEmail: string) {
    setEmail(demoEmail);
    setPassword(DEMO_PASSWORD);
    submit.reset();
  }

  return (
    <AuthLayout>
      <section aria-labelledby="signin-title" className="mt-10 flex flex-col gap-4">
        <h1 id="signin-title" className="m-0 text-2xl font-bold">
          Sign in
        </h1>

        {submit.isError && (
          <p ref={alertRef} tabIndex={-1} role="alert" className="m-0 rounded-control bg-bad-soft px-3 py-2.5 text-[15px] font-semibold text-bad">
            {signInProblem(submit.error)}
          </p>
        )}

        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          <AuthField id="email" type="email" label="Email" autoComplete="username" value={email} onChange={setEmail} />
          <AuthField id="password" type="password" label="Password" autoComplete="current-password" value={password} onChange={setPassword} />
          <button
            type="submit"
            disabled={submit.isPending}
            className="inline-flex min-h-12 items-center justify-center rounded-control bg-accent px-5 text-[17px] font-bold text-on-accent disabled:opacity-60"
          >
            {submit.isPending ? 'Signing in…' : 'Sign in'}
          </button>
        </form>

        <p className="m-0 text-[15px] leading-normal text-ink-2">
          New here? Your broker set up your policy.{' '}
          <Link to="/signup" className="font-semibold">
            Create your account
          </Link>
        </p>
      </section>

      <section aria-labelledby="demo-title" className="mt-8 flex flex-col gap-3 rounded-2xl border border-dashed border-line bg-card p-4">
        <h2 id="demo-title" className="m-0 text-[15px] font-semibold text-ink-2">
          Demo accounts · password <span className="font-mono text-ink">{DEMO_PASSWORD}</span>
        </h2>
        <ul className="m-0 flex list-none flex-col gap-2 p-0">
          {DEMO_ACCOUNTS.map((a) => (
            <li key={a.email} className="flex flex-wrap items-center gap-x-3 gap-y-1">
              <span className="flex min-w-0 flex-1 flex-col">
                <span className="text-[15px] font-bold">
                  {a.name} · {a.role}
                </span>
                <span className="truncate text-sm text-ink-2">{a.email}</span>
              </span>
              <button
                type="button"
                onClick={() => fillDemo(a.email)}
                className="min-h-10 rounded-control border-[1.5px] border-line px-3 text-sm font-semibold hover:border-accent"
                aria-label={`Fill in ${a.name}'s demo email and password`}
              >
                Use these
              </button>
            </li>
          ))}
        </ul>
      </section>
    </AuthLayout>
  );
}
