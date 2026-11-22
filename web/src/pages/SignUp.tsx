import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { Link, Navigate, useNavigate } from 'react-router';
import { ApiError, signUp, useSession } from '../api/client';
import type { SignedInUser, SignUpRequest, ValidationBody } from '../api/types';
import { AuthField } from '../components/AuthField';
import { AuthLayout } from '../components/AuthLayout';
import { homeFor } from '../components/RouteGuards';

type Field = keyof SignUpRequest;

/** The API's message when the email already has an account; the field then offers a sign-in link. */
export const ALREADY_SIGNED_UP = 'This email already has an account. Sign in instead.';

/** Field errors from a refused sign-up, or one general message when something else went wrong. */
export function signUpProblems(e: unknown): { fields: Partial<Record<Field, string>>; general: string | null } {
  if (e instanceof ApiError && e.status === 422) {
    return { fields: (e.body as ValidationBody).fields as Partial<Record<Field, string>>, general: null };
  }
  return { fields: {}, general: "We couldn't reach Plainly. Check your connection and try again." };
}

/**
 * Create your account. Owners can't buy here: their broker issues the policy and enters their email,
 * and the owner then signs up with that email and a password of their own.
 */
export function SignUp() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const session = useSession();
  const [form, setForm] = useState<SignUpRequest>({ email: '', password: '', confirmPassword: '' });
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({});
  const [general, setGeneral] = useState<string | null>(null);
  const summaryRef = useRef<HTMLParagraphElement>(null);

  const submit = useMutation({
    mutationFn: signUp,
    onSuccess: (user: SignedInUser) => {
      queryClient.clear();
      queryClient.setQueryData(['session'], user);
      navigate(homeFor(user.role), { replace: true });
    },
    onError: (e) => {
      const p = signUpProblems(e);
      setErrors(p.fields);
      setGeneral(p.general);
    },
  });

  const open = Object.values(errors).filter(Boolean).length;
  useEffect(() => {
    if (submit.isError) summaryRef.current?.focus();
  }, [submit.isError, submit.failureCount]);

  if (session.data) return <Navigate to={homeFor(session.data.role)} replace />;

  const set = (field: Field) => (value: string) => {
    setForm((f) => ({ ...f, [field]: value }));
    setErrors((e) => (e[field] ? { ...e, [field]: undefined } : e));
  };

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    submit.mutate(form);
  }

  const summary = general ?? (open > 0 ? `${open} ${open === 1 ? 'thing' : 'things'} to fix before we can create your account.` : null);

  return (
    <AuthLayout>
      <section aria-labelledby="signup-title" className="mt-10 flex flex-col gap-4">
        <div className="flex flex-col gap-1.5">
          <h1 id="signup-title" className="m-0 text-2xl font-bold">
            Create your account
          </h1>
          <p className="m-0 text-[15px] leading-normal text-ink-2">
            Your broker has already set up your policy. Use the same email they have for you, choose a password, and you'll
            see what you're covered for and what you owe.
          </p>
        </div>

        {summary && (
          <p ref={summaryRef} tabIndex={-1} role="alert" className="m-0 rounded-control bg-bad-soft px-3 py-2.5 text-[15px] font-semibold text-bad">
            {summary}
          </p>
        )}

        <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
          <AuthField
            id="email"
            type="email"
            label="Email"
            hint="Use the email your broker has on file for your business."
            autoComplete="username"
            value={form.email}
            onChange={set('email')}
            error={errors.email}
            errorAction={
              errors.email === ALREADY_SIGNED_UP ? (
                <Link to="/signin" className="font-semibold">
                  Go to sign in
                </Link>
              ) : undefined
            }
          />
          <AuthField
            id="password"
            type="password"
            label="Password"
            hint="At least 10 characters."
            autoComplete="new-password"
            value={form.password}
            onChange={set('password')}
            error={errors.password}
          />
          <AuthField
            id="confirmPassword"
            type="password"
            label="Type the password again"
            autoComplete="new-password"
            value={form.confirmPassword}
            onChange={set('confirmPassword')}
            error={errors.confirmPassword}
          />
          <button
            type="submit"
            disabled={submit.isPending}
            className="inline-flex min-h-12 items-center justify-center rounded-control bg-accent px-5 text-[17px] font-bold text-on-accent disabled:opacity-60"
          >
            {submit.isPending ? 'Creating your account…' : 'Create account'}
          </button>
        </form>

        <p className="m-0 text-[15px] leading-normal text-ink-2">
          Already have an account?{' '}
          <Link to="/signin" className="font-semibold">
            Sign in
          </Link>
        </p>
      </section>
    </AuthLayout>
  );
}
