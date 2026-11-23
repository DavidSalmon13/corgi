import { forwardRef } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../../api/client';
import type { ValidationBody } from '../../api/types';
import { Glossed } from '../../components/Glossed';

// Pieces shared by B4's tabs (Add cover, Correct): the field styles, 422 handling, the error summary and the
// "can't do that here" card.

export const INPUT = 'min-h-11 w-full rounded-control border-[1.5px] bg-card px-3 py-2.5 text-base text-ink';
export const HINT = 'text-[13px] leading-snug text-ink-2';
export const BTN =
  'inline-flex min-h-11 items-center justify-center rounded-control bg-accent px-5 text-base font-bold text-on-accent disabled:opacity-60';
export const BTN2 =
  'inline-flex min-h-11 items-center justify-center rounded-control border-[1.5px] border-line px-[18px] text-base font-semibold text-ink no-underline';

/** What B3 shows after a save; PolicyWorkspace reads it from router state. {@code refs}: the ids the save created. */
export type ChangeSavedState = { banner: string; refs?: string };

export type FormErrors<F extends string> = { message: string; fields: Partial<Record<F, string>> };

export function validationOf(e: unknown): ValidationBody | null {
  return e instanceof ApiError && e.status === 422 ? (e.body as ValidationBody) : null;
}

/**
 * The summary above the form after a failed save: the message, then each field to fix as a link to that field.
 * A policy-wide problem has no field to jump to, so it is plain text. Gone once every listed field is fixed.
 */
export const ErrorSummary = forwardRef<HTMLDivElement, { errors: FormErrors<string> | null }>(function ErrorSummary(
  { errors },
  ref,
) {
  if (!errors) return null;
  const listed = Object.entries(errors.fields).filter(([, m]) => !!m);
  const hadFieldErrors = Object.keys(errors.fields).length > 0;
  if (hadFieldErrors && listed.length === 0) return null;
  return (
    <div ref={ref} tabIndex={-1} role="alert" className="rounded-card bg-bad-soft px-4 py-3.5 font-semibold text-bad">
      {errors.message}
      {listed.length > 0 && (
        <ul className="mt-1.5 list-disc pl-5 font-normal">
          {listed.map(([f, m]) => (
            <li key={f}>
              {f === 'policy' ? (
                <Glossed>{m ?? ''}</Glossed>
              ) : (
                // A link can't hold the "?" buttons, so a field message here stays plain; the same words appear
                // under the field itself, explained.
                <a href={`#${f}`} className="text-bad underline">
                  {m}
                </a>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
});

/** A tab whose action can't be used on this policy right now, and why. */
export function BlockedCard({ reason, back }: { reason: string; back: string }) {
  return (
    <div className="flex flex-col items-start gap-3 rounded-2xl border border-line bg-card p-5 md:p-6">
      <p className="m-0 text-[17px]">
        <Glossed>{reason}</Glossed>
      </p>
      <Link to={back} className={BTN2}>
        Back to policy
      </Link>
    </div>
  );
}

/** The message under a field, tied to it with aria-describedby through {@code id}. */
export function FieldError({ id, message }: { id: string; message: string | undefined }) {
  return message ? (
    <span id={id} className="text-sm font-semibold text-bad">
      <Glossed>{message}</Glossed>
    </span>
  ) : null;
}
