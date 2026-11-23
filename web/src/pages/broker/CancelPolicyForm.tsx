import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { api } from '../../api/client';
import type { CancelPlan, CancelRequest, Cancelled, PolicyDetail } from '../../api/types';
import { Glossed } from '../../components/Glossed';
import { Term } from '../../components/Term';
import { GLOSSARY } from '../../lib/glossary';
import {
  cancelConfirm,
  cancelledBanner,
  cancelPreviewHeading,
  cancelPreviewRows,
  cancelSaveLabel,
  cancelSaveProblems,
  customerWillSeeCancel,
  refundPieces,
  type MoneyRow,
} from '../../lib/cancelCopy';
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

type Field = 'cancelDate' | 'reason' | 'policy';

const REASON_MAX = 500;
/** Show how much room is left once the reason gets near the limit. */
const REASON_COUNT_FROM = 400;

/**
 * B4's Cancel tab: end all cover from a chosen day. The preview runs the same code as the save, so each cover's
 * unused days, anything kept back and the refund the broker sees are exactly what gets posted. Saving asks once more.
 */
export function CancelPolicyForm({ p, back }: { p: PolicyDetail; back: string }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const summaryRef = useRef<HTMLDivElement>(null);
  const dialogRef = useRef<HTMLDialogElement>(null);

  const [cancelDate, setCancelDate] = useState('');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState<FormErrors<Field> | null>(null);
  const [failedSaves, setFailedSaves] = useState(0);
  const [confirming, setConfirming] = useState(false);

  const pricedDate = useDebounced(cancelDate, 250);
  const preview = useQuery({
    queryKey: ['cancel-preview', p.policyId, pricedDate],
    queryFn: () =>
      api<CancelPlan>(`/api/broker/policies/${p.policyId}/cancellation/preview`, {
        method: 'POST',
        body: JSON.stringify({ cancelDate: pricedDate, reason: null }),
      }),
    enabled: pricedDate !== '',
    placeholderData: keepPreviousData,
    retry: false,
  });
  const previewFields = validationOf(preview.error)?.fields;
  const previewProblem = previewFields ? Object.values(previewFields)[0] : undefined;
  const previewFailed = preview.isError && !previewFields;
  const plan = cancelDate && !preview.isError ? preview.data : undefined;
  // True while the panel may not yet match the date, so the broker can't confirm numbers they haven't seen.
  const repricing = cancelDate !== '' && (pricedDate !== cancelDate || preview.isFetching);
  const cantSave = repricing || previewFailed;

  const request: CancelRequest = { cancelDate, reason };
  const save = useMutation({
    mutationFn: () =>
      api<Cancelled>(`/api/broker/policies/${p.policyId}/cancellation`, { method: 'POST', body: JSON.stringify(request) }),
    onSuccess: (saved) => {
      setConfirming(false);
      queryClient.invalidateQueries({ queryKey: ['policy', String(p.policyId)] });
      queryClient.invalidateQueries({ queryKey: ['policies'] });
      const { message, refs } = cancelledBanner(p.customer.ownerName, saved, p.today);
      const state: ChangeSavedState = { banner: message, refs };
      navigate(back, { state });
    },
    onError: (e) => {
      setConfirming(false);
      const v = validationOf(e);
      setErrors(
        v
          ? { message: v.message, fields: v.fields as Partial<Record<Field, string>> }
          : { message: "We couldn't cancel the policy. Nothing was changed. Try again.", fields: {} },
      );
      // Someone else may have cancelled it, or a payment started, meanwhile: re-read the policy.
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
    // Check here rather than sending the save to find out: a save the server accepts would cancel the policy and send
    // the refund without the "Cancel this policy?" step (AC7).
    const problems = cancelSaveProblems(cancelDate, reason);
    if (problems) {
      setErrors(problems);
      setFailedSaves((n) => n + 1);
      return;
    }
    // A day outside the term already shows under the date from the preview; there's nothing to confirm without a price.
    if (plan) setConfirming(true);
  };

  const dateError = errors?.fields.cancelDate ?? previewFields?.cancelDate;
  const reasonError = errors?.fields.reason;
  const rows = plan ? cancelPreviewRows(plan) : [];
  const pieces = plan && plan.refunds.length > 1 ? refundPieces(plan) : null;
  const confirm = plan ? cancelConfirm(plan, p.customer.ownerName) : null;

  return (
    <>
      <ErrorSummary ref={summaryRef} errors={errors} />

      <div className="flex flex-wrap items-start gap-6">
        <form
          id="cancel-policy"
          noValidate
          aria-label="Cancel the policy"
          onSubmit={(e) => {
            e.preventDefault();
            submit();
          }}
          className="flex min-w-0 flex-[1_1_460px] flex-col gap-5 rounded-2xl border border-line bg-card p-5 md:p-6"
        >
          <h2 className="m-0 text-xl font-bold">Cancel the policy</h2>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="cancelDate" className="text-sm font-semibold">
              Cancellation date
            </label>
            <input
              id="cancelDate"
              type="date"
              value={cancelDate}
              min={p.start}
              aria-invalid={!!dateError}
              aria-describedby={dateError ? 'cancelDate-error cancelDate-hint' : 'cancelDate-hint'}
              onChange={(e) => {
                setCancelDate(e.target.value);
                clearError('cancelDate');
              }}
              className={`${INPUT} tabular-nums ${dateError ? 'border-bad' : cancelDate ? 'border-accent' : 'border-line'}`}
            />
            <span id="cancelDate-hint" className={HINT}>
              All coverage stops at the start of this day. No{' '}
              <Term explain={GLOSSARY.shortRate}>short-rate penalty</Term>.
            </span>
            <FieldError id="cancelDate-error" message={dateError} />
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="reason" className="text-sm font-semibold">
              Reason <span className="font-normal text-ink-2">(required)</span>
            </label>
            <textarea
              id="reason"
              rows={3}
              value={reason}
              placeholder="The customer's reason for cancelling"
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
              {reason.trim().length >= REASON_COUNT_FROM && ` ${reason.trim().length} of ${REASON_MAX} characters.`}
            </span>
            <FieldError id="reason-error" message={reasonError} />
          </div>
        </form>

        <aside
          aria-label="Refund preview"
          aria-live="polite"
          className="flex min-w-0 flex-[1_1_420px] flex-col gap-4 rounded-2xl border-[1.5px] border-accent bg-card p-5 md:p-6"
        >
          <div>
            <h2 className="m-0 text-xl font-bold">{cancelPreviewHeading(plan ?? null, p.customer.ownerName)}</h2>
            <p className="m-0 mt-1 text-[15px] text-ink-2">
              Every charge stays on the <Term explain={GLOSSARY.ledger}>ledger</Term>. The{' '}
              <Term explain={GLOSSARY.returnPremium}>return premium</Term> for the unused days comes off as new lines.
              Nothing is edited.
            </p>
          </div>
          {plan ? (
            <div className={`flex flex-col gap-4 transition-opacity ${repricing ? 'opacity-60' : ''}`}>
              <div>
                {rows.map((r) => (
                  <PreviewRow key={r.label} row={r} />
                ))}
                {pieces && <p className={`${HINT} m-0 -mt-1 pb-1 tabular-nums`}>{pieces}.</p>}
              </div>
              <div className="flex flex-col gap-1.5 rounded-card bg-bg px-4 py-3.5">
                <span className="text-xs font-semibold uppercase tracking-[.06em] text-ink-2">Customer will see</span>
                <p className="m-0 text-[17px] font-medium leading-[1.45]">
                  <Glossed>{customerWillSeeCancel(plan, p.today)}</Glossed>
                </p>
                {reason.trim() && (
                  <p className="m-0 text-[15px] leading-normal text-ink-2">Your broker's note: “{reason.trim()}”</p>
                )}
              </div>
            </div>
          ) : (
            <p className="m-0 text-base leading-normal text-ink-2">
              {previewFailed && cancelDate ? (
                <>
                  We couldn't work out the refund just now.{' '}
                  <button type="button" onClick={() => preview.refetch()} className="font-semibold text-ink underline">
                    Try again
                  </button>
                </>
              ) : previewProblem && cancelDate ? (
                <Glossed>{`${previewProblem} We'll show the refund here, before anything is saved.`}</Glossed>
              ) : (
                <Glossed>
                  {"Pick the cancellation date and we'll show each coverage's return premium and the refund here, before anything is saved."}
                </Glossed>
              )}
            </p>
          )}
          <div className="flex flex-wrap gap-2.5">
            <button type="submit" form="cancel-policy" disabled={save.isPending || cantSave} className={BTN}>
              {save.isPending ? 'Cancelling…' : plan ? cancelSaveLabel(plan) : 'Cancel policy'}
            </button>
            <Link to={back} className={BTN2}>
              Keep the policy
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
        {plan && confirm && (
          <div className="flex flex-col gap-4">
            <h2 id="confirm-heading" className="m-0 text-[22px] font-bold">
              {confirm.title}
            </h2>
            <p className="m-0 text-base leading-normal">
              <Glossed>{confirm.sentence}</Glossed>
            </p>
            <div className="flex flex-wrap gap-2.5">
              <button type="button" onClick={() => save.mutate()} disabled={save.isPending} className={BTN}>
                {save.isPending
                  ? 'Cancelling…'
                  : plan.refundTotalCents > 0
                    ? `Yes, cancel and refund ${formatCents(plan.refundTotalCents)}`
                    : 'Yes, cancel the policy'}
              </button>
              <button type="button" onClick={() => setConfirming(false)} className={BTN2}>
                Go back
              </button>
            </div>
          </div>
        )}
      </dialog>
    </>
  );
}

/** One line of the preview; the bottom line (refund or still owed) is bigger and set off by a solid rule. */
function PreviewRow({ row }: { row: MoneyRow }) {
  const label = [row.label, row.detail, row.amount].join(' · ');
  return row.total ? (
    <div role="group" aria-label={label} className="flex items-baseline justify-between gap-4 border-t-[1.5px] border-ink py-3">
      <div className="min-w-0">
        <div className="text-[17px] font-bold">
          <Glossed>{row.label}</Glossed>
        </div>
        <div className={HINT}>
          <Glossed>{row.detail}</Glossed>
        </div>
      </div>
      <span className="whitespace-nowrap text-2xl font-bold tabular-nums text-accent-ink">{row.amount}</span>
    </div>
  ) : (
    <div role="group" aria-label={label} className="flex justify-between gap-4 border-t border-dashed border-line py-3">
      <div className="min-w-0">
        <div className="font-semibold">
          <Glossed>{row.label}</Glossed>
        </div>
        <div className={`${HINT} tabular-nums`}>
          <Glossed>{row.detail}</Glossed>
        </div>
      </div>
      <span className="whitespace-nowrap font-semibold tabular-nums">{row.amount}</span>
    </div>
  );
}
