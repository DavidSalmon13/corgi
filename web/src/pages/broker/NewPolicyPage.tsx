import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router';
import { api, ApiError } from '../../api/client';
import type { CustomerRow, IssueForCustomerRequest, IssuePlan, IssueRequest, Issued, PolicyDetail, ValidationBody } from '../../api/types';
import { Money } from '../../components/Money';
import { Glossed } from '../../components/Glossed';
import { Term } from '../../components/Term';
import { GLOSSARY } from '../../lib/glossary';
import { daysBetween, formatDate } from '../../lib/dates';
import { customerWillSee } from '../../lib/issueCopy';
import { formatCents } from '../../lib/money';
import { rewriteNote } from '../../lib/policiesCopy';
import { useDebounced } from '../../lib/useDebounced';

type Field = 'legalName' | 'businessDescription' | 'ownerName' | 'ownerEmail' | 'startDate' | 'replaces';
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
      {props.disabled ? (
        // Always included: shown ticked in amber as in the design, not as a greyed-out disabled box.
        <span aria-hidden="true" className="mt-0.5 flex size-5 flex-none items-center justify-center rounded-[4px] bg-accent text-on-accent">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3.5" strokeLinecap="round" strokeLinejoin="round">
            <path d="M5 12l5 5L20 7" />
          </svg>
        </span>
      ) : (
        <input
          type="checkbox"
          checked={props.checked}
          onChange={(e) => props.onChange?.(e.target.checked)}
          className="mt-0.5 size-5 flex-none accent-accent"
        />
      )}
      <span className="min-w-0 flex-1">
        <span className="font-bold">{props.title}</span>
        {/* Phones: price under the title so the description keeps the full width. */}
        <span className="block font-semibold tabular-nums sm:hidden">
          <Money cents={props.cents} /> a year
        </span>
        <span className="block text-sm text-ink-2">
          <Glossed>{props.description}</Glossed>
        </span>
      </span>
      <span className="hidden font-semibold tabular-nums whitespace-nowrap sm:block">
        <Money cents={props.cents} /> a year
      </span>
    </label>
  );
}

/** Whether the replaced policy still had product liability when it was cancelled, so its rewrite starts with it. */
function hadProductLiability(p: PolicyDetail): boolean {
  return p.coverages.some(
    (c) => c.kind === 'PRODUCT_LIABILITY' && (!c.endedOn || (p.cancelledOn != null && c.endedOn >= p.cancelledOn)),
  );
}

/** One read-only fact about the customer on B2 in customer mode. */
function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-[1_1_200px] flex-col gap-0.5">
      <dt className="text-sm font-semibold text-ink-2">{label}</dt>
      <dd className="m-0 break-words text-base font-semibold">{children}</dd>
    </div>
  );
}

/**
 * B2: issue a policy, with the price shown live before anything is saved. At /broker/policies/new it creates the
 * customer too. At /broker/customers/:customerId/policies/new (customer mode) the customer is read-only and only the
 * coverage is asked; with ?replaces=:policyId it is the rewrite of that cancelled policy.
 */
export function NewPolicyPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const summaryRef = useRef<HTMLDivElement>(null);
  const params = useParams();
  const [search] = useSearchParams();
  const customerId = params.customerId ? Number(params.customerId) : null;
  const replacesId = customerId != null && search.get('replaces') ? Number(search.get('replaces')) : null;

  const customer = useQuery({
    queryKey: ['customer', customerId],
    queryFn: () => api<CustomerRow>(`/api/broker/customers/${customerId}`),
    enabled: customerId != null,
  });
  const replaced = useQuery({
    queryKey: ['policy', String(replacesId)],
    queryFn: () => api<PolicyDetail>(`/api/broker/policies/${replacesId}`),
    enabled: replacesId != null,
  });

  const [form, setForm] = useState<Form>({
    legalName: '',
    businessDescription: '',
    ownerName: '',
    ownerEmail: '',
    startDate: todayIso(),
  });
  const [extra, setExtra] = useState(false);
  // A rewrite starts with the cover the cancelled policy had; the broker can still change it.
  const prefilled = useRef(false);
  useEffect(() => {
    if (replaced.data && !prefilled.current) {
      prefilled.current = true;
      setExtra(hadProductLiability(replaced.data));
    }
  }, [replaced.data]);
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
    queryKey: ['issue-preview', customerId, replacesId, pricedStart, pricedExtra],
    queryFn: () =>
      customerId != null
        ? api<IssuePlan>(`/api/broker/customers/${customerId}/policies/preview`, {
            method: 'POST',
            body: JSON.stringify({ startDate: pricedStart, productLiability: pricedExtra, replacesPolicyId: replacesId } satisfies IssueForCustomerRequest),
          })
        : api<IssuePlan>('/api/broker/policies/preview', {
            method: 'POST',
            body: JSON.stringify({ startDate: pricedStart, productLiability: pricedExtra }),
          }),
    enabled: pricedStart !== '',
    placeholderData: keepPreviousData,
    retry: false,
  });
  const previewProblem = validationOf(preview.error)?.fields.startDate;
  // A rewrite the rules refuse (not cancelled, already replaced…) shows under the note, not as a pricing failure.
  const previewReplaces = validationOf(preview.error)?.fields.replaces;
  const previewFailed = preview.isError && !previewProblem && !previewReplaces;
  const plan = form.startDate && !preview.isError ? preview.data : undefined;
  // True while the panel may not yet match the inputs, so Create can't issue a price the broker hasn't seen.
  const repricing = form.startDate !== '' && (pricedStart !== form.startDate || pricedExtra !== extra || preview.isFetching);

  const create = useMutation({
    mutationFn: () =>
      customerId != null
        ? api<Issued>(`/api/broker/customers/${customerId}/policies`, {
            method: 'POST',
            body: JSON.stringify({ startDate: form.startDate, productLiability: extra, replacesPolicyId: replacesId } satisfies IssueForCustomerRequest),
          })
        : api<Issued>('/api/broker/policies', { method: 'POST', body: JSON.stringify({ ...form, productLiability: extra }) }),
    onSuccess: (issued) => {
      queryClient.invalidateQueries({ queryKey: ['policies'] });
      if (replacesId != null) queryClient.invalidateQueries({ queryKey: ['policy', String(replacesId)] });
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
  // Count what is still wrong, so the summary shrinks as fields are fixed and goes once they all are.
  const open = errors ? Object.values(errors.fields).filter(Boolean).length : 0;
  const hadFieldErrors = errors ? Object.keys(errors.fields).length > 0 : false;
  const summary = !errors ? null : hadFieldErrors ? (open === 0 ? null : `${open} ${open === 1 ? 'thing' : 'things'} to fix before you can save.`) : errors.message;
  const owner = (customer.data?.ownerName ?? form.ownerName).trim().split(/\s+/)[0];
  const period = plan ? `${formatDate(plan.start)} → ${formatDate(plan.end)}` : 'Starts on the effective date above';

  return (
    <>
      <div>
        <h1 className="m-0 text-[32px] font-bold tracking-[-0.015em]">
          {customerId == null ? 'New customer and policy' : replacesId != null ? 'Rewrite a policy' : 'New policy for this customer'}
        </h1>
        <p className="mt-1 text-base text-ink-2">The price updates as you type. Nothing is charged until the customer pays.</p>
      </div>

      {summary && errors && (
        <div ref={summaryRef} tabIndex={-1} role="alert" className="rounded-card bg-bad-soft px-4 py-3.5 font-semibold text-bad">
          {summary}
          {open > 0 && (
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
          {customerId != null && (
            <section aria-labelledby="customer-heading" className={CARD}>
              <h2 id="customer-heading" className="m-0 text-lg font-bold">
                The customer
              </h2>
              {customer.data ? (
                <dl className="m-0 flex flex-wrap gap-x-6 gap-y-3">
                  <Fact label="Business">{customer.data.legalName}</Fact>
                  <Fact label="Owner">{customer.data.ownerName}</Fact>
                  <Fact label="Email for receipts">{customer.data.ownerEmail}</Fact>
                </dl>
              ) : (
                <p className="m-0 text-ink-2">{customer.isError ? "We couldn't find this customer." : 'Loading the customer…'}</p>
              )}
              {replacesId != null && (
                <div id="replaces" tabIndex={-1} className="flex flex-col gap-1 rounded-card bg-bg px-4 py-3.5">
                  <span className="text-xs font-semibold uppercase tracking-[.06em] text-ink-2">
                    <Term explain={GLOSSARY.rewrite}>Rewrite</Term>
                  </span>
                  <p className="m-0 text-base font-medium">
                    {replaced.data
                      ? replaced.data.cancelledOn
                        ? rewriteNote(replaced.data.policyNumber, replaced.data.cancelledOn)
                        : `Replaces ${replaced.data.policyNumber}`
                      : 'Loading the policy it replaces…'}
                  </p>
                  {(fieldError('replaces') ?? previewReplaces) && (
                    <span className="text-sm font-semibold text-bad">{fieldError('replaces') ?? previewReplaces}</span>
                  )}
                </div>
              )}
            </section>
          )}

          {customerId == null && (
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
          )}

          <fieldset className={CARD}>
            <legend className={LEGEND}>The coverage</legend>
            <div className="flex flex-wrap gap-4">
              <TextField
                id="startDate"
                type="date"
                label={<Term explain={GLOSSARY.effectiveDate}>Effective date</Term>}
                value={form.startDate}
                error={fieldError('startDate') ?? previewProblem}
                onChange={set('startDate')}
              />
              <div className="flex min-w-0 flex-[1_1_240px] flex-col gap-1.5">
                <span className="text-sm font-semibold">
                  <Term explain={GLOSSARY.policyPeriod}>Policy period</Term>
                </span>
                <div className={`${INPUT} flex items-center border-transparent bg-track tabular-nums`}>
                  <span>
                    <Glossed>{period}</Glossed>
                  </span>
                </div>
                <span className="text-[13px] text-ink-2">One year. The end date isn't counted.</span>
              </div>
            </div>
            <CoverChoice
              title={<Term explain={GLOSSARY.generalLiability}>General liability</Term>}
              description="Pays if someone is hurt or their property is damaged because of the business. Always included."
              cents={BASE_CENTS}
              checked
              disabled
            />
            <CoverChoice
              title={<Term explain={GLOSSARY.productLiability}>Product liability</Term>}
              description="Pays if something the business sells hurts someone. Can also be added later by endorsement."
              cents={EXTRA_CENTS}
              checked={extra}
              onChange={setExtra}
            />
          </fieldset>
        </form>

        <aside aria-label="Price preview" aria-live="polite" className="flex min-w-0 flex-[1_1_360px] flex-col gap-3.5 rounded-2xl border-[1.5px] border-accent bg-card p-5 md:p-6">
          <h2 className="m-0 text-xl font-bold">
            <Term explain={GLOSSARY.premium}>Premium</Term>
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
                      <div className="font-semibold">
                        <Glossed>{l.description}</Glossed>
                      </div>
                      <div className="text-[13px] text-ink-2 tabular-nums">
                        {formatCents(l.calc.annualCents)} × {l.calc.days} ÷ {l.calc.termDays} ·{' '}
                        <Term explain={GLOSSARY.proRata}>pro-rata</Term>
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
                  <Glossed>
                    {customerWillSee({ start: plan.start, end: plan.end, totalCents: plan.totalCents, extra: plan.lines.some((l) => l.kind !== 'BASE') })}
                  </Glossed>
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
              ) : previewReplaces ? (
                `${previewReplaces} Pick another policy to rewrite.`
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
              {create.isPending
                ? 'Creating…'
                : customerId != null
                  ? replacesId != null
                    ? 'Create the rewrite'
                    : 'Create policy'
                  : owner
                    ? `Create policy and invite ${owner}`
                    : 'Create policy'}
            </button>
            <Link
              to={replacesId != null ? `/broker/policies/${replacesId}` : '/broker/policies'}
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
