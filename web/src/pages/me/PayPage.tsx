import { Elements, PaymentElement, useElements, useStripe } from '@stripe/react-stripe-js';
import type { Stripe } from '@stripe/stripe-js';
import { loadStripe } from '@stripe/stripe-js/pure';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { HeaderEnd, usePolicyId, useRefreshPolicy, useSeveralPolicies } from './MeNav';
import { meApiPath, mePolicyPath, myCoverKey } from '../../lib/mePaths';
import { Link } from 'react-router';
import { api, ApiError } from '../../api/client';
import type { MyCover, StartedPayment } from '../../api/types';
import { Mark } from '../../components/Mark';
import { dueReasonText } from '../../lib/myCoverCopy';
import { PAYMENTS_OFF, SEND_UNKNOWN, STRIPE_NOTE, payLabel, payingFor, savedCardLabel } from '../../lib/paymentCopy';
import { STRIPE_FONTS, stripeAppearance } from '../../lib/stripeAppearance';
import { PRIMARY, PaymentProgress, SECONDARY, type Submitted } from './PaymentProgress';

const CARD = 'rounded-2xl border border-line bg-card';

/**
 * One Stripe.js instance per publishable key, loaded only when someone opens the pay page. The `/pure` import is what
 * makes that true: the plain `@stripe/stripe-js` import adds Stripe's script (and its fraud-signal calls to
 * m.stripe.com) to every page the moment the app starts, broker pages included.
 */
const stripes = new Map<string, Promise<Stripe | null>>();
function stripeFor(key: string): Promise<Stripe | null> {
  let s = stripes.get(key);
  if (!s) {
    s = loadStripe(key);
    stripes.set(key, s);
  }
  return s;
}

/** Why a payment couldn't start, in the words the API gave (409 nothing due / in flight, 503 payments off). */
function startProblem(e: unknown): { error: string; message: string } | null {
  if (e instanceof ApiError && e.body && typeof e.body === 'object' && 'error' in e.body) {
    const b = e.body as { error: string; message?: string };
    return { error: b.error, message: b.message ?? '' };
  }
  return null;
}

function Shell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh justify-center bg-bg">
      <div className="flex w-full max-w-[480px] flex-col">
        <header className="flex items-center justify-between pl-2 pr-5 pt-2">
          <Link to={mePolicyPath(usePolicyId())} className="flex min-h-11 items-center gap-1 px-2 text-base font-semibold text-accent-ink no-underline hover:text-ink">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M15 18l-6-6 6-6" />
            </svg>
            Your cover
          </Link>
          <HeaderEnd />
        </header>
        <main className="flex flex-col gap-5 px-5 pb-8 pt-3">{children}</main>
      </div>
    </div>
  );
}

function Notice({ title, body }: { title: string; body: string }) {
  const policyId = usePolicyId();
  return (
    <>
      <section className={`${CARD} mt-10 flex items-center gap-4 p-5`}>
        <Mark size={56} />
        <div>
          <h1 className="m-0 text-[19px] font-semibold">{title}</h1>
          {body && <p className="mt-1 mb-0 text-[15px] leading-snug text-ink-2">{body}</p>}
        </div>
      </section>
      <Link to={mePolicyPath(policyId)} className={SECONDARY}>
        Back to your cover
      </Link>
    </>
  );
}

/** P5 "Pay": take a card for the balance due. The amount always comes from the server. */
export function PayPage() {
  const policyId = usePolicyId();
  const several = useSeveralPolicies();
  const refresh = useRefreshPolicy();
  // Bumping the attempt opens a fresh payment, e.g. after a decline ("Try again").
  const [attempt, setAttempt] = useState(0);
  const [preferNewCard, setPreferNewCard] = useState(false);
  const [submitted, setSubmitted] = useState<Submitted | null>(null);

  const cover = useQuery({ queryKey: myCoverKey(policyId), queryFn: () => api<MyCover>(meApiPath(policyId, 'cover')) });
  // more-policies AC9: the payment is for this policy only; the API checks the owner holds it.
  const start = useQuery({
    queryKey: ['pay-start', policyId, attempt],
    queryFn: () => api<StartedPayment>('/api/me/payments', { method: 'POST', body: JSON.stringify({ policyId }) }),
    staleTime: Infinity,
    gcTime: 0,
    retry: false,
  });

  const retry = (newCard: boolean) => {
    setSubmitted(null);
    setPreferNewCard(newCard);
    setAttempt((n) => n + 1);
    refresh(policyId);
  };

  if (submitted) {
    return (
      <Shell>
        <PaymentProgress
          submitted={submitted}
          ownerName={cover.data?.business.ownerName ?? ''}
          policyId={policyId}
          policyNumber={several ? (cover.data?.policy.policyNumber ?? null) : null}
          onRetry={retry}
        />
      </Shell>
    );
  }
  if (start.isPending || cover.isPending) {
    return (
      <Shell>
        <p className="text-[15px] text-ink-2">Getting your payment ready…</p>
      </Shell>
    );
  }
  if (start.isError) {
    const p = startProblem(start.error);
    if (p?.error === 'nothing_due') return <Shell><Notice title="You're all paid up." body="Nothing to do right now." /></Shell>;
    if (p?.error === 'payment_in_flight')
      return <Shell><Notice title="Your last payment is still on its way." body="We'll show it as paid once your bank confirms. You don't need to pay again." /></Shell>;
    if (p?.error === 'payments_off') return <Shell><Notice title="You can't pay here yet" body={PAYMENTS_OFF} /></Shell>;
    return <Shell><Notice title="We couldn't get your payment ready." body={p?.message || 'Reload to try again. Nothing was charged.'} /></Shell>;
  }

  const s = start.data;
  const reason = cover.data ? dueReasonText(cover.data.dueReason) : '';
  return (
    <Shell>
      <div>
        {several && cover.data && (
          <p className="m-0 mb-1 text-sm font-semibold text-ink-2">{payingFor(cover.data.policy.policyNumber)}</p>
        )}
        <h1 className="m-0 text-[40px] font-bold tracking-[-0.02em] text-accent-ink tabular-nums">{payLabel(s.amountCents)}</h1>
        {reason && (
          <p className="mt-1.5 mb-0 text-[17px] leading-snug">
            {reason}{' '}
            <Link to={mePolicyPath(policyId, 'statement')} className="font-semibold text-accent-ink">
              See why
            </Link>
          </p>
        )}
      </div>
      <Elements
        key={s.clientSecret}
        stripe={stripeFor(s.publishableKey)}
        options={{ clientSecret: s.clientSecret, appearance: stripeAppearance(), fonts: STRIPE_FONTS }}
      >
        <CardForm started={s} startWithNewCard={preferNewCard || !s.savedCard} onSubmitted={setSubmitted} />
      </Elements>
      <p className="m-0 text-center text-sm leading-snug text-ink-2">{STRIPE_NOTE}</p>
    </Shell>
  );
}

function CardForm({
  started,
  startWithNewCard,
  onSubmitted,
}: {
  started: StartedPayment;
  startWithNewCard: boolean;
  onSubmitted: (s: Submitted) => void;
}) {
  const stripe = useStripe();
  const elements = useElements();
  const [useNewCard, setUseNewCard] = useState(startWithNewCard);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const alertRef = useRef<HTMLParagraphElement>(null);
  const saved = started.savedCard;

  useEffect(() => {
    if (problem) alertRef.current?.focus();
  }, [problem]);

  // Hands over to the waiting screen. Only Stripe's webhook decides paid or declined from here.
  const sent = (last4: string | null) =>
    onSubmitted({ paymentId: started.paymentId, amountCents: started.amountCents, last4, at: new Date().toISOString() });

  async function payWithSavedCard() {
    const answer = await api<{ status: string; clientSecret: string | null }>(
      `/api/me/payments/${started.paymentId}/saved-card`,
      { method: 'POST' },
    );
    if (answer.status === 'requires_action' && answer.clientSecret && stripe) {
      // The bank wants the owner to approve it (3-D Secure); Stripe shows that step itself.
      const { error } = await stripe.handleNextAction({ clientSecret: answer.clientSecret });
      if (error && error.type !== 'card_error') {
        setProblem(error.message ?? "Your bank didn't finish checking the card. Try again.");
        return;
      }
    }
    sent(saved?.last4 ?? null);
  }

  async function payWithNewCard() {
    if (!stripe || !elements) return;
    const { error } = await stripe.confirmPayment({ elements, redirect: 'if_required' });
    // A card the bank declines still reaches us as a charge.failed webhook, so that's "sent" too.
    // Anything else (an unfinished card number, say) never left the page: say what to fix.
    if (error && error.type !== 'card_error') {
      setProblem(error.message ?? 'Check your card details and try again.');
      return;
    }
    sent(null);
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setProblem(null);
    try {
      await (saved && !useNewCard ? payWithSavedCard() : payWithNewCard());
    } catch (err) {
      setProblem(startProblem(err)?.message || SEND_UNKNOWN);
    } finally {
      setBusy(false);
    }
  }

  const option = (selected: boolean) =>
    `flex min-h-[52px] cursor-pointer items-center gap-3 rounded-xl px-3.5 ${selected ? 'border-2 border-accent' : 'border-[1.5px] border-line'}`;

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-5">
      <section aria-label="Card details" className={`${CARD} flex flex-col gap-3.5 p-5`}>
        {saved && (
          <fieldset className="m-0 flex flex-col gap-3.5 border-0 p-0">
            <legend className="sr-only">Which card?</legend>
            <label className={option(!useNewCard)}>
              <input type="radio" name="card" checked={!useNewCard} onChange={() => setUseNewCard(false)} className="size-5 accent-accent" />
              <span className="flex-1 font-semibold">{savedCardLabel(saved.last4).card}</span>
              <span className="text-sm text-ink-2">{savedCardLabel(saved.last4).note}</span>
            </label>
            <label className={option(useNewCard)}>
              <input type="radio" name="card" checked={useNewCard} onChange={() => setUseNewCard(true)} className="size-5 accent-accent" />
              <span className="flex-1 font-semibold">A different card</span>
            </label>
          </fieldset>
        )}
        {/* Kept mounted so switching back and forth doesn't lose what was typed. */}
        <div hidden={!!saved && !useNewCard}>
          <PaymentElement options={{ layout: 'tabs', wallets: { applePay: 'never', googlePay: 'never' } }} />
        </div>
      </section>
      {problem && (
        <p ref={alertRef} tabIndex={-1} role="alert" className="m-0 rounded-control bg-bad-soft px-3 py-2.5 text-[15px] font-semibold text-bad">
          {problem}
        </p>
      )}
      <button type="submit" className={PRIMARY} disabled={busy || !stripe}>
        {busy ? 'Sending to your bank…' : payLabel(started.amountCents)}
      </button>
    </form>
  );
}
