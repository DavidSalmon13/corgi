import { useEffect, useId, useRef } from 'react';
import type { MoneyStatus, StatementRow } from '../api/types';
import { drawerCopy, type Audience } from '../lib/statementCopy';
import { Glossed } from './Glossed';
import { StatusPill, type PillTone } from './StatusPill';

const STATUS_TONE: Record<MoneyStatus, PillTone> = {
  PAID: 'good',
  REFUND_SENT: 'good',
  WAITING: 'wait',
  REFUND_ON_ITS_WAY: 'wait',
  DECLINED: 'bad',
};

const SECTION = 'flex flex-col gap-2 border-t border-dashed border-line px-5 py-[18px]';
const SECTION_HEAD = 'm-0 text-xs font-semibold uppercase tracking-[0.06em] text-ink-2';

type Props = {
  row: StatementRow;
  /** Called after the drawer has closed; focus has already gone back to `returnTo`. */
  onClose: () => void;
  /** The amount that opened the drawer, where focus goes back to (AC8). */
  returnTo: HTMLElement | null;
  /** Whose voice: the policyholder's statement (default) or the broker's history, same numbers either way. */
  audience?: Audience;
};

/**
 * P4 "How we got this" for one statement amount: the plain reason, the maths, what caused it, the money and the
 * references. A modal dialog: a bottom sheet on phones, a centred card on wider screens. The native <dialog> keeps
 * focus inside and makes the page behind it inert; Esc, the close button and a tap outside all close it, and focus
 * goes back to the amount that opened it.
 */
export function HowWeGotThis({ row, onClose, returnTo, audience = 'policyholder' }: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const titleId = useId();
  const d = drawerCopy(row, audience);
  const status = row.trace.money?.status ?? null;

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (!dialog.open) dialog.showModal();
    closeButton.current?.focus();
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = overflow;
    };
  }, []);

  const close = () => ref.current?.close();

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onClose={() => {
        returnTo?.focus();
        onClose();
      }}
      onClick={(e) => {
        // A click on the backdrop lands on the <dialog> itself, outside the panel.
        if (e.target === e.currentTarget) close();
      }}
      className="m-0 mt-auto max-h-[calc(100dvh-56px)] w-full max-w-none overflow-y-auto overscroll-contain rounded-t-[22px] border-none bg-card p-0 text-ink backdrop:bg-[rgba(21,24,31,0.45)] sm:m-auto sm:max-h-[85dvh] sm:max-w-[480px] sm:rounded-[22px] sm:shadow-2xl"
    >
      <div className="flex flex-col pb-[max(8px,env(safe-area-inset-bottom))]">
        <div aria-hidden="true" className="mx-auto mt-2.5 h-[5px] w-10 rounded-[3px] bg-line sm:hidden" />
        <div className="flex items-start gap-3 pt-3.5 pr-3 pb-4 pl-5">
          <div className="min-w-0 flex-1">
            <h2 id={titleId} className="m-0 text-[22px] font-bold">
              How we got this
            </h2>
            <p className="mt-1 mb-0 text-[15px] text-ink-2"><Glossed>{d.line}</Glossed></p>
          </div>
          <button
            ref={closeButton}
            type="button"
            aria-label="Close"
            onClick={close}
            className="flex size-11 flex-none cursor-pointer items-center justify-center rounded-full border-none bg-track text-ink"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" />
            </svg>
          </button>
        </div>

        <div className="mx-5 mb-[18px] flex flex-col gap-1.5 rounded-[14px] bg-accent-soft px-[18px] py-4">
          <span className="text-[34px] font-bold leading-none tracking-[-0.02em] tabular-nums text-accent-ink">{d.amount}</span>
          <span className="text-[15px] leading-snug">
            <Glossed>{d.plain}</Glossed>
          </span>
        </div>

        <section aria-labelledby={`${titleId}-maths`} className={SECTION}>
          <h3 id={`${titleId}-maths`} className={SECTION_HEAD}>
            The maths
          </h3>
          <div className="text-[17px] font-semibold leading-snug tabular-nums">
            <Glossed>{d.formula}</Glossed>
          </div>
          {d.formulaNote && (
            <div className="text-sm leading-[1.45] text-ink-2">
              <Glossed>{d.formulaNote}</Glossed>
            </div>
          )}
        </section>

        <section aria-labelledby={`${titleId}-cause`} className={SECTION}>
          <h3 id={`${titleId}-cause`} className={SECTION_HEAD}>
            What caused it
          </h3>
          <div className="text-base font-semibold">
            <Glossed>{d.actionWhat}</Glossed>
          </div>
          <div className="text-sm text-ink-2">{d.actionWho}</div>
          {d.reason && (
            <blockquote className="mx-0 mt-0.5 mb-0 rounded-[10px] bg-bg px-3 py-2.5 text-[15px] leading-[1.45]">“{d.reason}”</blockquote>
          )}
        </section>

        <section aria-labelledby={`${titleId}-money`} className={SECTION}>
          <h3 id={`${titleId}-money`} className={SECTION_HEAD}>
            {row.kind === 'REFUND' ? 'The refund' : 'The payment'}
          </h3>
          {d.payment && status && (
            <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
              <span className="text-base font-semibold">{d.payment.what}</span>
              <StatusPill tone={STATUS_TONE[status]}>{d.payment.status}</StatusPill>
            </div>
          )}
          <div className="text-sm leading-[1.45] text-ink-2"><Glossed>{(d.payment ? d.payment.note : d.noMoney) ?? ''}</Glossed></div>
        </section>

        {d.refs.length > 0 && (
          <section aria-labelledby={`${titleId}-refs`} className={SECTION}>
            <h3 id={`${titleId}-refs`} className={SECTION_HEAD}>
              References
            </h3>
            <dl className="m-0 flex flex-wrap items-center gap-x-2 gap-y-1.5 text-sm text-ink-2">
              {d.refs.map((r) => (
                <div key={`${r.label}-${r.id}`} className="inline-flex min-w-0 items-center gap-1.5">
                  <dt>{r.label}</dt>
                  <dd className="m-0 min-w-0 break-all rounded-md bg-track px-[7px] py-0.5 font-mono text-[13px] text-ink">{r.id}</dd>
                </div>
              ))}
            </dl>
            <span className="text-[13px] text-ink-2">{d.refsNote}</span>
          </section>
        )}
      </div>
    </dialog>
  );
}
