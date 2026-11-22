import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router';
import { api, ApiError } from '../../api/client';
import type { IssuePlan, IssueRequest, Issued, ValidationBody } from '../../api/types';
import { Money } from '../../components/Money';
import { Term } from '../../components/Term';
import { daysBetween, formatDate } from '../../lib/dates';
import { customerWillSee } from '../../lib/issueCopy';
import { formatCents } from '../../lib/money';
import { useDebounced } from '../../lib/useDebounced';

type Field = 'legalName' | 'businessDescription' | 'ownerName' | 'ownerEmail' | 'startDate';
type Form = Omit<IssueRequest, 'productLiability'>;

const BASE_CENTS = 120_000;
const EXTRA_CENTS = 73_000;

function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function validationOf(e: unknown): ValidationBody | null {
  return e instanceof ApiError && e.status === 422 ? (e.body as ValidationBody) : null;
}

const INPUT =
  'min-h-11 w-full rounded-control border-[1.5px] bg-card px-3 py-2.5 text-base text-ink placeholder:text-ink-2/70';
const CARD = 'm-0 flex min-w-0 flex-col gap-4 rounded-2xl border border-line bg-card p-5 md:p-6';
const LEGEND = 'px-1.5 text-lg font-bold';

function TextField(props: {
  id: Field;
  label: ReactNode;
  value: string;
  error?: string;
  type?: string;
  autoComplete?: string;
  onChange: (v: string) => void;
}) {
  const errId = `${props.id}-error`;
  return (
    <div className="flex min-w-0 flex-[1_1_240px] flex-col gap-1.5">
      <label htmlFor={props.id} className="text-sm font-semibold">
        {props.label}
      </label>
      <input
        id={props.id}
        type={props.type ?? 'text'}
        value={props.value}
        autoComplete={props.autoComplete}
        aria-invalid={!!props.error}
        aria-describedby={props.error ? errId : undefined}
        onChange={(e) => props.onChange(e.target.value)}
        className={`${INPUT} ${props.error ? 'border-bad' : 'border-line'}`}
      />
      {props.error && (
        <span id={errId} className="text-sm font-semibold text-bad">
          {props.error}
        </span>
      )}
    </div>
  );
}

function CoverChoice(props: {
  title: ReactNode;
  description: string;
  cents: number;
  checked: boolean;
  disabled?: boolean;
  onChange?: (v: boolean) => void;
}) {
  return (
    <label
      className={`flex items-start gap-3 rounded-card border-[1.5px] p-3.5 ${props.checked ? 'border-accent' : 'border-line'} ${props.disabled ? '' : 'cursor-pointer'}`}
    >
      <input
        type="checkbox"
        checked={props.checked}
        disabled={props.disabled}
        onChange={(e) => props.onChange?.(e.target.checked)}
        className="mt-0.5 size-5 flex-none accent-accent"
      />
      <span className="min-w-0 flex-1">
        <span className="font-bold">{props.title}</span>
        <span className="block text-sm text-ink-2">{props.description}</span>
      </span>
      <span className="font-semibold tabular-nums whitespace-nowrap">
        <Money cents={props.cents} /> a year
      </span>
    </label>
  );
}

/** B2: create a customer and issue their policy, with the price shown live before anything is saved. */
export function NewPolicyPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const summaryRef = useRef<HTMLDivElement>(null);

  const [form, setForm] = useState<Form>({
    legalName: '',
    businessDescription: '',
    ownerName: '',
    ownerEmail: '',
    startDate: todayIso(),
  });
  const [extra, setExtra] = useState(false);
  const [errors, setErrors] = useState<{ message: string; fields: Partial<Record<Field, string>> } | null>(null);
  // Bumped once per failed save, so focus moves to the summary then — not again while fields are being fixed.
  const [failedSaves, setFailedSaves] = useState(0);

  const set = (field: Field) => (value: string) => {
    setForm((f) => ({ ...f, [field]: value }));
    // Once fixed, a field's message goes; the summary stays until the next try.
    setErrors((e) => (e && e.fields[field] ? { ...e, fields: { ...e.fields, [field]: undefined } } : e));
  };

  // Debounce the two primitives, not an object literal: a fresh object every render would never settle.
  const pricedStart = useDebounced(form.startDate, 250);
  const pricedExtra = useDebounced(extra, 250);
  const preview = useQuery({
    queryKey: ['issue-preview', pricedStart, pricedExtra],
    queryFn: () =>
      api<IssuePlan>('/api/broker/policies/preview', {
        method: 'POST',
        body: JSON.stringify({ startDate: pricedStart, productLiability: pricedExtra }),
      }),
    enabled: pricedStart !== '',
    placeholderData: keepPreviousData,
    retry: false,
  });
  const previewProblem = validationOf(preview.error)?.fields.startDate;
  const previewFailed = preview.isError && !previewProblem;
  const plan = form.startDate && !preview.isError ? preview.data : undefined;
  // True while the panel may not yet match the inputs, so Create can't issue a price the broker hasn't seen.
  const repricing = form.startDate !== '' && (pricedStart !== form.startDate || pricedExtra !== extra || preview.isFetching);

  const create = useMutation({
    mutationFn: () => api<Issued>('/api/broker/policies', { method: 'POST', body: JSON.stringify({ ...form, productLiability: extra }) }),
    onSuccess: (issued) => {
      queryClient.invalidateQueries({ queryKey: ['policies'] });
      queryClient.invalidateQueries({ queryKey: ['demo-users'] });
      navigate(`/broker/policies/${issued.policyId}`);
    },
    onError: (e) => {
      const v = validationOf(e);
      setErrors(v ? { message: v.message, fields: v.fields as Partial<Record<Field, string>> } : { message: "We couldn't save the policy. Try again.", fields: {} });
      setFailedSaves((n) => n + 1);
    },
  });

  useEffect(() => {
    if (failedSaves > 0) summaryRef.current?.focus();
  }, [failedSaves]);

  const fieldError = (f: Field) => errors?.fields[f];
  const owner = form.ownerName.trim().split(/\s+/)[0];
  const period = plan ? `${formatDate(plan.start)} → ${formatDate(plan.end)}` : 'Starts on the date above';

  return (
    <>
      <div>
        <h1 className="m-0 text-[32px] font-bold tracking-[-0.015em]">New customer and policy</h1>
        <p className="mt-1 text-base text-ink-2">The price updates as you type. Nothing is charged until the customer pays.</p>
      </div>

      {errors && (
        <div ref={summaryRef} tabIndex={-1} role="alert" className="rounded-card bg-bad-soft px-4 py-3.5 font-semibold text-bad">
          {errors.message}
          {Object.entries(errors.fields).some(([, m]) => m) && (
            <ul className="mt-1.5 list-disc pl-5 font-normal">
              {Object.entries(errors.fields).map(([f, m]) =>
                m ? (
                  <li key={f}>
                    <a href={`#${f}`} className="text-bad underline">
                      {m}
                    </a>
                  </li>
                ) : null,
              )}
            </ul>
          )}
        </div>
      )}

      <div className="flex flex-wrap items-start gap-6">
        <form
          id="new-policy"
          noValidate
          onSubmit={(e) => {
            e.preventDefault();
            create.mutate();
          }}
          className="flex min-w-0 flex-[1_1_520px] flex-col gap-5"
        >
          <fieldset className={CARD}>
            <legend className={LEGEND}>The business</legend>
            <div className="flex flex-wrap gap-4">
              <TextField id="legalName" label="Legal business name" value={form.legalName} error={fieldError('legalName')} autoComplete="organization" onChange={set('legalName')} />
              <TextField id="businessDescription" label="What the business does" value={form.businessDescription} error={fieldError('businessDescription')} onChange={set('businessDescription')} />
            </div>
            <div className="flex flex-wrap gap-4">
              <TextField id="ownerName" label="Owner's name" value={form.ownerName} error={fieldError('ownerName')} autoComplete="name" onChange={set('ownerName')} />
              <TextField id="ownerEmail" type="email" label="Email for receipts" value={form.ownerEmail} error={fieldError('ownerEmail')} autoComplete="email" onChange={set('ownerEmail')} />
            </div>
          </fieldset>

          <fieldset className={CARD}>
            <legend className={LEGEND}>The cover</legend>
            <div className="flex flex-wrap gap-4">
              <TextField
                id="startDate"
                type="date"
                label={<Term explain="The first day this cover protects the customer.">Starts on</Term>}
                value={form.startDate}
                error={fieldError('startDate') ?? previewProblem}
                onChange={set('startDate')}
              />
              <div className="flex min-w-0 flex-[1_1_240px] flex-col gap-1.5">
                <span className="text-sm font-semibold">
                  <Term explain="The dates the policy runs.">Cover period</Term>
                </span>
                <div className={`${INPUT} flex items-center border-transparent bg-track tabular-nums`}>{period}</div>
                <span className="text-[13px] text-ink-2">One year. The end date isn't counted.</span>
              </div>
            </div>
            <CoverChoice
              title={<Term explain="General Liability: pays if someone is hurt or their property is damaged because of the business.">Base cover</Term>}
              description="Pays if someone is hurt or their property is damaged because of the business. Always included."
              cents={BASE_CENTS}
              checked
              disabled
            />
            <CoverChoice
              title="Extra cover: product liability"
              description="Pays if something the business sells hurts someone. Can also be added later."
              cents={EXTRA_CENTS}
              checked={extra}
              onChange={setExtra}
            />
          </fieldset>
        </form>

        <aside aria-label="Price preview" aria-live="polite" className="flex min-w-0 flex-[1_1_360px] flex-col gap-3.5 rounded-2xl border-[1.5px] border-accent bg-card p-5 md:p-6">
          <h2 className="m-0 text-xl font-bold">
            <Term explain="Premium: what the customer pays for a year of protection.">Price of the cover</Term>
          </h2>
          {plan ? (
            <div className={`flex flex-col gap-3.5 ${repricing ? 'opacity-70' : ''}`}>
              <div className="text-[36px] font-bold leading-tight tracking-[-0.02em] text-accent-ink tabular-nums">
                {formatCents(plan.totalCents)}{' '}
                <span className="text-lg font-semibold text-ink">for {daysBetween(plan.start, plan.end)} days</span>
              </div>
              <div>
                {plan.lines.map((l) => (
                  <div key={l.kind} className="flex justify-between gap-4 border-t border-dashed border-line py-3">
                    <div>
                      <div className="font-semibold">{l.description}</div>
                      <div className="text-[13px] text-ink-2 tabular-nums">
                        {formatCents(l.calc.annualCents)} × {l.calc.days} ÷ {l.calc.termDays}
                      </div>
                    </div>
                    <Money cents={l.calc.amountCents} signed className="font-semibold" />
                  </div>
                ))}
                <div className="flex justify-between gap-4 border-t-[1.5px] border-ink py-3 font-bold">
                  <span>Customer pays</span>
                  <Money cents={plan.totalCents} />
                </div>
              </div>
              <div className="flex flex-col gap-1.5 rounded-card bg-bg px-4 py-3.5">
                <span className="text-xs font-semibold uppercase tracking-[.06em] text-ink-2">Customer will see</span>
                <p className="m-0 text-base font-medium leading-normal">
                  {customerWillSee({ start: plan.start, end: plan.end, totalCents: plan.totalCents, extra: plan.lines.some((l) => l.kind !== 'BASE') })}
                </p>
              </div>
            </div>
          ) : (
            <p className="m-0 text-base leading-normal text-ink-2">
              {previewFailed && form.startDate ? (
                <>
                  We couldn't work out the price just now.{' '}
                  <button type="button" onClick={() => preview.refetch()} className="font-semibold text-ink underline">
                    Try again
                  </button>
                </>
              ) : previewProblem && form.startDate ? (
                `${previewProblem} We'll show the price here, before anything is saved.`
              ) : (
                "Add a start date and we'll show the price here, before anything is saved."
              )}
            </p>
          )}
          <div className="mt-1 flex flex-wrap gap-2.5">
            <button
              type="submit"
              form="new-policy"
              disabled={create.isPending || repricing}
              className="inline-flex min-h-11 items-center justify-center rounded-control bg-accent px-5 text-base font-bold text-on-accent disabled:opacity-60"
            >
              {create.isPending ? 'Creating…' : owner ? `Create policy and invite ${owner}` : 'Create policy'}
            </button>
            <Link
              to="/broker/policies"
              className="inline-flex min-h-11 items-center justify-center rounded-control border-[1.5px] border-line px-[18px] text-base font-semibold text-ink no-underline"
            >
              Cancel
            </Link>
          </div>
        </aside>
      </div>
    </>
  );
}
