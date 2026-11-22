import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { api, ApiError } from '../../api/client';
import type { History, HistoryAction } from '../../api/types';
import { Ref } from '../../components/LedgerTable';
import { formatDate } from '../../lib/dates';
import { actionDetail, actionMoney, actionStatus, actionTitle, actionTone, ledgerChips, type Tone } from '../../lib/historyCopy';

const DOT: Record<Tone, string> = {
  neutral: 'bg-ink-2',
  good: 'bg-good',
  bad: 'bg-bad',
  wait: 'bg-wait',
  accent: 'bg-accent',
};

/** A correction stands out in amber as in B5; a refund that failed needs the broker's action, so it is red. */
function border(a: HistoryAction, tone: Tone): string {
  if (a.status === 'REFUND_FAILED') return 'border-[1.5px] border-bad';
  if (tone === 'accent') return 'border-[1.5px] border-accent';
  return 'border border-line';
}

function moneyColour(a: HistoryAction): string {
  if (a.pending || a.amountCents === 0) return 'text-ink-2';
  if (a.changeType === 'CORRECT') return 'text-accent-ink';
  return 'text-ink';
}

/** One action on the rail: date and who on the left (above the card on a phone), the dot, then the card. */
function ActionItem({ a, policyId }: { a: HistoryAction; policyId: string }) {
  const tone = actionTone(a);
  const chips = ledgerChips(a);
  const status = actionStatus(a);
  return (
    <li className="grid grid-cols-[16px_minmax(0,1fr)] gap-x-3 md:grid-cols-[120px_20px_minmax(0,1fr)] md:gap-x-4">
      <div className="col-start-2 row-start-1 pt-3 md:col-start-1 md:py-[18px] md:text-right">
        <div className="text-[15px] font-semibold tabular-nums">{formatDate(a.date)}</div>
        {a.actorName && <div className="text-[13px] text-ink-2">{a.actorName}</div>}
      </div>

      <div aria-hidden="true" className="relative col-start-1 row-span-2 row-start-1 flex justify-center md:col-start-2 md:row-span-1">
        <div className="absolute inset-y-0 w-0.5 bg-line" />
        <div className={`relative mt-[18px] size-3 rounded-full shadow-[0_0_0_3px_var(--color-bg)] md:mt-[22px] ${DOT[tone]}`} />
      </div>

      <div className="col-start-2 row-start-2 pt-2 pb-3 md:col-start-3 md:row-start-1 md:py-3">
        <article
          aria-label={[formatDate(a.date), actionTitle(a), status].filter(Boolean).join(', ')}
          className={`flex flex-col gap-2 rounded-[14px] bg-card px-4 py-4 md:px-[18px] ${border(a, tone)}`}
        >
          <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
            <h2 className={`m-0 text-[17px] font-bold ${a.status === 'REFUND_FAILED' ? 'text-bad' : ''}`}>{actionTitle(a)}</h2>
            <span className={`text-[17px] font-bold tabular-nums whitespace-nowrap ${moneyColour(a)}`}>{actionMoney(a)}</span>
          </div>

          <p className="m-0 break-words text-[15px] leading-[1.45] text-ink-2 tabular-nums">{actionDetail(a)}</p>

          {a.reason && (
            <blockquote className="m-0 rounded-[10px] bg-bg px-3 py-2.5 text-[15px] leading-[1.45]">
              <span className="text-xs font-semibold uppercase tracking-[0.06em] text-ink-2">Reason</span>
              <br />“{a.reason}”
            </blockquote>
          )}

          {a.status === 'REFUND_FAILED' && (
            <Link to={`/broker/policies/${policyId}`} className="self-start text-[15px] font-semibold">
              Retry the refund on the policy page ›
            </Link>
          )}

          {chips.length > 0 && (
            <div className="flex flex-wrap items-center gap-1.5 text-[13px] text-ink-2">
              <span>Ledger lines</span>
              {chips.map((c) =>
                c.undone ? (
                  <s key={c.id} aria-label={`${c.id}, undone later`} className="decoration-[1.5px]">
                    <Ref>{c.id}</Ref>
                  </s>
                ) : (
                  <Ref key={c.id}>{c.id}</Ref>
                ),
              )}
            </div>
          )}
        </article>
      </div>
    </li>
  );
}

/** B5: every action on one policy, oldest first, with what it did to the money and every ID behind it. */
export function HistoryPage() {
  const { id = '' } = useParams();
  const history = useQuery({
    queryKey: ['policy-history', id],
    queryFn: () => api<History>(`/api/broker/policies/${id}/history`),
    retry: (n, e) => !(e instanceof ApiError && e.status === 404) && n < 1,
  });

  if (history.isPending) return <p className="text-[15px] text-ink-2">Loading the history…</p>;
  if (history.isError) {
    const missing = history.error instanceof ApiError && history.error.status === 404;
    return (
      <div className="flex flex-col gap-2">
        <Link to="/broker/policies" className="text-[15px] font-semibold no-underline">
          ‹ All policies
        </Link>
        <p className="text-[15px] text-ink-2">
          {missing ? "We couldn't find that policy." : "We couldn't load the history. Reload to try again."}
        </p>
      </div>
    );
  }

  const h = history.data;
  return (
    <div className="mx-auto flex w-full max-w-[960px] flex-col gap-5">
      <div className="flex flex-col gap-1.5">
        <Link to={`/broker/policies/${id}`} className="text-[15px] font-semibold no-underline">
          ‹ {h.businessName} · {h.policyNumber}
        </Link>
        <h1 className="m-0 text-[28px] font-bold tracking-[-0.015em] md:text-[32px]">History</h1>
        <p className="m-0 text-base text-ink-2">Every action on this policy, oldest first, with what it did to the money.</p>
      </div>

      {h.actions.length === 0 ? (
        <p className="m-0 text-[15px] text-ink-2">Nothing has happened on this policy yet.</p>
      ) : (
        <ol className="m-0 flex list-none flex-col p-0">
          {h.actions.map((a) => (
            <ActionItem key={a.id} a={a} policyId={id} />
          ))}
        </ol>
      )}
    </div>
  );
}
