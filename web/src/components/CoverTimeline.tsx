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
  /** `select` only: where the cover started before a correction, drawn as a dashed outline above its band. */
  ghostFrom?: IsoDate;
  /** `select` only: the caption inside that outline, e.g. "Before the fix: from Oct 15". */
  ghostLabel?: string;
  /** `select` only: what a screen reader says for the band's button, e.g. "Extra cover, Oct 8, 2026 to Sep 1, 2027". */
  accessibleLabel?: string;
};

type Props = {
  start: IsoDate;
  end: IsoDate;
  today: IsoDate;
  covers: TimelineCover[];
  /** Cover stops on this date; the rest of the period is hatched. */
  cancelledFrom?: IsoDate;
  /** `full` is the broker workspace (B3); `compact` is the policyholder home (P1); `select` is P2, with tappable bands. */
  variant?: 'full' | 'compact' | 'select';
  /** `select` only: the band drawn as chosen. */
  selectedId?: number | string;
  /** `select` only: called when a band is tapped or chosen with Enter or Space. */
  onSelect?: (id: number | string) => void;
};

/** The ring around the chosen band, as in P2. */
const RING = '0 0 0 3px var(--pl-card), 0 0 0 5px var(--pl-ink)';

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

/**
 * One bar per cover across the cover period, with a marker for today. On a cancelled policy each bar stops on the
 * cancel day and the unused days after it are striped.
 */
export function CoverTimeline({
  start,
  end,
  today,
  covers: given,
  cancelledFrom,
  variant = 'full',
  selectedId,
  onSelect,
}: Props) {
  const covers = cancelledFrom
    ? given.map((c) => ({ ...c, to: c.to > cancelledFrom ? cancelledFrom : c.to, from: c.from > cancelledFrom ? cancelledFrom : c.from }))
    : given;
  const todayPct = positionPct(start, end, today);
  const showToday = today >= start && today <= end;
  const hatchLeft = cancelledFrom ? positionPct(start, end, cancelledFrom) : null;

  if (variant === 'select') {
    return (
      <div>
        <div className="mb-2 flex justify-between text-[13px] font-semibold tabular-nums">
          <span>{formatDate(start)}</span>
          <span>{formatDate(end)}</span>
        </div>
        <div aria-hidden="true" className="relative h-[18px]">
          {monthTicks(start, end).map((t, i) => (
            <span key={`${t.label}-${i}`} className="absolute top-0 -translate-x-px text-xs text-ink-2" style={{ left: pct(t.pct) }}>
              {t.label.charAt(0)}
            </span>
          ))}
        </div>
        <div className="relative flex flex-col gap-2.5 pt-1.5 pb-1">
          {covers.map((c) => {
            const s = spanPct(start, end, c.from, c.to);
            const ghost = c.ghostFrom ? spanPct(start, end, c.ghostFrom, given.find((g) => g.id === c.id)?.to ?? end) : null;
            const chosen = c.id === selectedId;
            return (
              <div key={c.id} className="contents">
                {ghost && (
                  <div aria-hidden="true" className="relative h-[22px]">
                    <div
                      className="absolute inset-y-0 rounded-lg border-[1.5px] border-dashed border-ink-2 opacity-70"
                      style={{ left: pct(ghost.left), width: pct(ghost.width) }}
                    />
                    <span className="absolute top-0.5 text-xs text-ink-2" style={{ left: `calc(${pct(ghost.left)} + 8px)` }}>
                      {c.ghostLabel}
                    </span>
                  </div>
                )}
                <div className="relative h-11 rounded-[10px] bg-track">
                  {hatchLeft != null && (
                    <div aria-hidden="true" className="absolute inset-y-0 right-0 rounded-r-[10px]" style={{ left: pct(hatchLeft), ...HATCH }} />
                  )}
                  <button
                    type="button"
                    aria-pressed={chosen}
                    aria-label={c.accessibleLabel}
                    onClick={() => onSelect?.(c.id)}
                    className={`absolute inset-y-0 flex cursor-pointer items-center overflow-hidden whitespace-nowrap rounded-[10px] px-2.5 text-left text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ink ${
                      c.kind === 'BASE' ? 'bg-cover-base text-bg' : 'bg-accent text-on-accent'
                    }`}
                    style={{ left: pct(s.left), width: pct(s.width), boxShadow: chosen ? RING : 'none' }}
                  >
                    {c.label}
                  </button>
                </div>
              </div>
            );
          })}
          {showToday && (
            <div aria-hidden="true" className="absolute -top-1 -bottom-1 w-0.5 rounded-sm bg-ink" style={{ left: pct(todayPct) }} />
          )}
        </div>
        {showToday && (
          <div className="relative h-[34px]">
            <span
              className="absolute top-1.5 -translate-x-1/2 whitespace-nowrap text-[13px] font-bold"
              style={{ left: pct(Math.min(85, Math.max(15, todayPct))) }}
            >
              Today, {formatShortDate(today)}
            </span>
          </div>
        )}
      </div>
    );
  }

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
