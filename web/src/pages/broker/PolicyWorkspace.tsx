import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { api, ApiError } from '../../api/client';
import type { CoverKind, PolicyDetail } from '../../api/types';
import { CoverTimeline } from '../../components/CoverTimeline';
import { LedgerTable } from '../../components/LedgerTable';
import { Money } from '../../components/Money';
import { StatusPill } from '../../components/StatusPill';
import { Term } from '../../components/Term';
import { formatDate } from '../../lib/dates';
import { balanceNote, balanceText, policyStatus } from '../../lib/policyCopy';
import { ownerAccountLine } from '../../lib/accountCopy';
import type { ChangeSavedState } from './ChangePolicyPage';

const COVER_NAME: Record<CoverKind, string> = {
  BASE: 'Base cover',
  PRODUCT_LIABILITY: 'Product liability',
};

const CARD = 'rounded-2xl border border-line bg-card px-4 py-5 md:px-6';

function BackLink() {
  return (
    <Link to="/broker/policies" className="text-[15px] font-semibold no-underline">
      ‹ All policies
    </Link>
  );
}

/**
 * The "Extra cover added. Maya now owes …" note B4 hands over in router state. It is read once and the
 * history entry is replaced without it, so a reload or Back doesn't announce the change a second time.
 */
function useSavedBanner(): [string | null, () => void] {
  const location = useLocation();
  const navigate = useNavigate();
  const [banner, setBanner] = useState<string | null>(
    () => (location.state as ChangeSavedState | null)?.banner ?? null,
  );
  useEffect(() => {
    if ((location.state as ChangeSavedState | null)?.banner) {
      navigate(location.pathname, { replace: true, state: null });
    }
  }, [location.state, location.pathname, navigate]);
  return [banner, () => setBanner(null)];
}

function SavedBanner({ text, onClose }: { text: string; onClose: () => void }) {
  return (
    <div
      role="status"
      className="flex items-center justify-between gap-3 rounded-card border border-good/30 bg-good-soft py-1 pl-4 pr-1 text-[15px] font-semibold text-good"
    >
      <span className="py-2">{text}</span>
      <button
        type="button"
        onClick={onClose}
        aria-label="Hide this message"
        className="inline-flex min-h-11 min-w-11 shrink-0 items-center justify-center rounded-control text-lg text-good hover:bg-good/10"
      >
        ×
      </button>
    </div>
  );
}

/** B3: one policy — who, what period, what is owed, the cover timeline, and every ledger line behind the balance. */
export function PolicyWorkspace() {
  const { id } = useParams();
  const [banner, closeBanner] = useSavedBanner();
  const policy = useQuery({
    queryKey: ['policy', id],
    queryFn: () => api<PolicyDetail>(`/api/broker/policies/${id}`),
    retry: (n, e) => !(e instanceof ApiError && e.status === 404) && n < 1,
  });

  if (policy.isPending) return <p className="text-[15px] text-ink-2">Loading the policy…</p>;
  if (policy.isError) {
    const missing = policy.error instanceof ApiError && policy.error.status === 404;
    return (
      <div className="flex flex-col gap-2">
        <BackLink />
        <p className="text-[15px] text-ink-2">
          {missing ? "We couldn't find that policy." : "We couldn't load the policy. Reload to try again."}
        </p>
      </div>
    );
  }

  const p = policy.data;
  const status = policyStatus(p.status, p.balanceCents, p.paymentWaiting);
  const cancelled = p.status === 'CANCELLED';
  // AC1: one extra cover per policy, and none on a cancelled policy.
  const canAddCover = p.status === 'ACTIVE' && !p.coverages.some((c) => c.kind === 'PRODUCT_LIABILITY');

  return (
    <>
      {banner && <SavedBanner text={banner} onClose={closeBanner} />}
      <section aria-label="Policy" className="flex flex-wrap items-end justify-between gap-5">
        <div className="flex min-w-0 flex-col gap-1.5">
          <BackLink />
          <h1 className="m-0 text-[28px] font-bold tracking-[-0.015em] md:text-[32px]">{p.customer.legalName}</h1>
          <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-[15px] text-ink-2">
            <span className="tabular-nums">
              {p.policyNumber} ·{' '}
              <Term explain="General Liability: pays if someone is hurt or their property is damaged because of the business.">
                General Liability
              </Term>
            </span>
            <span>
              {p.customer.ownerName} ·{' '}
              <span className={p.customer.ownerSignedUpAt ? '' : 'font-semibold text-wait'}>
                {ownerAccountLine(p.customer.ownerName, p.customer.ownerSignedUpAt)}
              </span>
            </span>
            <span className="tabular-nums">
              {formatDate(p.start)} → {formatDate(p.end)}
              {cancelled && ' (cancelled)'}
            </span>
            <StatusPill tone={status.tone}>{status.label}</StatusPill>
          </div>
        </div>
        <div className="flex flex-wrap items-end gap-5">
          <div className="text-left md:text-right">
            <div className="text-[13px] text-ink-2">Balance</div>
            <div className={`text-[28px] font-bold tabular-nums ${p.balanceCents > 0 ? 'text-accent-ink' : 'text-ink'}`}>
              {balanceText(p.balanceCents)}
            </div>
            <div className="text-[13px] text-ink-2">{balanceNote(p.balanceCents, p.paymentWaiting)}</div>
          </div>
          {canAddCover && (
            <Link
              to={`/broker/policies/${p.policyId}/change`}
              className="inline-flex min-h-11 items-center justify-center rounded-control border-[1.5px] border-line px-4 text-[15px] font-semibold text-ink no-underline hover:border-ink-2"
            >
              Add cover
            </Link>
          )}
        </div>
      </section>

      <section aria-labelledby="timeline-h" className={CARD}>
        <h2 id="timeline-h" className="m-0 mb-2.5 text-lg font-bold">
          Cover timeline
        </h2>
        <CoverTimeline
          start={p.start}
          end={p.end}
          today={p.today}
          covers={p.coverages.map((c) => ({
            id: c.id,
            kind: c.kind,
            label:
              c.kind === 'BASE' ? (
                <Term explain="General Liability: pays if someone is hurt or their property is damaged because of the business.">
                  {COVER_NAME[c.kind]}
                </Term>
              ) : (
                <Term explain="Pays if something the business sells hurts someone.">{COVER_NAME[c.kind]}</Term>
              ),
            detail: (
              <>
                <Money cents={c.annualPremiumCents} /> a year
              </>
            ),
            from: c.from,
            to: c.to,
          }))}
        />
      </section>

      <section aria-labelledby="ledger-h" className={CARD}>
        <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="ledger-h" className="m-0 text-lg font-bold">
            <Term explain="The money record for this policy: every charge, payment and refund, in order.">Ledger</Term>
          </h2>
          <span className="text-sm text-ink-2">
            Every line, oldest first. Lines are never edited; corrections add an undo and a new line.
          </span>
        </div>
        <LedgerTable lines={p.ledger} />
      </section>
    </>
  );
}
