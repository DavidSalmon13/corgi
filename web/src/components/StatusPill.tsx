import type { ReactNode } from 'react';

export type PillTone = 'good' | 'wait' | 'bad' | 'neutral';

const TONES: Record<PillTone, { pill: string; dot: string }> = {
  good: { pill: 'bg-good-soft text-good', dot: 'bg-good' },
  wait: { pill: 'bg-wait-soft text-wait', dot: 'bg-accent' },
  bad: { pill: 'bg-bad-soft text-bad', dot: 'bg-bad' },
  neutral: { pill: 'bg-track text-ink-2', dot: 'bg-ink-2' },
};

type Props = {
  tone: PillTone;
  /** A leading dot, used on the policyholder screens for money that is still moving. */
  dot?: boolean;
  children: ReactNode;
};

/** A short status in words, never colour alone: "Active · balance due", "Waiting for bank". */
export function StatusPill({ tone, dot = false, children }: Props) {
  const t = TONES[tone];
  return (
    <span
      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-[9px] py-0.5 text-[13px] font-semibold ${t.pill}`}
    >
      {dot && <span aria-hidden="true" className={`size-[7px] rounded-full ${t.dot}`} />}
      {children}
    </span>
  );
}
