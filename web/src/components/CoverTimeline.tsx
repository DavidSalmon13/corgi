import type { ReactNode } from 'react';
import type { CoverKind } from '../api/types';
import { formatDate, formatShortDate, type IsoDate } from '../lib/dates';
import { monthTicks, pct, positionPct, spanPct } from '../lib/timeline';

export type TimelineCover = {
  id: number | string;
  kind: CoverKind;
  label: ReactNode;
  /** Shown under the label on the full timeline, e.g. "$1,200.00 a year". */
  detail?: ReactNode;
  from: IsoDate;
  to: IsoDate;
  /** Written inside the bar on the full timeline, e.g. "from Oct 8 · was Oct 15" after a correction. */
  barLabel?: string;
};

type Props = {
  start: IsoDate;
  end: IsoDate;
  today: IsoDate;
  covers: TimelineCover[];
  /** Cover stops on this date; the rest of the period is hatched. */
  cancelledFrom?: IsoDate;
  /** `full` is the broker workspace (B3); `compact` is the policyholder home (P1). */
  variant?: 'full' | 'compact';
};

const HATCH = { backgroundImage: 'repeating-linear-gradient(135deg, var(--pl-line) 0 2px, transparent 2px 7px)' };

function barColor(kind: CoverKind, variant: 'full' | 'compact'): string {
  if (kind !== 'BASE') return 'bg-accent';
  return variant === 'full' ? 'bg-cover-base' : 'bg-ink/85';
}

/** Plain-text version of the bars for screen readers. */
function Summary({ covers, today }: { covers: TimelineCover[]; today: IsoDate }) {
  return (
    <ul className="sr-only">
      {covers.map((c) => (
        <li key={c.id}>
          {c.label}: {formatDate(c.from)} to {formatDate(c.to)}
          {c.barLabel?.includes(' · ') && ` (${c.barLabel.split(' · ').slice(1).join(', ')})`}
        </li>
      ))}
      <li>Today is {formatDate(today)}</li>
    </ul>
  );
}

/** One bar per cover across the cover period, with a marker for today. */
export function CoverTimeline({ start, end, today, covers, cancelledFrom, variant = 'full' }: Props) {
  const todayPct = positionPct(start, end, today);
  const showToday = today >= start && today <= end;
  const hatchLeft = cancelledFrom ? positionPct(start, end, cancelledFrom) : null;

  if (variant === 'compact') {
    return (
      <div className="flex flex-col gap-1.5">
        <Summary covers={covers} today={today} />
        <div aria-hidden="true" className="flex flex-col gap-1.5">
          {covers.map((c) => {
            const s = spanPct(start, end, c.from, c.to);
            return (
              <div key={c.id} className="relative h-3 overflow-hidden rounded-md bg-track">
                <div
                  className={`absolute inset-y-0 ${barColor(c.kind, 'compact')}`}
                  style={{ left: pct(s.left), width: pct(s.width) }}
                />
                {hatchLeft != null && <div className="absolute inset-y-0 right-0" style={{ left: pct(hatchLeft), ...HATCH }} />}
              </div>
            );
          })}
          <div className="relative h-[18px] text-xs text-ink-2 tabular-nums">
            {/* Near an edge, "Today" stands in for that edge's date rather than overlapping it. */}
            {!(showToday && todayPct < 15) && <span className="absolute left-0">{formatShortDate(start)}</span>}
            {showToday && (
              <span
                className="absolute -translate-x-1/2 font-semibold text-ink"
                style={{ left: pct(Math.min(94, Math.max(6, todayPct))) }}
              >
                Today
              </span>
            )}
            {!(showToday && todayPct > 85) && <span className="absolute right-0">{formatShortDate(end)}</span>}
          </div>
        </div>
      </div>
    );
  }

  const ticks = monthTicks(start, end);
  return (
    <div className="grid grid-cols-[minmax(96px,160px)_minmax(0,1fr)] items-center gap-x-4 gap-y-2.5">
      <Summary covers={covers} today={today} />
      <span aria-hidden="true" />
      <div aria-hidden="true" className="relative h-[18px]">
        {ticks.map((t, i) => (
          <span
            key={`${t.label}-${i}`}
            className={`absolute top-0 text-xs text-ink-2 ${i % 2 === 1 ? 'max-md:hidden' : ''}`}
            style={{ left: pct(t.pct) }}
          >
            {t.label}
          </span>
        ))}
      </div>
      {covers.map((c) => {
        const s = spanPct(start, end, c.from, c.to);
        return (
          <div key={c.id} className="contents">
            <span className="text-[15px] font-semibold">
              {c.label}
              {c.detail && <span className="block text-[13px] font-normal text-ink-2 tabular-nums">{c.detail}</span>}
            </span>
            <div aria-hidden="true" className="relative h-8 rounded-lg bg-track">
              <div
                className={`absolute inset-y-0 flex items-center overflow-hidden whitespace-nowrap rounded-lg px-2.5 text-[13px] font-semibold text-on-accent ${barColor(c.kind, 'full')}`}
                style={{ left: pct(s.left), width: pct(s.width) }}
              >
                {c.barLabel}
              </div>
              {hatchLeft != null && (
                <div className="absolute inset-y-0 right-0 rounded-r-lg" style={{ left: pct(hatchLeft), ...HATCH }} />
              )}
              {showToday && (
                <div className="absolute -inset-y-1.5 w-0.5 bg-ink" style={{ left: pct(todayPct) }} />
              )}
            </div>
          </div>
        );
      })}
      {showToday && (
        <>
          <span aria-hidden="true" />
          <div aria-hidden="true" className="relative h-[18px]">
            <span
              className="absolute -translate-x-1/2 whitespace-nowrap text-[13px] font-bold"
              style={{ left: pct(Math.min(90, Math.max(10, todayPct))) }}
            >
              Today, {formatShortDate(today)}
            </span>
          </div>
        </>
      )}
    </div>
  );
}
