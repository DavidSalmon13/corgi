import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { HeaderEnd, usePolicyId } from './MeNav';
import { meApiPath, mePolicyPath, myCoverKey } from '../../lib/mePaths';
import { Link, Navigate } from 'react-router';
import { api, ApiError } from '../../api/client';
import type { Cancellation, MyCover } from '../../api/types';
import { Glossed } from '../../components/Glossed';
import { StatusPill } from '../../components/StatusPill';
import {
  cancellationTitle,
  cancelledCard,
  refundPill,
  unusedDaysCaption,
  workedOutRows,
  type MoneyRow,
} from '../../lib/cancelCopy';
import { formatDate, formatShortDate } from '../../lib/dates';
import { payLabel, PAYMENTS_OFF } from '../../lib/paymentCopy';
import { pct, positionPct, spanPct } from '../../lib/timeline';

const CARD = 'rounded-2xl border border-line bg-card';
const HATCH = { backgroundImage: 'repeating-linear-gradient(135deg, var(--pl-line) 0 2px, transparent 2px 7px)' };
/** The torn top edge of the "How we worked it out" receipt. */
const PERFORATION = {
  backgroundImage: 'radial-gradient(circle at 7px 0, var(--pl-bg) 5px, transparent 5.5px)',
  backgroundSize: '14px 9px',
  backgroundRepeat: 'repeat-x',
};

function Shell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh justify-center bg-bg">
      <div className="flex w-full max-w-[480px] flex-col">
        <header className="flex items-center justify-between pl-2 pr-5 pt-2">
          <Link to={mePolicyPath(usePolicyId())} className="flex min-h-11 items-center gap-1 px-2 text-base font-semibold text-accent-ink no-underline hover:text-ink">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M15 18l-6-6 6-6" />
            </svg>
            Your coverage
          </Link>
          <HeaderEnd />
        </header>
        <main className="flex flex-col gap-5 px-5 pb-8 pt-3">{children}</main>
      </div>
    </div>
  );
}

/** Days used against the striped unused days, one bar per cover, from the policy start to its original end. */
function DaysBar({ c, start, end, covers }: { c: Cancellation; start: string; end: string; covers: MyCover['covers'] }) {
  const cut = positionPct(start, end, c.endsOn);
  const label = (left: number) => ({ left: pct(left) });
  return (
    <section aria-label="Days used" className={`${CARD} p-4`}>
      <div aria-hidden="true" className="relative mb-2 h-[18px] text-[13px] font-semibold tabular-nums">
        <span className="absolute left-0">{formatShortDate(start)}</span>
        {cut > 14 && cut < 72 && (
          <span className="absolute -translate-x-1/2" style={label(cut)}>
            {formatShortDate(c.endsOn)}
          </span>
        )}
        <span className="absolute right-0">{formatDate(end)}</span>
      </div>
      <div aria-hidden="true" className="flex flex-col gap-1.5">
        {covers.map((x) => {
          const to = x.to < c.endsOn ? x.to : c.endsOn;
          const s = x.from < to ? spanPct(start, end, x.from, to) : null;
          return (
            <div key={x.id} className="relative h-3.5 overflow-hidden rounded-[7px] bg-track">
              {s && (
                <div
                  className={`absolute inset-y-0 ${x.kind === 'BASE' ? 'bg-cover-base' : 'bg-accent'}`}
                  style={{ left: pct(s.left), width: pct(s.width) }}
                />
              )}
              <div className="absolute inset-y-0 right-0" style={{ left: pct(cut), ...HATCH }} />
            </div>
          );
        })}
      </div>
      <p className="mt-2.5 mb-0 text-sm leading-snug text-ink-2"><Glossed>{unusedDaysCaption(c)}</Glossed></p>
    </section>
  );
}

/** P6 "How we worked it out": a receipt of each return premium, what was kept back, and the bottom line. */
function WorkedOut({ rows }: { rows: MoneyRow[] }) {
  return (
    <section aria-label="How we worked it out" className="overflow-hidden rounded-b-2xl border border-t-0 border-line bg-card">
      <div aria-hidden="true" className="h-[9px]" style={PERFORATION} />
      <h2 className="m-0 px-[18px] pt-2.5 pb-1 text-[13px] font-semibold uppercase tracking-[0.06em] text-ink-2">
        How we worked it out
      </h2>
      {rows.map((r, i) =>
        r.total ? (
          <div key={i} className="flex items-baseline justify-between gap-3 border-t-[1.5px] border-ink px-[18px] py-4">
            <div>
              <div className="text-[17px] font-bold">
                <Glossed>{r.label}</Glossed>
              </div>
              <div className="mt-0.5 text-[13px] text-ink-2">
                <Glossed>{r.detail}</Glossed>
              </div>
            </div>
            <span className="text-[22px] font-bold text-accent-ink tabular-nums">{r.amount}</span>
          </div>
        ) : (
          <div key={i} className={`flex gap-3 px-[18px] py-3.5 ${i > 0 ? 'border-t border-dashed border-line' : ''}`}>
            <div className="flex-1">
              <div className="font-semibold">
                <Glossed>{r.label}</Glossed>
              </div>
              <div className="mt-0.5 text-[13px] text-ink-2 tabular-nums">
                <Glossed>{r.detail}</Glossed>
              </div>
            </div>
            <span className="font-semibold tabular-nums">{r.amount}</span>
          </div>
        ),
      )}
    </section>
  );
}

/** P6 (/me/policies/:policyId/cancellation): when cover ends, the days used and unused, and how the money back was worked out. */
export function CancellationPage() {
  const policyId = usePolicyId();
  const cover = useQuery({
    queryKey: myCoverKey(policyId),
    queryFn: () => api<MyCover>(meApiPath(policyId, 'cover')),
    retry: (n, e) => !(e instanceof ApiError && e.status === 404) && n < 1,
  });

  if (cover.isPending) return <Shell><p className="text-[15px] text-ink-2">Loading…</p></Shell>;
  if (cover.isError) {
    if (cover.error instanceof ApiError && cover.error.status === 404) return <Navigate to={mePolicyPath(policyId)} replace />;
    return <Shell><p className="text-[17px] leading-snug">We couldn't load your coverage. Reload to try again.</p></Shell>;
  }

  const c = cover.data;
  const k = c.cancellation;
  // cancel-refund AC11: only a cancelled policy has this page.
  if (c.policy.status !== 'CANCELLED' || !k) return <Navigate to={mePolicyPath(policyId)} replace />;

  const summary = cancelledCard(k, c.amountDueCents, c.today);
  const pill = c.amountDueCents > 0 ? null : refundPill(k);
  const covers = [...c.covers.filter((x) => x.kind === 'BASE'), ...c.covers.filter((x) => x.kind !== 'BASE')];

  return (
    <Shell>
      <div>
        <h1 className="m-0 text-[30px] font-bold tracking-[-0.015em]">{cancellationTitle(k, c.today)}</h1>
        <p className="mt-0.5 mb-0 text-[15px] text-ink-2">
          {c.business.legalName} · Policy {c.policy.policyNumber}
        </p>
      </div>

      <section aria-label="Summary" className={`${CARD} flex flex-col gap-3 p-5`}>
        <p className="m-0 text-[19px] font-medium leading-snug">{summary.sentence}</p>
        {pill && (
          <>
            <span className="self-start">
              <StatusPill tone={pill.tone} dot>
                {pill.label}
              </StatusPill>
            </span>
            <span className="text-sm leading-snug text-ink-2">{pill.sentence}</span>
          </>
        )}
        {summary.due && (
          <>
            <p className="m-0 text-[17px] font-semibold leading-snug text-accent-ink tabular-nums">{summary.due}</p>
            {c.paymentsEnabled ? (
              <Link
                to={mePolicyPath(policyId, 'pay')}
                className="flex min-h-[52px] items-center justify-center rounded-xl bg-accent text-[17px] font-bold text-on-accent no-underline"
              >
                {payLabel(c.amountDueCents)}
              </Link>
            ) : (
              <p className="m-0 text-sm leading-snug text-ink-2">{PAYMENTS_OFF}</p>
            )}
          </>
        )}
        {k.reason && (
          <p className="m-0 text-[15px] leading-snug text-ink-2">
            {c.broker.name}'s note: “{k.reason}”
          </p>
        )}
      </section>

      <DaysBar c={k} start={c.policy.start} end={c.policy.end} covers={covers} />

      <WorkedOut rows={workedOutRows(k, c.amountDueCents)} />

      <p className="m-0 text-sm leading-snug text-ink-2">
        Questions? Your broker, {c.broker.name}, can help.
      </p>
    </Shell>
  );
}
