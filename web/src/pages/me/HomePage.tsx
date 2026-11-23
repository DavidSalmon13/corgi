import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { api, ApiError, useSession } from '../../api/client';
import type { MyCover } from '../../api/types';
import { CoverTimeline } from '../../components/CoverTimeline';
import { Glossed } from '../../components/Glossed';
import { Mark } from '../../components/Mark';
import { SignOutButton } from '../../components/SignOutButton';
import { Money } from '../../components/Money';
import { StatusPill } from '../../components/StatusPill';
import { Term } from '../../components/Term';
import { ThemeToggle } from '../../components/ThemeToggle';
import { cancelledCard } from '../../lib/cancelCopy';
import { formatDate } from '../../lib/dates';
import { AllPoliciesLink, usePolicyId } from './MeNav';
import { GLOSSARY } from '../../lib/glossary';
import { coverRemovedSentence, refundAfterRemoval, removedOn } from '../../lib/removeCoverCopy';
import { coverDates, dueReasonText, periodLine, protectionSentence, refundOnItsWay } from '../../lib/myCoverCopy';
import { meApiPath, mePolicyPath, myCoverKey } from '../../lib/mePaths';
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

/** The policyholder's header: the Plainly name, sign out and their initials. Shared with "Your policies". */
export function MeHeader({ name }: { name?: string }) {
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

const NAV_ROW =
  'flex min-h-14 items-center justify-between gap-3 px-[18px] py-2.5 font-semibold text-ink no-underline hover:bg-track hover:text-ink';

function Chevron() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className="flex-none">
      <path d="M9 18l6-6-6-6" />
    </svg>
  );
}

/** "See why": into the statement, where the change behind the amount is laid out line by line. */
function SeeWhy({ label = 'See why' }: { label?: string }) {
  return (
    <Link to={mePolicyPath(usePolicyId(), 'statement')} className="font-semibold">
      {label}
    </Link>
  );
}

const PAY_BUTTON =
  'flex min-h-[52px] items-center justify-center rounded-xl bg-accent text-[17px] font-bold text-on-accent no-underline';

/** The P1 pay button, or why there isn't one on a demo without Stripe keys. */
function PayAction({ c, label }: { c: MyCover; label: string }) {
  const policyId = usePolicyId();
  if (!c.paymentsEnabled) return <p className="m-0 text-sm leading-snug text-ink-2">{PAYMENTS_OFF}</p>;
  return (
    <Link to={mePolicyPath(policyId, 'pay')} className={PAY_BUTTON}>
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
  const policyId = usePolicyId();
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
      <p className="m-0 text-[19px] font-medium leading-snug">{copy?.sentence ?? 'This coverage has ended.'}</p>
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
        <Link to={mePolicyPath(policyId, 'cancellation')} className={LINK_BUTTON}>
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
  // remove-cover AC7: after a removal the refund is the removed days' return premium, not a corrected date.
  const removal = removedOn(c.covers);
  if (c.refundWaiting && c.amountDueCents === 0) {
    return (
      <section aria-label="Refund on its way" className={`${CARD} flex flex-col gap-3 p-5`}>
        <span className="self-start">
          <StatusPill tone="wait" dot>
            Refund on its way
          </StatusPill>
        </span>
        <p className="m-0 text-[19px] font-medium leading-snug">{refundOnItsWay(c.refundWaiting)}</p>
        {removal ? (
          <p className="m-0 text-[15px] leading-snug text-ink-2">
            <Glossed>{refundAfterRemoval(removal, c.today)}</Glossed> <SeeWhy />
          </p>
        ) : (
          <p className="m-0 text-[15px] leading-snug text-ink-2">
            We corrected the <Term explain={GLOSSARY.effectiveDate}>effective date</Term> of your{' '}
            <Term explain={GLOSSARY.productLiability}>product liability</Term>{' '}
            <Term explain={GLOSSARY.endorsement}>endorsement</Term>, so you'd paid for days you weren't covered. Banks
            take 5–10 days to show a refund. <SeeWhy />
          </p>
        )}
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
        <p className="m-0 text-[15px] leading-snug text-ink-2">
          {copy.body} <SeeWhy label="See statement" />
        </p>
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
      <p className="m-0 text-[17px] leading-snug">
        <Glossed>{dueReasonText(c.dueReason, c.latestChangeFrom)}</Glossed> <SeeWhy />
      </p>
      {(c.dueReason === 'CORRECTION' || c.dueReason === 'COVER_REMOVED') && <BrokerNote c={c} />}
      <PayAction c={c} label={payLabel(c.amountDueCents)} />
    </section>
  );
}

/** P1: the policyholder's cover — what they owe and why, what they're protected for, and who to ask. */
export function HomePage() {
  const session = useSession();
  const policyId = usePolicyId();
  const cover = useQuery({
    queryKey: myCoverKey(policyId),
    queryFn: () => api<MyCover>(meApiPath(policyId, 'cover')),
    retry: (n, e) => !(e instanceof ApiError && e.status === 404) && n < 1,
  });

  const shell = (body: ReactNode) => (
    <div className="flex min-h-dvh justify-center bg-bg">
      <div className="flex w-full max-w-[480px] flex-col">
        <MeHeader name={session.data?.displayName} />
        <main className="flex flex-col gap-[18px] px-5 pb-8 pt-1">{body}</main>
      </div>
    </div>
  );

  if (cover.isPending) return shell(<p className="text-[15px] text-ink-2">Loading your cover…</p>);
  if (cover.isError) {
    const none = cover.error instanceof ApiError && cover.error.status === 404;
    return shell(
      <p className="text-[17px] leading-snug">
        {none ? "We couldn't find that policy." : "We couldn't load your cover. Reload to try again."}{' '}
        {none && (
          <Link to="/me" className="font-semibold">
            See your policies
          </Link>
        )}
      </p>,
    );
  }

  const c = cover.data;
  // remove-cover AC7: once removed product liability has ended, the business is no longer covered for what it sells.
  const hasExtra = c.covers.some((x) => x.kind !== 'BASE' && !(x.endedOn && x.endedOn <= c.today));
  const removal = c.policy.status === 'CANCELLED' ? null : removedOn(c.covers);
  const base = c.covers.filter((x) => x.kind === 'BASE');
  const extras = c.covers.filter((x) => x.kind !== 'BASE');
  // cancel-refund AC10: on a cancelled policy the protection line, cover period and covers all stop on the cancel day.
  const endsOn = c.policy.status === 'CANCELLED' ? (c.cancellation?.endsOn ?? null) : null;
  const periodEnd = endsOn ?? c.policy.end;

  return shell(
    <>
      <div>
        <AllPoliciesLink />
        <p className="m-0 text-[15px] text-ink-2">{c.business.legalName}</p>
        <h1 className="mt-0.5 mb-0 text-[30px] font-bold tracking-[-0.015em]">Your coverage</h1>
      </div>

      <AmountCard c={c} />

      <section aria-label="What you're covered for" className={`${CARD} flex flex-col gap-3.5 overflow-hidden p-5 pb-0`}>
        <p className="m-0 text-[17px] leading-[1.45]">
          <Glossed>{protectionSentence(c.business.description, hasExtra, endsOn ? { endsOn, today: c.today, startsOn: c.policy.start } : null)}</Glossed>
        </p>
        {removal && (
          <p className="m-0 text-[15px] leading-snug text-ink-2">
            <Glossed>{coverRemovedSentence(removal, c.today)}</Glossed>
          </p>
        )}
        <div className="flex flex-wrap justify-between gap-x-3 gap-y-1 text-sm text-ink-2">
          <Term explain={`${GLOSSARY.policyPeriod} Yours is ${formatDate(c.policy.start)} to ${formatDate(periodEnd)}.`}>
            Your policy period
          </Term>
          <span className="font-semibold text-ink tabular-nums">{periodLine(c.policy.start, periodEnd)}</span>
        </div>

        {/* cover-timeline AC1: tapping the bars opens P2. Keyboard users get the "See your cover timeline" row below. */}
        <Link to={mePolicyPath(policyId, 'timeline')} tabIndex={-1} className="block text-ink no-underline">
          <CoverTimeline
            variant="compact"
            start={c.policy.start}
            end={c.policy.end}
            today={c.today}
            cancelledFrom={endsOn ?? undefined}
            covers={[...base, ...extras].map((x) => ({
              id: x.id,
              kind: x.kind,
              label: x.label,
              from: x.from,
              to: x.to,
              endedOn: x.endedOn ?? undefined,
            }))}
          />
        </Link>

        <ul className="m-0 flex list-none flex-col gap-2 p-0 text-[15px]">
          {base.map((x) => (
            <li key={x.id} className="flex items-center gap-2.5">
              <span aria-hidden="true" className="size-2.5 flex-none rounded-[3px] bg-ink/85" />
              <span className="flex-1">
                <Term explain={GLOSSARY.generalLiability}>General liability</Term>
              </span>
              <span className="text-ink-2 tabular-nums">{coverDates(x.from, endsOn)}</span>
            </li>
          ))}
          {extras.map((x) => (
            <li key={x.id} className="flex items-center gap-2.5">
              <span aria-hidden="true" className="size-2.5 flex-none rounded-[3px] bg-accent" />
              <span className="flex-1">
                <Term explain={GLOSSARY.productLiability}>Product liability</Term>
              </span>
              {/* remove-cover AC7: a removed cover ends on its own day, unless the policy was cancelled before it. */}
              <span className="text-ink-2 tabular-nums">
                {coverDates(x.from, x.endedOn && (!endsOn || x.endedOn < endsOn) ? x.endedOn : endsOn)}
              </span>
            </li>
          ))}
        </ul>

        <Link to={mePolicyPath(policyId, 'timeline')} className={`${NAV_ROW} -mx-5 border-t border-line`}>
          See your coverage timeline
          <Chevron />
        </Link>
      </section>

      <nav aria-label="More" className={`${CARD} overflow-hidden`}>
        <Link to={mePolicyPath(policyId, 'statement')} className={NAV_ROW}>
          <span className="flex flex-col">
            <span>Statement</span>
            <span className="text-sm font-normal text-ink-2">Every charge and payment, and how we got each one</span>
          </span>
          <Chevron />
        </Link>
        <div className="flex flex-col border-t border-line px-[18px] py-3.5">
          <span className="font-semibold">Your broker, {c.broker.name}</span>
          <span className="text-sm text-ink-2">Questions about your coverage? Your broker set up this policy and can help.</span>
        </div>
      </nav>

      <div className="flex items-center justify-between gap-3 pt-2 text-[13px] text-ink-2">
        <span className="tabular-nums">Policy {c.policy.policyNumber}</span>
        <ThemeToggle />
      </div>
    </>,
  );
}
