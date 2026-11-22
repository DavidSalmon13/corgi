import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { api, ApiError } from '../../api/client';
import type { AddCoverPlan, AddCoverRequest, CoverAdded, PolicyDetail, ValidationBody } from '../../api/types';
import { Money } from '../../components/Money';
import { Term } from '../../components/Term';
import {
  billHeading,
  calcArithmetic,
  coverOptionLabel,
  customerWillSeeAddCover,
  defaultStart,
  saveLabel,
  savedBanner,
  startsOnHint,
} from '../../lib/addCoverCopy';
import { daysBetween } from '../../lib/dates';
import { useDebounced } from '../../lib/useDebounced';

type Field = 'kind' | 'startDate' | 'policy';

const EXTRA_CENTS = 73_000;
const INPUT = 'min-h-11 w-full rounded-control border-[1.5px] bg-card px-3 py-2.5 text-base text-ink';
const HINT = 'text-[13px] leading-snug text-ink-2';
const BTN2 =
  'inline-flex min-h-11 items-center justify-center rounded-control border-[1.5px] border-line px-[18px] text-base font-semibold text-ink no-underline';

function validationOf(e: unknown): ValidationBody | null {
  return e instanceof ApiError && e.status === 422 ? (e.body as ValidationBody) : null;
}

/** What B3 shows after a save; PolicyWorkspace reads it from router state. */
export type ChangeSavedState = { banner: string };

/** B4 "Change this policy". Only the Add cover tab for now; Correct and Cancel come with their own specs. */
export function ChangePolicyPage() {
  const { id } = useParams();
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
        <Link to="/broker/policies" className="text-[15px] font-semibold no-underline">
          ‹ All policies
        </Link>
        <p className="text-[15px] text-ink-2">
          {missing ? "We couldn't find that policy." : "We couldn't load the policy. Reload to try again."}
        </p>
      </div>
    );
  }
  return <ChangePolicy p={policy.data} />;
}

function ChangePolicy({ p }: { p: PolicyDetail }) {
  const back = `/broker/policies/${p.policyId}`;
  const blocked =
    p.status === 'CANCELLED'
      ? "This policy is cancelled, so cover can't be added."
      : p.coverages.some((c) => c.kind === 'PRODUCT_LIABILITY')
        ? 'This policy already has product liability cover.'
        : null;

  return (
    <>
      <div className="flex flex-col gap-1.5">
        <Link to={back} className="text-[15px] font-semibold no-underline">
          ‹ {p.customer.legalName} · {p.policyNumber}
        </Link>
        <h1 className="m-0 text-[28px] font-bold tracking-[-0.015em] md:text-[32px]">Change this policy</h1>
        <p className="m-0 text-base text-ink-2">Nothing is saved until you've seen what it costs or refunds the customer.</p>
      </div>

      <div role="group" aria-label="Action" className="flex flex-wrap gap-2">
        <button
          type="button"
          aria-pressed="true"
          className="min-h-11 rounded-full border-[1.5px] border-ink bg-ink px-[18px] text-[15px] font-semibold text-bg"
        >
          Add cover
        </button>
      </div>

      {blocked ? (
        <div className="flex flex-col items-start gap-3 rounded-2xl border border-line bg-card p-5 md:p-6">
          <p className="m-0 text-[17px]">{blocked}</p>
          <Link to={back} className={BTN2}>
            Back to policy
          </Link>
        </div>
      ) : (
        <AddCoverForm p={p} back={back} />
      )}
    </>
  );
}

function AddCoverForm({ p, back }: { p: PolicyDetail; back: string }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const summaryRef = useRef<HTMLDivElement>(null);
  const termDays = daysBetween(p.start, p.end);

  const [startDate, setStartDate] = useState(() => defaultStart(p.today, p.start, p.end));
  const [errors, setErrors] = useState<{ message: string; fields: Partial<Record<Field, string>> } | null>(null);
  // Bumped once per failed save, so focus moves to the summary then — not again while fields are being fixed.
  const [failedSaves, setFailedSaves] = useState(0);

  const request: AddCoverRequest = { kind: 'PRODUCT_LIABILITY', startDate };
  const pricedStart = useDebounced(startDate, 250);
  const preview = useQuery({
    queryKey: ['add-cover-preview', p.policyId, 'PRODUCT_LIABILITY', pricedStart],
    queryFn: () =>
      api<AddCoverPlan>(`/api/broker/policies/${p.policyId}/coverages/preview`, {
        method: 'POST',
        body: JSON.stringify({ kind: 'PRODUCT_LIABILITY', startDate: pricedStart }),
      }),
    enabled: pricedStart !== '',
    placeholderData: keepPreviousData,
    retry: false,
  });
  const previewFields = validationOf(preview.error)?.fields;
  const previewProblem = previewFields ? Object.values(previewFields)[0] : undefined;
  const previewFailed = preview.isError && !previewFields;
  const plan = startDate && !preview.isError ? preview.data : undefined;
  // True while the panel may not yet match the date, so the broker can't save a price they haven't seen.
  const repricing = startDate !== '' && (pricedStart !== startDate || preview.isFetching);

  const save = useMutation({
    mutationFn: () =>
      api<CoverAdded>(`/api/broker/policies/${p.policyId}/coverages`, { method: 'POST', body: JSON.stringify(request) }),
    onSuccess: (added) => {
      queryClient.invalidateQueries({ queryKey: ['policy', String(p.policyId)] });
      queryClient.invalidateQueries({ queryKey: ['policies'] });
      const state: ChangeSavedState = { banner: savedBanner(p.customer.ownerName, added.balanceCents) };
      navigate(back, { state });
    },
    onError: (e) => {
      const v = validationOf(e);
      setErrors(
        v
          ? { message: v.message, fields: v.fields as Partial<Record<Field, string>> }
          : { message: "We couldn't add the cover. Try again.", fields: {} },
      );
      setFailedSaves((n) => n + 1);
    },
  });

  useEffect(() => {
    if (failedSaves > 0) summaryRef.current?.focus();
  }, [failedSaves]);

  const changeStart = (value: string) => {
    setStartDate(value);
    setErrors((e) => (e && e.fields.startDate ? { ...e, fields: { ...e.fields, startDate: undefined } } : e));
  };

  const open = errors ? Object.values(errors.fields).filter(Boolean).length : 0;
  const hadFieldErrors = errors ? Object.keys(errors.fields).length > 0 : false;
  // The summary goes once every field it listed is fixed.
  const summary = errors && (!hadFieldErrors || open > 0) ? errors.message : null;
  const startError = errors?.fields.startDate ?? previewFields?.startDate;
  const kindError = errors?.fields.kind ?? previewFields?.kind;

  return (
    <>
      {summary && errors && (
        <div ref={summaryRef} tabIndex={-1} role="alert" className="rounded-card bg-bad-soft px-4 py-3.5 font-semibold text-bad">
          {summary}
          {open > 0 && (
            <ul className="mt-1.5 list-disc pl-5 font-normal">
              {Object.entries(errors.fields).map(([f, m]) =>
                m ? (
                  <li key={f}>
                    {f === 'policy' ? (
                      m
                    ) : (
                      <a href={`#${f}`} className="text-bad underline">
                        {m}
                      </a>
                    )}
                  </li>
                ) : null,
              )}
            </ul>
          )}
        </div>
      )}

      <div className="flex flex-wrap items-start gap-6">
        <form
          id="add-cover"
          noValidate
          aria-label="Add cover"
          onSubmit={(e) => {
            e.preventDefault();
            save.mutate();
          }}
          className="flex min-w-0 flex-[1_1_460px] flex-col gap-5 rounded-2xl border border-line bg-card p-5 md:p-6"
        >
          <h2 className="m-0 text-xl font-bold">Add extra cover</h2>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="kind" className="text-sm font-semibold">
              Cover
            </label>
            <select
              id="kind"
              value="PRODUCT_LIABILITY"
              onChange={() => undefined}
              aria-invalid={!!kindError}
              aria-describedby={kindError ? 'kind-error kind-hint' : 'kind-hint'}
              className={`${INPUT} ${kindError ? 'border-bad' : 'border-line'}`}
            >
              <option value="PRODUCT_LIABILITY">{coverOptionLabel(EXTRA_CENTS, termDays)}</option>
            </select>
            <span id="kind-hint" className={HINT}>
              Pays if something the business sells hurts someone or damages their property.
            </span>
            {kindError && (
              <span id="kind-error" className="text-sm font-semibold text-bad">
                {kindError}
              </span>
            )}
          </div>
          <div className="flex flex-col gap-1.5">
            <label htmlFor="startDate" className="text-sm font-semibold">
              <Term explain="The first day the extra cover protects the customer.">Starts on</Term>
            </label>
            <input
              id="startDate"
              type="date"
              value={startDate}
              min={p.start}
              aria-invalid={!!startError}
              aria-describedby={startError ? 'startDate-error startDate-hint' : 'startDate-hint'}
              onChange={(e) => changeStart(e.target.value)}
              className={`${INPUT} tabular-nums ${startError ? 'border-bad' : 'border-line'}`}
            />
            <span id="startDate-hint" className={HINT}>
              {startsOnHint(p.end)}
            </span>
            {startError && (
              <span id="startDate-error" className="text-sm font-semibold text-bad">
                {startError}
              </span>
            )}
          </div>
        </form>

        <aside
          aria-label="Money preview"
          aria-live="polite"
          className="flex min-w-0 flex-[1_1_420px] flex-col gap-4 rounded-2xl border-[1.5px] border-accent bg-card p-5 md:p-6"
        >
          <h2 className="m-0 text-xl font-bold">{billHeading(p.customer.ownerName)}</h2>
          {plan ? (
            <div className={`flex flex-col gap-4 transition-opacity ${repricing ? 'opacity-60' : ''}`}>
              <div>
                <div className="flex justify-between gap-4 border-t border-dashed border-line py-3">
                  <div className="min-w-0">
                    <div className="font-semibold">{plan.label}</div>
                    <div className={`${HINT} tabular-nums`}>
                      {calcArithmetic(plan.calc)} ·{' '}
                      <Term explain="Pro-rata: the yearly price × the days covered ÷ the days in the policy year, so every day costs the same.">
                        charged by the day
                      </Term>
                    </div>
                  </div>
                  <Money cents={plan.totalCents} signed className="font-semibold" />
                </div>
                <div className="flex items-baseline justify-between gap-4 border-t-[1.5px] border-ink py-3">
                  <span className="text-[17px] font-bold">Customer pays</span>
                  <Money cents={plan.totalCents} signed className="text-2xl font-bold text-accent-ink" />
                </div>
              </div>
              <div className="flex flex-col gap-1.5 rounded-card bg-bg px-4 py-3.5">
                <span className="text-xs font-semibold uppercase tracking-[.06em] text-ink-2">Customer will see</span>
                <p className="m-0 text-[17px] font-medium leading-[1.45]">{customerWillSeeAddCover(plan.from, plan.calc)}</p>
              </div>
            </div>
          ) : (
            <p className="m-0 text-base leading-normal text-ink-2">
              {previewFailed && startDate ? (
                <>
                  We couldn't work out the price just now.{' '}
                  <button type="button" onClick={() => preview.refetch()} className="font-semibold text-ink underline">
                    Try again
                  </button>
                </>
              ) : previewProblem && startDate ? (
                `${previewProblem} We'll show the price here, before anything is saved.`
              ) : (
                "Pick a start date and we'll show the price here, before anything is saved."
              )}
            </p>
          )}
          <div className="flex flex-wrap gap-2.5">
            <button
              type="submit"
              form="add-cover"
              disabled={save.isPending || repricing}
              className="inline-flex min-h-11 items-center justify-center rounded-control bg-accent px-5 text-base font-bold text-on-accent disabled:opacity-60"
            >
              {save.isPending ? 'Adding…' : saveLabel(p.customer.ownerName)}
            </button>
            <Link to={back} className={BTN2}>
              Discard
            </Link>
          </div>
        </aside>
      </div>
    </>
  );
}
