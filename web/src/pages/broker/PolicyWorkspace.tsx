import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { api, ApiError } from '../../api/client';
import type { CoverKind, PolicyDetail, PolicyLink, RefundRetried, RefundView } from '../../api/types';
import { CoverTimeline } from '../../components/CoverTimeline';
import { LedgerTable } from '../../components/LedgerTable';
import { Money } from '../../components/Money';
import { StatusPill } from '../../components/StatusPill';
import { Glossed } from '../../components/Glossed';
import { Term } from '../../components/Term';
import { GLOSSARY } from '../../lib/glossary';
import { coverWasLabel, failedRefundsBanner } from '../../lib/correctCopy';
import { removedBarLabel } from '../../lib/removeCoverCopy';
import { balanceNote, balanceText, policyPeriod, policyStatus } from '../../lib/policyCopy';
import { ownerAccountLine } from '../../lib/accountCopy';
import { canRewrite, NEW_POLICY_FOR_CUSTOMER, REWRITE_POLICY } from '../../lib/policiesCopy';
import { newPolicyPath } from '../../lib/mePaths';
import { formatCents } from '../../lib/money';
import type { ChangeSavedState } from './ChangePolicyPage';

const COVER_NAME: Record<CoverKind, string> = {
  BASE: 'General liability',
  PRODUCT_LIABILITY: 'Product liability',
};

const CARD = 'rounded-2xl border border-line bg-card px-4 py-5 md:px-6';
const ACTION =
  'inline-flex min-h-11 items-center justify-center rounded-control border-[1.5px] border-line px-4 text-[15px] font-semibold text-ink no-underline hover:border-ink-2';
const ACTION_MAIN =
  'inline-flex min-h-11 items-center justify-center rounded-control bg-accent px-5 text-[15px] font-bold text-on-accent no-underline';

function BackLink() {
  return (
    <Link to="/broker/policies" className="text-[15px] font-semibold no-underline">
      ‹ All policies
    </Link>
  );
}

/**
 * more-policies AC4: "Replaces GL-…" on a rewrite and "Replaced by GL-…" on the cancelled policy it replaced, each a
 * link to the other policy so the broker can follow the correction both ways.
 */
function RewriteLinks({ replaces, replacedBy }: { replaces?: PolicyLink | null; replacedBy?: PolicyLink | null }) {
  if (!replaces && !replacedBy) return null;
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-[15px] text-ink-2">
      {replaces && (
        <span className="tabular-nums">
          <Term explain={GLOSSARY.rewrite}>Rewrite</Term> · replaces{' '}
          <Link to={`/broker/policies/${replaces.policyId}`} className="font-semibold">
            {replaces.policyNumber}
          </Link>
        </span>
      )}
      {replacedBy && (
        <span className="tabular-nums">
          Replaced by{' '}
          <Link to={`/broker/policies/${replacedBy.policyId}`} className="font-semibold">
            {replacedBy.policyNumber}
          </Link>{' '}
          (<Term explain={GLOSSARY.rewrite}>rewrite</Term>)
        </span>
      )}
    </div>
  );
}

/**
 * The "Endorsement added. Maya now owes …" note B4 hands over in router state, with the ids the save created. It is
 * read once and the history entry is replaced without it, so a reload or Back doesn't announce the change twice.
 */
function useSavedBanner(): [ChangeSavedState | null, () => void] {
  const location = useLocation();
  const navigate = useNavigate();
  const [banner, setBanner] = useState<ChangeSavedState | null>(() => {
    const state = location.state as ChangeSavedState | null;
    return state?.banner ? state : null;
  });
  useEffect(() => {
    if ((location.state as ChangeSavedState | null)?.banner) {
      navigate(location.pathname, { replace: true, state: null });
    }
  }, [location.state, location.pathname, navigate]);
  return [banner, () => setBanner(null)];
}

function SavedBanner({ saved, onClose }: { saved: ChangeSavedState; onClose: () => void }) {
  return (
    <div
      role="status"
      className="flex items-center justify-between gap-3 rounded-card border border-good/30 bg-good-soft py-1 pl-4 pr-1 text-[15px] font-semibold text-good"
    >
      <span className="flex flex-col gap-0.5 py-2">
        <span>
          <Glossed>{saved.banner}</Glossed>
        </span>
        {saved.refs && (
          <span className="text-sm font-normal tabular-nums">
            <Glossed>{saved.refs}</Glossed>
          </span>
        )}
      </span>
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

/**
 * Every refund Stripe couldn't send, in one banner: a cancellation can split its refund over several payments, and
 * one alert per payment buried the policy. Retrying sends a new attempt for each (its own idempotency key, so a
 * double click can't send twice). Only a broker sees B3, and the API checks that too.
 */
function FailedRefunds({ refunds, p }: { refunds: RefundView[]; p: PolicyDetail }) {
  const queryClient = useQueryClient();
  const retry = useMutation({
    mutationFn: () =>
      Promise.all(refunds.map((r) => api<RefundRetried>(`/api/broker/refunds/${r.id}/retry`, { method: 'POST' }))),
    onSettled: () => queryClient.invalidateQueries({ queryKey: ['policy', String(p.policyId)] }),
  });
  const copy = failedRefundsBanner(refunds, p.transactions, p.customer.ownerName);
  const reasons = [...new Set(refunds.map((r) => r.failureReason).filter(Boolean))];
  return (
    <section
      role="alert"
      aria-label={refunds.length === 1 ? `Refund ${refunds[0].displayId} didn't go through` : "Refunds didn't go through"}
      className="flex flex-col gap-2.5 rounded-card bg-bad-soft px-4 py-3.5 text-bad"
    >
      <strong className="text-base">{copy.heading}</strong>
      <span className="text-sm leading-normal text-ink">
        {copy.detail}
        {reasons.length === 1 && <span className="block text-ink-2">Stripe's reason: {reasons[0]}</span>}
      </span>
      <ul className="m-0 flex list-none flex-col gap-1 p-0 text-[13px] text-ink-2">
        {refunds.map((r) => (
          <li key={r.id} className="tabular-nums">
            <span className="font-mono">{r.displayId}</span> · {formatCents(r.amountCents)}
            {r.cardLast4 ? ` to card ending ${r.cardLast4}` : ''} · attempt {r.attempt}
            {reasons.length > 1 && r.failureReason ? ` · ${r.failureReason}` : ''}
          </li>
        ))}
      </ul>
      {retry.isError && (
        <span className="text-sm font-semibold">We couldn't reach the server to retry. Try again in a moment.</span>
      )}
      <div className="flex flex-wrap gap-2">
        <button type="button" onClick={() => retry.mutate()} disabled={retry.isPending} className={`${ACTION_MAIN} disabled:opacity-60`}>
          {retry.isPending ? 'Sending…' : copy.retryLabel}
        </button>
        <a href={`mailto:${p.customer.ownerEmail}`} className={`${ACTION} bg-card`}>
          Email {p.customer.ownerName.trim().split(/\s+/)[0] || 'the customer'}
        </a>
      </div>
    </section>
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
  const extra = p.coverages.find((c) => c.kind === 'PRODUCT_LIABILITY');
  const removed = !!extra?.endedOn;
  // add-coverage AC1: one extra cover per policy, and none on a cancelled policy.
  const canAddCover = p.status === 'ACTIVE' && !extra;
  // correct-effective-date AC1: an active policy with extra cover can correct it (B4 explains if it's blocked).
  // remove-cover AC5: not once it has been removed.
  const canCorrect = p.status === 'ACTIVE' && !!extra && !removed;
  // remove-cover AC1: live extra cover can be removed (B4 explains if a payment is still with the bank).
  const canRemove = canCorrect;
  // cancel-refund AC1: an active policy can be cancelled (B4 explains if a payment is still with the bank).
  const canCancel = p.status === 'ACTIVE';
  // more-policies AC2: a cancelled policy nobody has replaced yet is rewritten from here, as its main action.
  const rewritable = canRewrite(p);
  const failedRefunds = (p.refunds ?? []).filter((r) => r.status === 'FAILED');
  const refundPendingCents = (p.refunds ?? [])
    .filter((r) => r.status === 'PENDING')
    .reduce((sum, r) => sum + r.amountCents, 0);

  return (
    <>
      {banner && <SavedBanner saved={banner} onClose={closeBanner} />}
      {failedRefunds.length > 0 && <FailedRefunds refunds={failedRefunds} p={p} />}
      <section aria-label="Policy" className="flex flex-wrap items-end justify-between gap-5">
        <div className="flex min-w-0 flex-col gap-1.5">
          <BackLink />
          <h1 className="m-0 text-[28px] font-bold tracking-[-0.015em] md:text-[32px]">{p.customer.legalName}</h1>
          <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-[15px] text-ink-2">
            <span className="tabular-nums">
              {p.policyNumber} ·{' '}
              <Term explain={GLOSSARY.generalLiability}>General liability</Term>
            </span>
            <span>
              {p.customer.ownerName} ·{' '}
              <span className={p.customer.ownerSignedUpAt ? '' : 'font-semibold text-wait'}>
                {ownerAccountLine(p.customer.ownerName, p.customer.ownerSignedUpAt)}
              </span>
            </span>
            <span className="tabular-nums">
              <Term explain={GLOSSARY.policyPeriod}>Policy period</Term>{' '}
              {policyPeriod(p.start, p.end, cancelled ? p.cancelledOn : null)}
            </span>
            <StatusPill tone={status.tone}>{status.label}</StatusPill>
          </div>
          <RewriteLinks replaces={p.replaces} replacedBy={p.replacedBy} />
        </div>
        <div className="flex flex-wrap items-end gap-5">
          <div className="text-left md:text-right">
            <div className="text-[13px] text-ink-2">Balance</div>
            <div className={`text-[28px] font-bold tabular-nums ${p.balanceCents > 0 ? 'text-accent-ink' : 'text-ink'}`}>
              {balanceText(p.balanceCents)}
            </div>
            <div className="text-[13px] text-ink-2">{balanceNote(p.balanceCents, p.paymentWaiting, refundPendingCents)}</div>
          </div>
          <div className="flex flex-wrap gap-2">
              {rewritable && (
                <Link to={newPolicyPath(p.customerId, p.policyId)} className={ACTION_MAIN}>
                  {REWRITE_POLICY}
                </Link>
              )}
              {canAddCover && (
                <Link to={`/broker/policies/${p.policyId}/change`} className={ACTION}>
                  Add endorsement
                </Link>
              )}
              {canCorrect && (
                <Link to={`/broker/policies/${p.policyId}/change?tab=correct`} className={ACTION_MAIN}>
                  Correct
                </Link>
              )}
              {canRemove && (
                <Link to={`/broker/policies/${p.policyId}/change?tab=remove`} className={ACTION}>
                  Remove product liability
                </Link>
              )}
              {canCancel && (
                <Link to={`/broker/policies/${p.policyId}/change?tab=cancel`} className={ACTION}>
                  Cancel
                </Link>
              )}
              {/* more-policies AC1: every policy can start another one for the same customer. */}
              <Link to={newPolicyPath(p.customerId)} className={ACTION}>
                {NEW_POLICY_FOR_CUSTOMER}
              </Link>
            </div>
        </div>
      </section>

      <section aria-labelledby="timeline-h" className={CARD}>
        <div className="mb-2.5 flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="timeline-h" className="m-0 text-lg font-bold">
            Coverage timeline
          </h2>
          <Link to={`/broker/policies/${p.policyId}/history`} className="text-[15px] font-semibold">
            History ›
          </Link>
        </div>
        <CoverTimeline
          start={p.start}
          end={p.end}
          today={p.today}
          cancelledFrom={cancelled ? (p.cancelledOn ?? undefined) : undefined}
          covers={p.coverages.map((c) => ({
            id: c.id,
            kind: c.kind,
            label:
              c.kind === 'BASE' ? (
                <Term explain={GLOSSARY.generalLiability}>{COVER_NAME[c.kind]}</Term>
              ) : (
                <Term explain={GLOSSARY.productLiability}>{COVER_NAME[c.kind]}</Term>
              ),
            detail: (
              <>
                <Money cents={c.annualPremiumCents} /> <Term explain={GLOSSARY.annualPremium}>annual premium</Term>
              </>
            ),
            from: c.from,
            to: c.to,
            endedOn: c.endedOn ?? undefined,
            barLabel:
              c.kind !== 'PRODUCT_LIABILITY'
                ? undefined
                : c.endedOn
                  ? removedBarLabel(c.from, c.previousStart ?? null, c.endedOn)
                  : coverWasLabel(c.from, c.previousStart ?? null),
          }))}
        />
      </section>

      <section aria-labelledby="ledger-h" className={CARD}>
        <div className="mb-2 flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="ledger-h" className="m-0 text-lg font-bold">
            <Term explain={GLOSSARY.ledger}>Ledger</Term>
          </h2>
          <span className="text-sm text-ink-2">
            Every line, oldest first. Lines are never edited; corrections add a{' '}
            <Term explain={GLOSSARY.reversal}>reversal</Term> and a <Term explain={GLOSSARY.rebill}>rebill</Term>.
          </span>
        </div>
        <LedgerTable lines={p.ledger} transactions={p.transactions} />
      </section>
    </>
  );
}
