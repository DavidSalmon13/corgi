import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { GlossScope } from '../../components/GlossScope';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router';
import { api } from '../../api/client';
import type { Corrected, CorrectionPlan, CorrectRequest, PolicyDetail } from '../../api/types';
import { Glossed } from '../../components/Glossed';
import { Money } from '../../components/Money';
import { Term } from '../../components/Term';
import { billHeading, calcArithmetic, lastEffectiveDate } from '../../lib/addCoverCopy';
import {
  confirmSummary,
  correctedBanner,
  correctedLine,
  correctingWhich,
  correctSaveLabel,
  customerWillSeeCorrection,
  netChange,
  netWhy,
  settleLine,
  undoLine,
} from '../../lib/correctCopy';
import { formatDate, formatShortDate } from '../../lib/dates';
import { GLOSSARY } from '../../lib/glossary';
import { formatCents } from '../../lib/money';
import { useDebounced } from '../../lib/useDebounced';
import {
  BTN,
  BTN2,
  ErrorSummary,
  FieldError,
  HINT,
  INPUT,
  validationOf,
  type ChangeSavedState,
  type FormErrors,
} from './changeForm';

type Field = 'startDate' | 'reason' | 'policy';

const REASON_MAX = 500;
/** Show how much room is left once the reason gets near the limit. */
const REASON_COUNT_FROM = 400;

function firstName(ownerName: string): string | null {
  return ownerName.trim().split(/\s+/)[0] || null;
}

/**
 * B4's Correct tab: move the extra cover's start date. The preview runs the same code as the save, so the undo, the
 * corrected charge and any refund the broker sees are exactly what gets posted. Saving asks once more, in one line.
 */
export function CorrectCoverForm({ p, back }: { p: PolicyDetail; back: string }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const summaryRef = useRef<HTMLDivElement>(null);
  const dialogRef = useRef<HTMLDialogElement>(null);
  // The tab is only offered when the policy has extra cover (see ChangePolicyPage), so this is always found.
  const cover = p.coverages.find((c) => c.kind === 'PRODUCT_LIABILITY')!;
  const who = firstName(p.customer.ownerName);

  const [startDate, setStartDate] = useState('');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState<FormErrors<Field> | null>(null);
  const [failedSaves, setFailedSaves] = useState(0);
  const [confirming, setConfirming] = useState(false);

  const pricedStart = useDebounced(startDate, 250);
  const preview = useQuery({
    queryKey: ['correct-preview', p.policyId, cover.from, pricedStart],
    queryFn: () =>
      api<CorrectionPlan>(`/api/broker/policies/${p.policyId}/corrections/preview`, {
        method: 'POST',
        body: JSON.stringify({ startDate: pricedStart, reason: null }),
      }),
    enabled: pricedStart !== '',
    placeholderData: keepPreviousData,
    retry: false,
  });
  const previewFields = validationOf(preview.error)?.fields;
  const previewProblem = previewFields ? Object.values(previewFields)[0] : undefined;
  const previewFailed = preview.isError && !previewFields;
  const plan = startDate && !preview.isError ? preview.data : undefined;
  // True while the panel may not yet match the date, so the broker can't save numbers they haven't seen.
  const repricing = startDate !== '' && (pricedStart !== startDate || preview.isFetching);
  const cantSave = repricing || previewFailed;

  const request: CorrectRequest = { startDate, reason };
  const save = useMutation({
    mutationFn: () =>
      api<Corrected>(`/api/broker/policies/${p.policyId}/corrections`, { method: 'POST', body: JSON.stringify(request) }),
    onSuccess: (saved) => {
      setConfirming(false);
      queryClient.invalidateQueries({ queryKey: ['policy', String(p.policyId)] });
      queryClient.invalidateQueries({ queryKey: ['policies'] });
      const card = plan?.refunds[0]?.cardLast4 ?? null;
      const { message, refs } = correctedBanner(p.customer.ownerName, saved, card);
      const state: ChangeSavedState = { banner: message, refs };
      navigate(back, { state });
    },
    onError: (e) => {
      setConfirming(false);
      const v = validationOf(e);
      setErrors(
        v
          ? { message: v.message, fields: v.fields as Partial<Record<Field, string>> }
          : { message: "We couldn't save the correction. Nothing was changed. Try again.", fields: {} },
      );
      // Someone else may have corrected the cover meanwhile: re-read it so "Starts on now" is current.
      queryClient.invalidateQueries({ queryKey: ['policy', String(p.policyId)] });
      setFailedSaves((n) => n + 1);
    },
  });

  useEffect(() => {
    if (failedSaves > 0) summaryRef.current?.focus();
  }, [failedSaves]);

  useEffect(() => {
    const d = dialogRef.current;
    if (!d) return;
    if (confirming && !d.open) d.showModal();
    if (!confirming && d.open) d.close();
  }, [confirming]);

  const clearError = (f: Field) =>
    setErrors((e) => (e && e.fields[f] ? { ...e, fields: { ...e.fields, [f]: undefined } } : e));

  const submit = () => {
    if (cantSave || save.isPending) return;
    // Without a price or a usable reason there's nothing to confirm: let the server say what to fix, under the field.
    const reasonOk = reason.trim() !== '' && reason.length <= REASON_MAX;
    if (plan && reasonOk) setConfirming(true);
    else save.mutate();
  };

  const startError = errors?.fields.startDate ?? previewFields?.startDate;
  const reasonError = errors?.fields.reason;
  const summary = plan ? confirmSummary(plan, p.customer.ownerName) : null;

  return (
    <>
      <ErrorSummary ref={summaryRef} errors={errors} />

      <div className="flex flex-wrap items-start gap-6">
        <form
          id="correct-cover"
          noValidate
          aria-label="Correct the effective date"
          onSubmit={(e) => {
            e.preventDefault();
            submit();
          }}
          className="flex min-w-0 flex-[1_1_460px] flex-col gap-5 rounded-2xl border border-line bg-card p-5 md:p-6"
        >
          <h2 className="m-0 text-xl font-bold">
            Correct the <Term explain={GLOSSARY.effectiveDate}>effective date</Term>
          </h2>
          <div className="flex flex-col gap-1.5">
            <span className="text-sm font-semibold">Coverage</span>
            <div className={`${INPUT} flex items-center border-line`}>
              <span>
                <Glossed>{correctingWhich(cover.annualPremiumCents)}</Glossed>
              </span>
            </div>
          </div>
          <div className="flex flex-wrap items-start gap-4">
            <div className="flex min-w-0 flex-[1_1_160px] flex-col gap-1.5">
              <span className="text-sm font-semibold" id="oldStart-label">
                <Term explain={GLOSSARY.effectiveDate}>Effective date</Term> now
              </span>
              <div
                aria-labelledby="oldStart-label"
                className="flex min-h-11 items-center rounded-control border-[1.5px] border-transparent bg-track px-3 py-2.5 text-base tabular-nums text-ink-2 line-through"
              >
                {formatDate(cover.from)}
              </div>
            </div>
            <svg
              width="24"
              height="44"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
              aria-hidden="true"
              className="mt-[26px] hidden shrink-0 text-ink-2 sm:block"
            >
              <path d="M5 12h14M13 6l6 6-6 6" />
            </svg>
            <div className="flex min-w-0 flex-[1_1_160px] flex-col gap-1.5">
              <label htmlFor="startDate" className="text-sm font-semibold">
                Correct <Term explain={GLOSSARY.effectiveDate}>effective date</Term>
              </label>
              <input
                id="startDate"
                type="date"
                value={startDate}
                min={p.start}
                max={lastEffectiveDate(p.end)}
                aria-invalid={!!startError}
                aria-describedby={startError ? 'startDate-error' : undefined}
                onChange={(e) => {
                  setStartDate(e.target.value);
                  clearError('startDate');
                }}
                className={`${INPUT} tabular-nums ${startError ? 'border-bad' : startDate ? 'border-accent' : 'border-line'}`}
              />
              <FieldError id="startDate-error" message={startError} />
            </div>
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="reason" className="text-sm font-semibold">
              Reason <span className="font-normal text-ink-2">(required)</span>
            </label>
            <textarea
              id="reason"
              rows={3}
              value={reason}
              aria-invalid={!!reasonError}
              aria-describedby={reasonError ? 'reason-error reason-hint' : 'reason-hint'}
              onChange={(e) => {
                setReason(e.target.value);
                clearError('reason');
              }}
              className={`${INPUT} resize-y leading-[1.45] ${reasonError ? 'border-bad' : 'border-line'}`}
            />
            <span id="reason-hint" className={HINT}>
              The customer sees this reason word for word.
              {reason.length >= REASON_COUNT_FROM && ` ${reason.length} of ${REASON_MAX} characters.`}
            </span>
            <FieldError id="reason-error" message={reasonError} />
          </div>
        </form>

        <aside
          aria-label="Money preview"
          aria-live="polite"
          className="flex min-w-0 flex-[1_1_420px] flex-col gap-4 rounded-2xl border-[1.5px] border-accent bg-card p-5 md:p-6"
        >
          <div>
            <h2 className="m-0 text-xl font-bold">{billHeading(p.customer.ownerName)}</h2>
            <p className="m-0 mt-1 text-[15px] text-ink-2">
              The {formatShortDate(cover.from)} line stays on the bill and gets a{' '}
              <Term explain={GLOSSARY.reversal}>reversal</Term>. Nothing is edited.
            </p>
          </div>
          {plan ? (
            <div className={`flex flex-col gap-4 transition-opacity ${repricing ? 'opacity-60' : ''}`}>
              <div>
                <PreviewRow
                  label={undoLine(plan)}
                  title={
                    <>
                      <Term explain={GLOSSARY.reversal}>Reversal</Term>:{' '}
                      <Glossed>{`product liability from ${formatShortDate(plan.oldStart)}`}</Glossed>
                    </>
                  }
                  how={<>{calcArithmetic(plan.undo.calc)} · cancels the original line in full</>}
                  cents={plan.undo.amountCents}
                />
                <PreviewRow
                  label={correctedLine(plan)}
                  title={
                    <>
                      <Term explain={GLOSSARY.rebill}>Rebill</Term>:{' '}
                      <Glossed>{`product liability from ${formatShortDate(plan.newStart)}`}</Glossed>
                    </>
                  }
                  how={
                    <>
                      {calcArithmetic(plan.rebill.calc)} · <Term explain={GLOSSARY.proRata}>pro-rata</Term>
                    </>
                  }
                  cents={plan.rebill.calc.amountCents}
                />
                <div
                  className="flex items-baseline justify-between gap-4 border-t-[1.5px] border-ink py-3"
                  aria-label={`Net change ${netChange(plan)}`}
                  role="group"
                >
                  <div className="min-w-0">
                    <div className="text-[17px] font-bold">Net change</div>
                    <div className={`${HINT} tabular-nums`}>{netWhy(plan)}</div>
                  </div>
                  <Money cents={plan.netCents} signed className="text-2xl font-bold text-accent-ink" />
                </div>
                {plan.refunds.map((r) => (
                  <div key={r.paymentId} className="flex justify-between gap-4 border-t border-dashed border-line py-3">
                    <div className="min-w-0">
                      <div className="font-semibold">
                        Refund to {r.cardLast4 ? `card ending ${r.cardLast4}` : 'the card'}
                      </div>
                      <div className={HINT}>
                        From payment {r.paymentDisplayId} · sent through Stripe when you save
                      </div>
                    </div>
                    <Money cents={r.amountCents} className="font-semibold" />
                  </div>
                ))}
              </div>
              <div className="flex flex-col gap-1.5 rounded-card bg-bg px-4 py-3.5">
                <span className="text-xs font-semibold uppercase tracking-[.06em] text-ink-2">Customer will see</span>
                <p className="m-0 text-[17px] font-medium leading-[1.45]">
                  <Glossed>{customerWillSeeCorrection(plan)}</Glossed>
                </p>
                {reason.trim() && (
                  <p className="m-0 text-[15px] leading-normal text-ink-2">
                    Your broker's note: “{reason.trim()}”
                  </p>
                )}
              </div>
              <p className="m-0 text-sm leading-snug text-ink-2">{settleLine(plan, p.customer.ownerName)}</p>
            </div>
          ) : (
            <p className="m-0 text-base leading-normal text-ink-2">
              {previewFailed && startDate ? (
                <>
                  We couldn't work out the change just now.{' '}
                  <button type="button" onClick={() => preview.refetch()} className="font-semibold text-ink underline">
                    Try again
                  </button>
                </>
              ) : previewProblem && startDate ? (
                <Glossed>{`${previewProblem} We'll show what changes here, before anything is saved.`}</Glossed>
              ) : (
                <Glossed>
                  {"Pick the real effective date of the endorsement and we'll show what changes here, before anything is saved."}
                </Glossed>
              )}
            </p>
          )}
          <div className="flex flex-wrap gap-2.5">
            <button type="submit" form="correct-cover" disabled={save.isPending || cantSave} className={BTN}>
              {save.isPending ? 'Saving…' : plan ? correctSaveLabel(plan) : 'Save correction'}
            </button>
            <Link to={back} className={BTN2}>
              Discard
            </Link>
          </div>
        </aside>
      </div>

      <dialog
        ref={dialogRef}
        aria-labelledby="confirm-heading"
        onClose={() => setConfirming(false)}
        className="m-auto w-[calc(100%-32px)] max-w-[480px] rounded-2xl bg-card p-6 text-ink shadow-[0_20px_50px_rgba(0,0,0,.25)] backdrop:bg-[rgba(21,24,31,.45)] md:p-7"
      >
        <GlossScope>
          {plan && summary && (
            <div className="flex flex-col gap-4">
              <h2 id="confirm-heading" className="m-0 text-[22px] font-bold">
                Save this correction?
              </h2>
              <p className="m-0 text-base leading-normal">
                <Glossed>{summary.sentence}</Glossed>
              </p>
              <div className="rounded-control bg-bg px-3.5 py-3 text-[15px] leading-relaxed text-ink-2 tabular-nums">
                {summary.amounts.split(' · ').map((part, i) => (
                  <span key={part}>
                    {i > 0 && ' · '}
                    <span className="whitespace-nowrap">
                      <Glossed>{part}</Glossed>
                    </span>
                  </span>
                ))}
              </div>
              <div className="flex flex-wrap gap-2.5">
                <button type="button" onClick={() => save.mutate()} disabled={save.isPending} className={BTN}>
                  {save.isPending
                    ? 'Saving…'
                    : plan.refundTotalCents > 0
                      ? `Yes, save and refund ${formatCents(plan.refundTotalCents)}`
                      : `Yes, save and tell ${who ?? 'the customer'}`}
                </button>
                <button type="button" onClick={() => setConfirming(false)} className={BTN2}>
                  Go back
                </button>
              </div>
            </div>
          )}
        </GlossScope>
      </dialog>
    </>
  );
}

/** One line of the preview: what it is, how it was worked out, and the amount. {@code label} is the full line read aloud. */
function PreviewRow({ label, title, how, cents }: { label: string; title: ReactNode; how: ReactNode; cents: number }) {
  return (
    <div role="group" aria-label={label} className="flex justify-between gap-4 border-t border-dashed border-line py-3">
      <div className="min-w-0">
        <div className="font-semibold">{title}</div>
        <div className={`${HINT} tabular-nums`}>{how}</div>
      </div>
      <Money cents={cents} signed className="font-semibold" />
    </div>
  );
}
