import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { api, ApiError, useSession } from '../../api/client';
import type { MyCover } from '../../api/types';
import { CoverTimeline } from '../../components/CoverTimeline';
import { Mark } from '../../components/Mark';
import { SignOutButton } from '../../components/SignOutButton';
import { Money } from '../../components/Money';
import { StatusPill } from '../../components/StatusPill';
import { Term } from '../../components/Term';
import { ThemeToggle } from '../../components/ThemeToggle';
import { cancelledCard } from '../../lib/cancelCopy';
import { formatDate } from '../../lib/dates';
import { coverDates, dueReasonText, periodLine, protectionSentence, refundOnItsWay } from '../../lib/myCoverCopy';
import { PAYMENTS_OFF, homeFailedCopy, homePaymentState, homeWaitingCopy, payLabel } from '../../lib/paymentCopy';

const CARD = 'rounded-2xl border border-line bg-card';

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((w) => w[0]!.toUpperCase())
    .join('');
}

function Header({ name }: { name?: string }) {
  return (
    <header className="flex items-center justify-between px-5 py-3.5">
      <span className="text-xl font-bold tracking-[-0.01em]">Plainly</span>
      {name && (
        <div className="flex items-center gap-2">
          <SignOutButton />
          <span
            role="img"
            aria-label={`Signed in as ${name}`}
            className="flex size-11 items-center justify-center rounded-full bg-track text-[15px] font-bold text-ink"
          >
            {initials(name)}
          </span>
        </div>
      )}
    </header>
  );
}

const LINK_BUTTON =
  'flex min-h-12 items-center justify-center rounded-xl border-[1.5px] border-line text-base font-semibold text-ink no-underline hover:bg-track';

const PAY_BUTTON =
  'flex min-h-[52px] items-center justify-center rounded-xl bg-accent text-[17px] font-bold text-on-accent no-underline';

/** The P1 pay button, or why there isn't one on a demo without Stripe keys. */
function PayAction({ c, label }: { c: MyCover; label: string }) {
  if (!c.paymentsEnabled) return <p className="m-0 text-sm leading-snug text-ink-2">{PAYMENTS_OFF}</p>;
  return (
    <Link to="/me/pay" className={PAY_BUTTON}>
      {label}
    </Link>
  );
}

/** The broker's own words for the latest change, shown as written (correct-effective-date AC13). */
function BrokerNote({ c }: { c: MyCover }) {
  if (!c.latestChangeReason) return null;
  return (
    <p className="m-0 text-[15px] leading-snug text-ink-2">
      {c.broker.name}'s note: “{c.latestChangeReason}”
    </p>
  );
}

/**
 * P1 for a cancelled policy (cancel-refund AC10): when cover ends and either the money going back to the card or what
 * is still owed for the days covered, with a way to pay it and a link to how it was worked out.
 */
function CancelledCard({ c }: { c: MyCover }) {
  const k = c.cancellation;
  const copy = k ? cancelledCard(k, c.amountDueCents, c.today) : null;
  return (
    <section
      aria-label="Policy cancelled"
      className={`flex flex-col gap-3 rounded-2xl bg-card p-5 ${copy?.due ? 'border-[1.5px] border-accent' : 'border border-line'}`}
    >
      <span className="self-start">
        <StatusPill tone="neutral">Cancelled</StatusPill>
      </span>
      <p className="m-0 text-[19px] font-medium leading-snug">{copy?.sentence ?? 'This cover has ended.'}</p>
      {copy?.due && (
        <>
          <p className="m-0 text-[17px] font-semibold leading-snug text-accent-ink tabular-nums">{copy.due}</p>
          {c.payment?.status === 'FAILED' && (
            <p className="m-0 text-[15px] leading-snug text-ink-2">
              Your last payment didn't go through, so no money moved. You can try again.
            </p>
          )}
          <PayAction c={c} label={payLabel(c.amountDueCents)} />
        </>
      )}
      {k && (
        <Link to="/me/cancellation" className={LINK_BUTTON}>
          See how we worked it out
        </Link>
      )}
    </section>
  );
}

function AmountCard({ c }: { c: MyCover }) {
  const state = homePaymentState({
    cancelled: c.policy.status === 'CANCELLED',
    amountDueCents: c.amountDueCents,
    payment: c.payment,
  });
  if (state === 'cancelled') return <CancelledCard c={c} />;
  if (c.refundWaiting && c.amountDueCents === 0) {
    return (
      <section aria-label="Refund on its way" className={`${CARD} flex flex-col gap-3 p-5`}>
        <span className="self-start">
          <StatusPill tone="wait" dot>
            Refund on its way
          </StatusPill>
        </span>
        <p className="m-0 text-[19px] font-medium leading-snug">{refundOnItsWay(c.refundWaiting)}</p>
        <p className="m-0 text-[15px] leading-snug text-ink-2">
          We fixed when your extra cover started, so you'd paid for days you weren't covered. Banks take 5–10 days
          to show a refund.
        </p>
        <BrokerNote c={c} />
      </section>
    );
  }
  if (state === 'nothing') {
    return (
      <section aria-label="Nothing due" className={`${CARD} flex items-center gap-4 p-5`}>
        <Mark size={56} />
        <div>
          <p className="m-0 text-[19px] font-semibold">You're all paid up.</p>
          <p className="mt-1 text-[15px] text-ink-2">Nothing to do right now.</p>
        </div>
      </section>
    );
  }
  if (state === 'waiting' && c.payment) {
    const copy = homeWaitingCopy(c.payment.amountCents);
    return (
      <section aria-label="Payment on its way" aria-live="polite" className={`${CARD} flex flex-col gap-3 p-5`}>
        <span className="self-start">
          <StatusPill tone="wait" dot>
            {copy.title}
          </StatusPill>
        </span>
        <p className="m-0 text-[19px] font-semibold leading-snug">{copy.headline}</p>
        <p className="m-0 text-[15px] leading-snug text-ink-2">{copy.body}</p>
      </section>
    );
  }
  if (state === 'failed' && c.payment) {
    const copy = homeFailedCopy(c.payment.amountCents);
    return (
      <section aria-label="Payment didn't go through" className="flex flex-col gap-3 rounded-2xl border-[1.5px] border-bad bg-card p-5">
        <span className="self-start">
          <StatusPill tone="bad" dot>
            {copy.title}
          </StatusPill>
        </span>
        <p className="m-0 text-[19px] font-semibold leading-snug">{copy.headline}</p>
        <p className="m-0 text-[15px] leading-snug text-ink-2">{copy.body}</p>
        <PayAction c={c} label="Try again" />
      </section>
    );
  }
  return (
    <section aria-label="Amount due" className="flex flex-col gap-3.5 rounded-2xl border-[1.5px] border-accent bg-card p-5">
      <div className="text-[40px] font-bold leading-none tracking-[-0.02em] text-accent-ink">
        <Money cents={c.amountDueCents} /> <span className="text-xl font-semibold">due</span>
      </div>
      <p className="m-0 text-[17px] leading-snug">{dueReasonText(c.dueReason, c.latestChangeFrom)}</p>
      {c.dueReason === 'CORRECTION' && <BrokerNote c={c} />}
      <PayAction c={c} label={payLabel(c.amountDueCents)} />
    </section>
  );
}

/** P1: the policyholder's cover — what they owe and why, what they're protected for, and who to ask. */
export function HomePage() {
  const session = useSession();
  const cover = useQuery({
    queryKey: ['my-cover'],
    queryFn: () => api<MyCover>('/api/me/cover'),
    retry: (n, e) => !(e instanceof ApiError && e.status === 404) && n < 1,
  });

  const shell = (body: ReactNode) => (
    <div className="flex min-h-dvh justify-center bg-bg">
      <div className="flex w-full max-w-[480px] flex-col">
        <Header name={session.data?.displayName} />
        <main className="flex flex-col gap-[18px] px-5 pb-8 pt-1">{body}</main>
      </div>
    </div>
  );

  if (cover.isPending) return shell(<p className="text-[15px] text-ink-2">Loading your cover…</p>);
  if (cover.isError) {
    const none = cover.error instanceof ApiError && cover.error.status === 404;
    return shell(
      <p className="text-[17px] leading-snug">
        {none ? "You don't have a policy yet. Your broker will set one up for you." : "We couldn't load your cover. Reload to try again."}
      </p>,
    );
  }

  const c = cover.data;
  const hasExtra = c.covers.some((x) => x.kind !== 'BASE');
  const base = c.covers.filter((x) => x.kind === 'BASE');
  const extras = c.covers.filter((x) => x.kind !== 'BASE');
  // cancel-refund AC10: on a cancelled policy the protection line, cover period and covers all stop on the cancel day.
  const endsOn = c.policy.status === 'CANCELLED' ? (c.cancellation?.endsOn ?? null) : null;
  const periodEnd = endsOn ?? c.policy.end;

  return shell(
    <>
      <div>
        <p className="m-0 text-[15px] text-ink-2">{c.business.legalName}</p>
        <h1 className="mt-0.5 mb-0 text-[30px] font-bold tracking-[-0.015em]">Your cover</h1>
      </div>

      <AmountCard c={c} />

      <section aria-label="What you're covered for" className={`${CARD} flex flex-col gap-3.5 p-5 pb-4`}>
        <p className="m-0 text-[17px] leading-[1.45]">{protectionSentence(c.business.description, hasExtra, endsOn ? { endsOn, today: c.today } : null)}</p>
        <div className="flex flex-wrap justify-between gap-x-3 gap-y-1 text-sm text-ink-2">
          <Term explain={`The dates your policy runs, here ${formatDate(c.policy.start)} to ${formatDate(periodEnd)}.`}>
            Your cover period
          </Term>
          <span className="font-semibold text-ink tabular-nums">{periodLine(c.policy.start, periodEnd)}</span>
        </div>

        <CoverTimeline
          variant="compact"
          start={c.policy.start}
          end={c.policy.end}
          today={c.today}
          cancelledFrom={endsOn ?? undefined}
          covers={[...base, ...extras].map((x) => ({ id: x.id, kind: x.kind, label: x.label, from: x.from, to: x.to }))} />

        <ul className="m-0 flex list-none flex-col gap-2 p-0 text-[15px]">
          {base.map((x) => (
            <li key={x.id} className="flex items-center gap-2.5">
              <span aria-hidden="true" className="size-2.5 flex-none rounded-[3px] bg-ink/85" />
              <span className="flex-1">
                <Term explain="Pays if someone is hurt or their property is damaged because of your business.">Base cover</Term>
              </span>
              <span className="text-ink-2 tabular-nums">{coverDates(x.from, endsOn)}</span>
            </li>
          ))}
          {extras.map((x) => (
            <li key={x.id} className="flex items-center gap-2.5">
              <span aria-hidden="true" className="size-2.5 flex-none rounded-[3px] bg-accent" />
              <span className="flex-1">
                <Term explain="Cover added on top of your base cover. This one pays if something you sell hurts someone.">
                  Extra cover
                </Term>
                : products you sell
              </span>
              <span className="text-ink-2 tabular-nums">{coverDates(x.from, endsOn)}</span>
            </li>
          ))}
        </ul>
      </section>

      <section aria-label="Your broker" className={`${CARD} flex flex-col px-[18px] py-3.5`}>
        <span className="font-semibold">Your broker, {c.broker.name}</span>
        <span className="text-sm text-ink-2">Questions about your cover? Your broker set up this policy and can help.</span>
      </section>

      <div className="flex items-center justify-between gap-3 pt-2 text-[13px] text-ink-2">
        <span className="tabular-nums">Policy {c.policy.policyNumber}</span>
        <ThemeToggle />
      </div>
    </>,
  );
}
