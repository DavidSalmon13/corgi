import { useQuery } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { Link } from 'react-router';
import { api, ApiError } from '../../api/client';
import type { StatementRow, Timeline, TimelineCover, TimelineTerm } from '../../api/types';
import { CoverTimeline } from '../../components/CoverTimeline';
import { HowWeGotThis } from '../../components/HowWeGotThis';
import { Term } from '../../components/Term';
import { ThemeToggle } from '../../components/ThemeToggle';
import { formatDate } from '../../lib/dates';
import { formatCents } from '../../lib/money';
import { rowTitle } from '../../lib/statementCopy';
import {
  chargedText,
  correctionReason,
  correctionSentence,
  coverName,
  coverWhat,
  endsText,
  ghostLabel,
  priceText,
  stripedNote,
  timelineAnswer,
} from '../../lib/timelineCopy';

const CARD = 'rounded-2xl border border-line bg-card';
const KV = 'flex items-baseline justify-between gap-3 border-t border-dashed border-line py-2.5 text-[15px]';

/** Opens "How we got this" for one line; the button is where focus goes back to. */
type OpenTrace = (row: StatementRow, from: HTMLButtonElement) => void;

function Shell({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh justify-center bg-bg">
      <div className="flex w-full max-w-[480px] flex-col">
        <header className="flex items-center justify-between pl-2 pr-5 pt-2">
          <Link to="/me" className="flex min-h-11 items-center gap-1 px-2 text-base font-semibold text-accent-ink no-underline hover:text-ink">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M15 18l-6-6 6-6" />
            </svg>
            Your cover
          </Link>
          <span className="text-lg font-bold tracking-[-0.01em]">Plainly</span>
        </header>
        <main className="flex flex-col gap-5 px-5 pb-8 pt-3">{children}</main>
      </div>
    </div>
  );
}

/** "Base cover" / "Extra cover" on the band itself; the full name is on the detail card. */
function bandLabel(c: TimelineCover): string {
  return c.kind === 'BASE' ? 'Base cover' : 'Extra cover';
}

/** Base first, then extra covers by start date (AC3). */
function ordered(covers: TimelineCover[]): TimelineCover[] {
  return [...covers].sort((a, b) => (a.kind === 'BASE' ? -1 : b.kind === 'BASE' ? 1 : a.from.localeCompare(b.from)));
}

/**
 * AC7: "328 days · $656.00", tappable to see how we got it. A cover with one line opens it directly; one with a
 * charge and a cancellation credit lists both lines so each opens its own drawer, as on the statement.
 */
function Charged({ cover, term, onOpen }: { cover: TimelineCover; term: TimelineTerm; onOpen: OpenTrace }) {
  const text = chargedText(cover, term);
  const amountLink = 'cursor-pointer border-none bg-transparent p-0 font-[inherit] font-semibold tabular-nums underline decoration-ink-2 decoration-dotted underline-offset-4 text-ink';
  if (cover.rows.length === 1) {
    const row = cover.rows[0];
    return (
      <button
        type="button"
        onClick={(e) => onOpen(row, e.currentTarget)}
        aria-label={`${text}. How we got this`}
        className={`${amountLink} text-[15px]`}
      >
        {text}
      </button>
    );
  }
  return (
    <span className="flex flex-col items-end gap-1 text-right">
      <span className="font-semibold tabular-nums">{text}</span>
      {cover.rows.map((r) => {
        const amount = formatCents(r.amountCents, { signed: true });
        return (
          <span key={r.id} className="text-[13px] text-ink-2">
            {rowTitle(r)}{' '}
            <button
              type="button"
              onClick={(e) => onOpen(r, e.currentTarget)}
              aria-label={`${amount}, ${rowTitle(r)}. How we got this`}
              className={`${amountLink} text-[13px]`}
            >
              {amount}
            </button>
          </span>
        );
      })}
    </span>
  );
}

/** AC6: the chosen band's name, what it pays for, its dates, price and what was charged for the days covered. */
function Detail({ cover, term, onOpen }: { cover: TimelineCover; term: TimelineTerm; onOpen: OpenTrace }) {
  return (
    <section aria-label="Selected cover" aria-live="polite" className={`${CARD} px-[18px] pt-[18px] pb-2`}>
      <div className="flex items-center gap-2.5">
        <span aria-hidden="true" className={`size-3 flex-none rounded-[3px] ${cover.kind === 'BASE' ? 'bg-cover-base' : 'bg-accent'}`} />
        <h2 className="m-0 text-[19px] font-bold">{coverName(cover.kind)}</h2>
      </div>
      <p className="mt-2 mb-3 text-[15px] leading-[1.45] text-ink-2">{coverWhat(cover.kind)}</p>
      <div className={KV}>
        <span>
          <Term explain="Effective date: the first day this cover protects you.">Starts on</Term>
        </span>
        <span className="font-semibold tabular-nums">{formatDate(cover.from)}</span>
      </div>
      <div className={KV}>
        <span>Ends</span>
        <span className="font-semibold tabular-nums">{endsText(cover, term)}</span>
      </div>
      <div className={KV}>
        <span>
          <Term explain="Premium: what you pay for a year of protection.">Price of your cover</Term>
        </span>
        <span className="text-right font-semibold tabular-nums">{priceText(cover)}</span>
      </div>
      <div className={KV}>
        <span className="flex-none">
          <Term explain="Pro-rata: you only pay for the days you’re covered, yearly price × days ÷ 365.">Charged by the day</Term>
        </span>
        <Charged cover={cover} term={term} onOpen={onOpen} />
      </div>
    </section>
  );
}

/** P2 (/me/timeline): each cover drawn across the policy year, what changed, and what each cover cost by the day. */
export function TimelinePage() {
  const [picked, setPicked] = useState<number | null>(null);
  const [trace, setTrace] = useState<{ row: StatementRow; from: HTMLButtonElement } | null>(null);
  // AC9: always fetched fresh on load, so a broker's correction or cancellation shows on reload.
  const timeline = useQuery({
    queryKey: ['my-timeline'],
    queryFn: () => api<Timeline>('/api/me/timeline'),
    retry: (n, e) => !(e instanceof ApiError && e.status === 404) && n < 1,
  });

  if (timeline.isPending) return <Shell><p className="text-[15px] text-ink-2">Loading your cover…</p></Shell>;
  if (timeline.isError) {
    const none = timeline.error instanceof ApiError && timeline.error.status === 404;
    return (
      <Shell>
        <h1 className="m-0 text-[30px] font-bold tracking-[-0.015em]">Cover timeline</h1>
        <p className="m-0 text-[17px] leading-snug">
          {none
            ? "You don't have any cover yet, so there's nothing to draw. Your broker will set up your policy for you."
            : "We couldn't load your cover. Reload to try again."}
        </p>
      </Shell>
    );
  }

  const { term, covers: given } = timeline.data;
  const covers = ordered(given);
  // AC6: the extra cover is chosen by default when there is one, else the base cover.
  const fallback = covers.find((c) => c.kind !== 'BASE') ?? covers[0];
  const selected = covers.find((c) => c.id === picked) ?? fallback;
  const corrected = covers.filter((c) => c.correction);
  const onOpen: OpenTrace = (row, from) => setTrace({ row, from });

  return (
    <Shell>
      <div>
        <h1 className="m-0 text-[30px] font-bold tracking-[-0.015em]">Cover timeline</h1>
        <p className="mt-1.5 mb-0 text-[17px] leading-[1.4]">{timelineAnswer(term, covers)}</p>
      </div>

      <section aria-label="Timeline" className={`${CARD} px-4 pt-[18px] pb-4`}>
        <CoverTimeline
          variant="select"
          start={term.start}
          end={term.end}
          today={term.today}
          cancelledFrom={term.cancelledOn ?? undefined}
          selectedId={selected?.id}
          onSelect={(id) => setPicked(Number(id))}
          covers={covers.map((c) => ({
            id: c.id,
            kind: c.kind,
            label: bandLabel(c),
            from: c.from,
            to: c.to,
            ghostFrom: c.previousStart ?? undefined,
            ghostLabel: c.previousStart ? ghostLabel(c.previousStart) : undefined,
            accessibleLabel: `${coverName(c.kind)}, ${formatDate(c.from)} to ${endsText(c, term)}`,
          }))}
        />
        {term.cancelledOn && <p className="mt-0 mb-0 text-sm leading-snug text-ink-2">{stripedNote(term.cancelledOn)}</p>}
      </section>

      {selected && <Detail cover={selected} term={term} onOpen={onOpen} />}

      {corrected.map((c) => {
        const k = c.correction!;
        const reason = correctionReason(k);
        return (
          <p key={k.transactionDisplayId} className="m-0 text-[15px] leading-[1.45]">
            {correctionSentence(k, c.kind)}
            {reason && ` ${reason}`}{' '}
            <Link to={`/me/statement#correction-${k.transactionDisplayId}`} className="font-semibold text-accent-ink hover:text-ink">
              See what it changed
            </Link>
          </p>
        );
      })}

      <div className="flex items-center justify-end text-[13px] text-ink-2">
        <ThemeToggle />
      </div>

      {trace && <HowWeGotThis key={trace.row.id} row={trace.row} returnTo={trace.from} onClose={() => setTrace(null)} />}
    </Shell>
  );
}
