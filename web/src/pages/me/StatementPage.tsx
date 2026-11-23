import { useQuery } from '@tanstack/react-query';
import { Fragment, useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useLocation } from 'react-router';
import { api, ApiError } from '../../api/client';
import type { Statement, StatementCorrection, StatementRow } from '../../api/types';
import { HowWeGotThis } from '../../components/HowWeGotThis';
import { StatusPill, type PillTone } from '../../components/StatusPill';
import { Glossed } from '../../components/Glossed';
import { Term } from '../../components/Term';
import { ThemeToggle } from '../../components/ThemeToggle';
import { formatDate } from '../../lib/dates';
import { GLOSSARY } from '../../lib/glossary';
import { formatCents } from '../../lib/money';
import { bannerCopy, correctionNetMaths, correctionSummary, rowNote, rowTitle } from '../../lib/statementCopy';

/** The torn top edge of the statement, as on the P3 receipt. */
const PERFORATION = {
  backgroundImage: 'radial-gradient(circle at 7px 0, var(--pl-bg) 5px, transparent 5.5px)',
  backgroundSize: '14px 9px',
  backgroundRepeat: 'repeat-x',
};

const DATE_HEAD = 'px-[18px] pt-3.5 pb-0.5 text-xs font-semibold uppercase tracking-[0.06em] text-ink-2';
const META = 'mt-0.5 text-[13px] leading-[1.35] text-ink-2';

/** Opens "How we got this" for one row; the button is where focus goes back to. */
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
            Your coverage
          </Link>
          <span className="text-lg font-bold tracking-[-0.01em]">Plainly</span>
        </header>
        <main className="flex flex-col gap-5 px-5 pb-8 pt-3">{children}</main>
      </div>
    </div>
  );
}

/** A line's title with every insurance term in it (reversal, endorsement, return premium…) one tap from its meaning. */
function Title({ text, corrected = false }: { text: string; corrected?: boolean }) {
  return (
    <>
      <Glossed>{text}</Glossed>
      {corrected && (
        <>
          {' '}(<Term explain={GLOSSARY.rebill}>rebill</Term>)
        </>
      )}
    </>
  );
}

/** Every amount is a button that opens how we got it (AC7). Struck when the line was undone later. */
function Amount({ row, onOpen, struck = false }: { row: StatementRow; onOpen: OpenTrace; struck?: boolean }) {
  const text = formatCents(row.amountCents, { signed: true });
  const muted = row.pending || row.amountCents === 0;
  return (
    <button
      type="button"
      onClick={(e) => onOpen(row, e.currentTarget)}
      aria-label={`${text}, ${rowTitle(row)}${row.pending ? ', not counted yet' : ''}. How we got this`}
      className={`cursor-pointer border-none bg-transparent p-0 font-[inherit] text-base font-semibold tabular-nums whitespace-nowrap ${
        struck
          ? 'text-ink-2 line-through decoration-[1.5px]'
          : `underline decoration-ink-2 decoration-dotted underline-offset-4 ${muted ? 'text-ink-2' : 'text-ink'}`
      }`}
    >
      {text}
    </button>
  );
}

const STATUS_TONE: Record<NonNullable<StatementRow['status']>, string> = {
  PAID: 'text-good',
  DECLINED: 'text-bad',
  WAITING: 'text-wait',
  REFUND_SENT: 'text-good',
  REFUND_ON_ITS_WAY: 'text-wait',
};

/** The line under a title: a coloured status word first when money moved (or didn't), else the arithmetic. */
function Note({ row, prefix }: { row: StatementRow; prefix?: string }) {
  const note = rowNote(row);
  if (row.status) {
    const [word, ...rest] = note.split(' · ');
    return (
      <div className={META}>
        <span className={`font-semibold ${STATUS_TONE[row.status]}`}>{word}</span>
        {rest.length > 0 && ` · ${rest.join(' · ')}`}
      </div>
    );
  }
  return (
    <div className={`${META} tabular-nums`}>
      <Glossed>{[prefix, note].filter(Boolean).join(' · ')}</Glossed>
    </div>
  );
}

/** The running balance under an amount; a pending row shows that it isn't counted yet (AC4). */
function BalanceAfter({ row }: { row: StatementRow }) {
  if (row.pending) return <div className={`${META} font-semibold text-accent-ink`}>Not counted yet</div>;
  return <div className={`${META} tabular-nums`}>{formatCents(row.balanceAfterCents)}</div>;
}

function Row({ row, onOpen, first, inCard = false }: { row: StatementRow; onOpen: OpenTrace; first: boolean; inCard?: boolean }) {
  const struck = row.undoneOn != null;
  return (
    <div className={`flex gap-3 ${inCard ? 'px-3.5 py-3' : 'px-[18px] py-3.5'} ${first ? '' : 'border-t border-dashed border-line'}`}>
      <div className="min-w-0 flex-1">
        <div className={`font-semibold ${struck ? 'text-ink-2 line-through decoration-[1.5px]' : ''}`}>
          <Title text={rowTitle(row)} />
        </div>
        <Note row={row} />
      </div>
      <div className="text-right">
        <Amount row={row} onOpen={onOpen} struck={struck} />
        <BalanceAfter row={row} />
      </div>
    </div>
  );
}

/** AC5/AC6: one card per date correction, holding the original, its undo, the corrected line and the broker's words. */
function CorrectionCard({
  c,
  lines,
  onOpen,
  defaultOpen,
  linkedTo = false,
}: {
  c: StatementCorrection;
  lines: Map<string, StatementRow>;
  onOpen: OpenTrace;
  defaultOpen: boolean;
  /** cover-timeline AC4: the P2 link pointed here (#correction-PTX-…), so the card scrolls into view, open. */
  linkedTo?: boolean;
}) {
  const [open, setOpen] = useState(defaultOpen || linkedTo);
  const card = useRef<HTMLElement>(null);
  useEffect(() => {
    if (!linkedTo) return;
    setOpen(true);
    card.current?.scrollIntoView({ block: 'start', behavior: 'smooth' });
    card.current?.focus({ preventScroll: true });
  }, [linkedTo]);
  const original = lines.get(c.originalLineId);
  const undo = lines.get(c.reversalLineId);
  const rebill = lines.get(c.rebillLineId);
  const detailsId = `correction-${c.transactionDisplayId}-details`;
  return (
    <section
      ref={card}
      id={`correction-${c.transactionDisplayId}`}
      tabIndex={-1}
      aria-label={`Correction, ${formatDate(c.recordedOn)}`}
      className="mx-3 mt-2 mb-3 scroll-mt-4 overflow-hidden outline-none rounded-xl border-[1.5px] border-accent bg-card"
    >
      <div className="flex items-start gap-3 px-3.5 pt-3.5 pb-2.5">
        <div className="min-w-0 flex-1">
          <div className="text-xs font-semibold uppercase tracking-[0.06em] text-accent-ink">Correction</div>
          <div className="mt-0.5 text-base font-semibold leading-tight">
            <Glossed>Effective date corrected on your product liability endorsement</Glossed>
          </div>
          <div className={`${META} tabular-nums`}>{correctionSummary(c)}</div>
        </div>
        <div className="text-right">
          <div className="text-xl font-bold tabular-nums text-accent-ink">{formatCents(c.netCents, { signed: true })}</div>
          <div className={META}>net change</div>
        </div>
      </div>

      {open && (
        <div id={detailsId}>
          {original && (
            <div className="flex gap-3 border-t border-dashed border-line px-3.5 py-3">
              <div className="min-w-0 flex-1">
                <div className="font-semibold text-ink-2 line-through decoration-[1.5px]">
                  <Title text={rowTitle(original)} />
                </div>
                <div className={`${META} tabular-nums`}>
                  Original line
                  {original.trace.maths.annualCents != null &&
                    ` · ${formatCents(original.trace.maths.annualCents)} × ${original.trace.maths.days} ÷ ${original.trace.maths.termDays}`}
                </div>
              </div>
              <div className="text-right">
                <Amount row={original} onOpen={onOpen} struck />
              </div>
            </div>
          )}
          {undo && <Row row={undo} onOpen={onOpen} first={false} inCard />}
          {rebill && (
            <div className="flex gap-3 border-t border-dashed border-line px-3.5 py-3">
              <div className="min-w-0 flex-1">
                <div className="font-semibold">
                  <Title text={rowTitle(rebill)} corrected />
                </div>
                <Note row={rebill} />
              </div>
              <div className="text-right">
                <Amount row={rebill} onOpen={onOpen} />
                <BalanceAfter row={rebill} />
              </div>
            </div>
          )}
          {undo && rebill && (
            <div className="flex flex-wrap items-baseline justify-between gap-x-3 bg-accent-soft px-3.5 py-3">
              <span className="font-semibold">Net change</span>
              <span className="font-bold tabular-nums text-accent-ink">{correctionNetMaths(undo.amountCents, rebill.amountCents)}</span>
            </div>
          )}
          {c.reason && (
            <blockquote className="m-0 border-t border-dashed border-line px-3.5 py-3 text-sm leading-[1.45] text-ink-2">
              <span className="text-ink">“{c.reason}”</span>
              <br />
              {c.actorName ? `${c.actorName}, your broker` : 'Your broker'}
            </blockquote>
          )}
        </div>
      )}

      <button
        type="button"
        aria-expanded={open}
        aria-controls={detailsId}
        onClick={() => setOpen((o) => !o)}
        className="flex min-h-11 w-full cursor-pointer items-center justify-center gap-1.5 border-x-0 border-b-0 border-t border-dashed border-line bg-transparent font-[inherit] text-[15px] font-semibold text-accent-ink"
      >
        {open ? 'Hide the details' : 'Show the details'}
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className={open ? 'rotate-180' : ''}>
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>
    </section>
  );
}

const BANNER_PILL: Partial<Record<Statement['banner']['state'], { tone: PillTone; text: string }>> = {
  PAYMENT_WAITING: { tone: 'wait', text: 'Waiting for your bank' },
  REFUND_WAITING: { tone: 'wait', text: 'Refund on its way' },
};

/** AC3: the one thing that matters now. */
function Banner({ s }: { s: Statement }) {
  const copy = bannerCopy(s.banner);
  const pill = BANNER_PILL[s.banner.state];
  const owes = s.banner.state === 'OWES';
  const balance = s.header.balanceCents;
  const headline =
    s.banner.state === 'PAYMENT_WAITING' ? (
      balance > 0 ? (
        <>
          You owe <strong className="tabular-nums text-accent-ink">{formatCents(balance)}</strong> until your bank confirms your payment.
        </>
      ) : (
        'Your payment is with your bank.'
      )
    ) : owes ? (
      <>
        You owe <strong className="tabular-nums text-accent-ink">{formatCents(s.banner.amountCents)}</strong>
        {copy.detail ? (
          <>
            . <Glossed>{copy.detail}</Glossed>
          </>
        ) : (
          '.'
        )}
      </>
    ) : (
      copy.title
    );
  const detail = owes ? null : copy.detail;
  return (
    <section
      aria-label="Summary"
      aria-live="polite"
      className={`flex flex-col gap-3 rounded-2xl bg-card p-5 ${owes ? 'border-[1.5px] border-accent' : 'border border-line'}`}
    >
      <p className="m-0 text-[19px] font-medium leading-snug">{headline}</p>
      {(pill || detail) && (
        <div className="flex flex-col gap-1.5">
          {pill && (
            <span className="self-start">
              <StatusPill tone={pill.tone} dot>
                {pill.text}
              </StatusPill>
            </span>
          )}
          {detail && (
            <span className="text-sm leading-snug text-ink-2">
              <Glossed>{detail}</Glossed>
            </span>
          )}
        </div>
      )}
    </section>
  );
}

function BalanceTotal({ cents }: { cents: number }) {
  const text = cents > 0 ? `${formatCents(cents)} due` : cents < 0 ? `${formatCents(cents, { abs: true })} to you` : formatCents(0);
  return (
    <div className="flex items-baseline justify-between border-t-[1.5px] border-ink px-[18px] py-4">
      <span className="text-[17px] font-bold">Balance</span>
      <span className={`text-xl font-bold tabular-nums ${cents > 0 ? 'text-accent-ink' : 'text-ink'}`}>{text}</span>
    </div>
  );
}

/** The statement lines, oldest first: date headings, rows, and a correction card where each correction was made. */
function Lines({ s, onOpen }: { s: Statement; onOpen: OpenTrace }) {
  const byId = new Map(s.rows.map((r) => [r.id, r]));
  const cardAt = new Map(s.corrections.map((c) => [c.reversalLineId, c]));
  const inCard = new Set(s.corrections.map((c) => c.rebillLineId));
  const latest = s.corrections.at(-1)?.transactionDisplayId;
  const { hash } = useLocation();
  const linked = hash.startsWith('#correction-') ? decodeURIComponent(hash.slice('#correction-'.length)) : null;

  let lastDate: string | null = null;
  const items = s.rows
    .filter((r) => !inCard.has(r.id))
    .map((r) => {
      const newDate = r.date !== lastDate;
      lastDate = r.date;
      const heading = newDate && <div className={DATE_HEAD}>{formatDate(r.date)}</div>;
      const card = cardAt.get(r.id);
      return (
        <Fragment key={r.id}>
          {heading}
          {card ? (
            <CorrectionCard c={card} lines={byId} onOpen={onOpen} defaultOpen={card.transactionDisplayId === latest}
              linkedTo={card.transactionDisplayId === linked}
            />
          ) : (
            <Row row={r} onOpen={onOpen} first={Boolean(newDate)} />
          )}
        </Fragment>
      );
    });

  return (
    <section aria-label="Statement lines" className="overflow-hidden rounded-b-2xl border border-t-0 border-line bg-card">
      <div aria-hidden="true" className="h-[9px]" style={PERFORATION} />
      <div className="flex justify-between px-[18px] pt-2.5 pb-1.5 text-[13px] text-ink-2">
        <span>Oldest first</span>
        <span>Amount · balance</span>
      </div>
      {items.length === 0 && <p className="m-0 px-[18px] py-4 text-[15px] text-ink-2">Nothing on your statement yet.</p>}
      {items}
      <BalanceTotal cents={s.header.balanceCents} />
    </section>
  );
}

/** P3: every charge, credit, payment and refund on the policy, with a running balance and how we got each amount. */
export function StatementPage() {
  const [trace, setTrace] = useState<{ row: StatementRow; from: HTMLButtonElement } | null>(null);
  const statement = useQuery({
    queryKey: ['my-statement'],
    queryFn: () => api<Statement>('/api/me/statement'),
    retry: (n, e) => !(e instanceof ApiError && e.status === 404) && n < 1,
  });

  if (statement.isPending) return <Shell><p className="text-[15px] text-ink-2">Loading your statement…</p></Shell>;
  if (statement.isError) {
    const none = statement.error instanceof ApiError && statement.error.status === 404;
    return (
      <Shell>
        <p className="text-[17px] leading-snug">
          {none ? "You don't have a policy yet, so there's nothing on your statement." : "We couldn't load your statement. Reload to try again."}
        </p>
      </Shell>
    );
  }

  const s = statement.data;
  const onOpen: OpenTrace = (row, from) => setTrace({ row, from });

  return (
    <Shell>
      <div>
        <h1 className="m-0 text-[30px] font-bold tracking-[-0.015em]">Statement</h1>
        <p className="mt-0.5 mb-0 text-[15px] text-ink-2">
          {s.header.businessName} · Policy {s.header.policyNumber}
        </p>
      </div>

      <Banner s={s} />
      <Lines s={s} onOpen={onOpen} />

      <p className="m-0 flex items-start gap-2 text-sm leading-[1.45] text-ink-2">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true" className="mt-px flex-none">
          <circle cx="12" cy="12" r="9" />
          <path d="M12 11v5M12 8h.01" />
        </svg>
        <span>
          Tap any underlined amount to see how we got it. We never edit a line: a mistake gets a{' '}
          <Term explain={GLOSSARY.reversal}>reversal</Term> and a <Term explain={GLOSSARY.rebill}>rebill</Term>, so you
          can always see both.
        </span>
      </p>

      <div className="flex items-center justify-end text-[13px] text-ink-2">
        <ThemeToggle />
      </div>

      {trace && <HowWeGotThis key={trace.row.id} row={trace.row} returnTo={trace.from} onClose={() => setTrace(null)} />}
    </Shell>
  );
}
