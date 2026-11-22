import { useState, type ReactNode } from 'react';

const INPUT =
  'min-h-11 w-full rounded-control border-[1.5px] bg-card px-3 py-2.5 text-base text-ink placeholder:text-ink-2/70';

/**
 * A labelled input for the sign-in and sign-up forms, with an optional plain-language hint and a
 * field error. Password fields get a Show / Hide button so people can check what they typed.
 */
export function AuthField(props: {
  id: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  type?: 'email' | 'password';
  autoComplete?: string;
  hint?: string;
  error?: string;
  /** Shown after the error, e.g. a link to the fix. */
  errorAction?: ReactNode;
}) {
  const [shown, setShown] = useState(false);
  const password = props.type === 'password';
  const hintId = `${props.id}-hint`;
  const errId = `${props.id}-error`;
  const describedBy = [props.hint ? hintId : null, props.error ? errId : null].filter(Boolean).join(' ') || undefined;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={props.id} className="text-[15px] font-semibold">
        {props.label}
      </label>
      {props.hint && (
        <span id={hintId} className="text-sm text-ink-2">
          {props.hint}
        </span>
      )}
      <div className="relative">
        <input
          id={props.id}
          name={props.id}
          type={password && !shown ? 'password' : props.type === 'email' ? 'email' : 'text'}
          value={props.value}
          autoComplete={props.autoComplete}
          spellCheck={false}
          autoCapitalize="none"
          required
          aria-invalid={!!props.error}
          aria-describedby={describedBy}
          onChange={(e) => props.onChange(e.target.value)}
          className={`${INPUT} ${password ? 'pr-20' : ''} ${props.error ? 'border-bad' : 'border-line'}`}
        />
        {password && (
          <button
            type="button"
            onClick={() => setShown((s) => !s)}
            aria-pressed={shown}
            aria-label={shown ? `Hide ${props.label.toLowerCase()}` : `Show ${props.label.toLowerCase()}`}
            className="absolute inset-y-0 right-1 my-1 rounded-control px-3 text-sm font-semibold text-accent-ink hover:bg-accent-soft"
          >
            {shown ? 'Hide' : 'Show'}
          </button>
        )}
      </div>
      {props.error && (
        <span id={errId} className="text-sm font-semibold text-bad">
          {props.error} {props.errorAction}
        </span>
      )}
    </div>
  );
}
