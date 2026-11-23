import { useQuery } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { Link, useParams } from 'react-router';
import { api, ApiError } from '../../api/client';
import type { History, HistoryAction, StatementRow } from '../../api/types';
import { HowWeGotThis } from '../../components/HowWeGotThis';
import { formatDate } from '../../lib/dates';
import { formatCents } from '../../lib/money';
import { rowTitle } from '../../lib/statementCopy';
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

/** Opens "How we got this" for one row; the button is where focus goes back to (AC7). */
type OpenTrace = (row: StatementRow, from: HTMLButtonElement) => void;

const TRACE_BUTTON =
  'cursor-pointer border-none bg-transparent p-0 font-[inherit] text-inherit tabular-nums underline decoration-ink-2 decoration-dotted underline-offset-4 hover:text-accent-ink';

/** An amount that opens the drawer for its line. */
function TraceAmount({ row, text, onOpen, className = '' }: { row: StatementRow; text: string; onOpen: OpenTrace; className?: string }) {
  return (
    <button
      type="button"
      onClick={(e) => onOpen(row, e.currentTarget)}
      aria-label={`${text}, ${rowTitle(row)}. How we got this`}
      className={`${TRACE_BUTTON} whitespace-nowrap ${className}`}
    >
      {text}
    </button>
  );
}

/** A ledger line chip (LE-…) that opens the drawer for that line; struck when a later line undid it. */
function Chip({ row, undone, onOpen }: { row: StatementRow; undone: boolean; onOpen: OpenTrace }) {
  return (
    <button
      type="button"
      onClick={(e) => onOpen(row, e.currentTarget)}
      aria-label={`Line ${row.id}${undone ? ', undone later' : ''}. How we got this`}
      className={`cursor-pointer whitespace-nowrap rounded-[5px] border-none bg-track px-1.5 py-px font-mono text-[12.5px] text-ink hover:bg-accent-soft ${
        undone ? 'line-through decoration-[1.5px]' : ''
      }`}
    >
      {row.id}
    </button>
  );
}

/**
 * The detail line with each of the card's line amounts as a button, so a correction's "−$642.00" and "+$656.00"
 * open their own lines. Amounts are matched in order; text without one stays plain.
 */
function Detail({ a, text, onOpen }: { a: HistoryAction; text: string; onOpen: OpenTrace }) {
  if (a.rows.length < 2) return <>{text}</>;
  const parts: ReactNode[] = [];
  let rest = text;
  for (const row of a.rows) {
    const amount = formatCents(row.amountCents, { signed: true });
    const at = rest.indexOf(amount);
    if (at < 0) continue;
    parts.push(rest.slice(0, at), <TraceAmount key={row.id} row={row} text={amount} onOpen={onOpen} />);
    rest = rest.slice(at + amount.length);
  }
  parts.push(rest);
  return <>{parts}</>;
}

/** One action on the rail: date and who on the left (above the card on a phone), the dot, then the card. */
function ActionItem({ a, policyId, onOpen }: { a: HistoryAction; policyId: string; onOpen: OpenTrace }) {
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
            <span className={`text-[17px] font-bold tabular-nums whitespace-nowrap ${moneyColour(a)}`}>
              {a.rows.length === 1 ? <TraceAmount row={a.rows[0]} text={actionMoney(a)} onOpen={onOpen} /> : actionMoney(a)}
            </span>
          </div>

          <p className="m-0 break-words text-[15px] leading-[1.45] text-ink-2 tabular-nums">
            <Detail a={a} text={actionDetail(a)} onOpen={onOpen} />
          </p>

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
              {chips.map((c) => (
                <Chip key={c.id} row={c.row} undone={c.undone} onOpen={onOpen} />
              ))}
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
  const [trace, setTrace] = useState<{ row: StatementRow; from: HTMLButtonElement } | null>(null);
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
  const onOpen: OpenTrace = (row, from) => setTrace({ row, from });
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
            <ActionItem key={a.id} a={a} policyId={id} onOpen={onOpen} />
          ))}
        </ol>
      )}

      <p className="m-0 text-sm leading-[1.45] text-ink-2">
        Select any underlined amount or a ledger line to see how it was worked out. The customer sees the same numbers on
        their statement.
      </p>

      {trace && (
        <HowWeGotThis key={trace.row.id} row={trace.row} audience="broker" returnTo={trace.from} onClose={() => setTrace(null)} />
      )}
    </div>
  );
}
