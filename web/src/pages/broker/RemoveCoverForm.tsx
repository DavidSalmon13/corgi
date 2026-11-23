import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { GlossScope } from '../../components/GlossScope';
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { api } from '../../api/client';
import type { CoverRemoved, PolicyDetail, RemoveCoverPlan, RemoveCoverRequest } from '../../api/types';
import { Glossed } from '../../components/Glossed';
import { Term } from '../../components/Term';
import { lastEffectiveDate } from '../../lib/addCoverCopy';
import { GLOSSARY } from '../../lib/glossary';
import { formatCents } from '../../lib/money';
import {
  END_DATE_HINT,
  KEEP_LABEL,
  customerWillSeeRemoval,
  removeConfirm,
  removedBanner,
  removePreviewHeading,
  removePreviewRows,
  removeRefundPieces,
  removeSaveLabel,
  removeSaveProblems,
} from '../../lib/removeCoverCopy';
import { useDebounced } from '../../lib/useDebounced';
import { PreviewRow } from './CancelPolicyForm';
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

type Field = 'endDate' | 'reason' | 'policy';

const REASON_MAX = 500;
/** Show how much room is left once the reason gets near the limit. */
const REASON_COUNT_FROM = 400;

/**
 * B4's Remove tab: take product liability off from a chosen day, a mid-term endorsement. The preview runs the same
 * code as the save, so the return premium, anything kept back and the refund the broker sees are exactly what gets
 * posted. Saving asks once more.
 */
export function RemoveCoverForm({ p, back }: { p: PolicyDetail; back: string }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const summaryRef = useRef<HTMLDivElement>(null);
  const dialogRef = useRef<HTMLDialogElement>(null);

  const [endDate, setEndDate] = useState('');
  const [reason, setReason] = useState('');
  const [errors, setErrors] = useState<FormErrors<Field> | null>(null);
  const [failedSaves, setFailedSaves] = useState(0);
  const [confirming, setConfirming] = useState(false);

  const extra = p.coverages.find((c) => c.kind === 'PRODUCT_LIABILITY');

  const pricedDate = useDebounced(endDate, 250);
  const preview = useQuery({
    queryKey: ['remove-cover-preview', p.policyId, pricedDate],
    queryFn: () =>
      api<RemoveCoverPlan>(`/api/broker/policies/${p.policyId}/cover-removal/preview`, {
        method: 'POST',
        body: JSON.stringify({ endDate: pricedDate, reason: null }),
      }),
    enabled: pricedDate !== '',
    placeholderData: keepPreviousData,
    retry: false,
  });
  const previewFields = validationOf(preview.error)?.fields;
  const previewProblem = previewFields ? Object.values(previewFields)[0] : undefined;
  const previewFailed = preview.isError && !previewFields;
  const plan = endDate && !preview.isError ? preview.data : undefined;
  // True while the panel may not yet match the date, so the broker can't confirm numbers they haven't seen.
  const repricing = endDate !== '' && (pricedDate !== endDate || preview.isFetching);
  const cantSave = repricing || previewFailed;

  const request: RemoveCoverRequest = { endDate, reason };
  const save = useMutation({
    mutationFn: () =>
      api<CoverRemoved>(`/api/broker/policies/${p.policyId}/cover-removal`, {
        method: 'POST',
        body: JSON.stringify(request),
      }),
    onSuccess: (saved) => {
      setConfirming(false);
      queryClient.invalidateQueries({ queryKey: ['policy', String(p.policyId)] });
      queryClient.invalidateQueries({ queryKey: ['policies'] });
      const { message, refs } = removedBanner(p.customer.ownerName, saved, p.today);
      const state: ChangeSavedState = { banner: message, refs };
      navigate(back, { state });
    },
    onError: (e) => {
      setConfirming(false);
      const v = validationOf(e);
      setErrors(
        v
          ? { message: v.message, fields: v.fields as Partial<Record<Field, string>> }
          : { message: "We couldn't remove product liability. Nothing was changed. Try again.", fields: {} },
      );
      // Someone else may have removed it, or a payment started, meanwhile: re-read the policy.
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
    // Check here rather than sending the save to find out: a save the server accepts would remove the cover and send
    // the refund without the "Remove product liability?" step.
    const problems = removeSaveProblems(endDate, reason);
    if (problems) {
      setErrors(problems);
      setFailedSaves((n) => n + 1);
      return;
    }
    // A day outside the cover already shows under the date from the preview; there's nothing to confirm without a price.
    if (plan) setConfirming(true);
  };

  const dateError = errors?.fields.endDate ?? previewFields?.endDate;
  const reasonError = errors?.fields.reason;
  const rows = plan ? removePreviewRows(plan) : [];
  const pieces = plan && plan.refunds.length > 1 ? removeRefundPieces(plan) : null;
  const confirm = plan ? removeConfirm(plan, p.customer.ownerName) : null;

  return (
    <>
      <ErrorSummary ref={summaryRef} errors={errors} />

      <div className="flex flex-wrap items-start gap-6">
        <form
          id="remove-cover"
          noValidate
          aria-label="Remove product liability"
          onSubmit={(e) => {
            e.preventDefault();
            submit();
          }}
          className="flex min-w-0 flex-[1_1_460px] flex-col gap-5 rounded-2xl border border-line bg-card p-5 md:p-6"
        >
          <div>
            <h2 className="m-0 text-xl font-bold">
              Remove <Term explain={GLOSSARY.productLiability}>product liability</Term>
            </h2>
            <p className="m-0 mt-1 text-[15px] text-ink-2">
              An <Term explain={GLOSSARY.endorsement}>endorsement</Term> that ends this coverage mid-term.{' '}
              <Term explain={GLOSSARY.generalLiability}>General liability</Term> keeps running.
            </p>
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="endDate" className="text-sm font-semibold">
              Cover ends on
            </label>
            <input
              id="endDate"
              type="date"
              value={endDate}
              min={extra?.from}
              max={lastEffectiveDate(p.end)}
              aria-invalid={!!dateError}
              aria-describedby={dateError ? 'endDate-error endDate-hint' : 'endDate-hint'}
              onChange={(e) => {
                setEndDate(e.target.value);
                clearError('endDate');
              }}
              className={`${INPUT} tabular-nums ${dateError ? 'border-bad' : endDate ? 'border-accent' : 'border-line'}`}
            />
            <span id="endDate-hint" className={HINT}>
              <Glossed>{END_DATE_HINT}</Glossed>
            </span>
            <FieldError id="endDate-error" message={dateError} />
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="reason" className="text-sm font-semibold">
              Reason <span className="font-normal text-ink-2">(required)</span>
            </label>
            <textarea
              id="reason"
              rows={3}
              value={reason}
              placeholder="The customer's reason for removing it"
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
          aria-label="Return premium preview"
          aria-live="polite"
          className="flex min-w-0 flex-[1_1_420px] flex-col gap-4 rounded-2xl border-[1.5px] border-accent bg-card p-5 md:p-6"
        >
          <div>
            <h2 className="m-0 text-xl font-bold">{removePreviewHeading(plan ?? null, p.customer.ownerName)}</h2>
            <p className="m-0 mt-1 text-[15px] text-ink-2">
              The original charge stays on the <Term explain={GLOSSARY.ledger}>ledger</Term>. The{' '}
              <Term explain={GLOSSARY.returnPremium}>return premium</Term> for the unused days, worked out{' '}
              <Term explain={GLOSSARY.proRata}>pro-rata</Term>, comes off as one new line. Nothing is edited.
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
                  <Glossed>{customerWillSeeRemoval(plan, p.today)}</Glossed>
                </p>
                {reason.trim() && (
                  <p className="m-0 text-[15px] leading-normal text-ink-2">Your broker's note: “{reason.trim()}”</p>
                )}
              </div>
            </div>
          ) : (
            <p className="m-0 text-base leading-normal text-ink-2">
              {previewFailed && endDate ? (
                <>
                  We couldn't work out the return premium just now.{' '}
                  <button type="button" onClick={() => preview.refetch()} className="font-semibold text-ink underline">
                    Try again
                  </button>
                </>
              ) : previewProblem && endDate ? (
                <Glossed>{`${previewProblem} We'll show the return premium here, before anything is saved.`}</Glossed>
              ) : (
                <Glossed>
                  {"Pick the day product liability ends and we'll show its return premium and any refund here, before anything is saved."}
                </Glossed>
              )}
            </p>
          )}
          <div className="flex flex-wrap gap-2.5">
            <button type="submit" form="remove-cover" disabled={save.isPending || cantSave} className={BTN}>
              {save.isPending ? 'Removing…' : plan ? removeSaveLabel(plan) : 'Remove product liability'}
            </button>
            <Link to={back} className={BTN2}>
              {KEEP_LABEL}
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
                    ? 'Removing…'
                    : plan.refundTotalCents > 0
                      ? `Yes, remove and refund ${formatCents(plan.refundTotalCents)}`
                      : 'Yes, remove product liability'}
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
