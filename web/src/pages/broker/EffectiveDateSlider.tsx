import { useRef, useState, type KeyboardEvent, type PointerEvent } from 'react';
import { Term } from '../../components/Term';
import { daysBetween, formatDate, formatShortDate, type IsoDate } from '../../lib/dates';
import { coveredDaysLine, dayAtFraction, sliderValueText, stepDate } from '../../lib/dateSlider';
import { GLOSSARY } from '../../lib/glossary';
import { monthTicks, pct, positionPct, spanPct } from '../../lib/timeline';
import { HINT } from './changeForm';

type Props = {
  /** The policy period the bar spans, [start, end). */
  start: IsoDate;
  end: IsoDate;
  /** Where the extra cover starts now, drawn as a dashed outline. */
  currentFrom: IsoDate;
  /** The corrected effective date, shared with the date field; '' until one is picked. */
  value: IsoDate | '';
  /** The same limits as the date field. */
  min: IsoDate;
  max: IsoDate;
  invalid?: boolean;
  /** Every day the handle passes while dragging. Cheap: nothing is priced yet. */
  onMove: (date: IsoDate) => void;
  /** The day the handle lands on (a release, a tap or a key), which the form prices. */
  onCommit: (date: IsoDate) => void;
};

/**
 * AC15: the policy period as a bar, with a handle on the corrected effective date. The handle and the date field
 * hold one value, so a broker can drag roughly and type the exact day. A drag only reports its day as it goes; the
 * money is worked out once, when the handle is let go.
 */
export function EffectiveDateSlider({ start, end, currentFrom, value, min, max, invalid, onMove, onCommit }: Props) {
  const trackRef = useRef<HTMLDivElement>(null);
  const handleRef = useRef<HTMLDivElement>(null);
  const [dragging, setDragging] = useState(false);
  const last = useRef<IsoDate | null>(null);

  const at = value || currentFrom;
  const handlePct = positionPct(start, end, at);
  const now = spanPct(start, end, currentFrom, end);
  const covered = spanPct(start, end, at, end);

  const dayAt = (clientX: number): IsoDate => {
    const rect = trackRef.current!.getBoundingClientRect();
    return dayAtFraction(start, end, min, max, (clientX - rect.left) / rect.width);
  };

  const move = (clientX: number) => {
    const day = dayAt(clientX);
    if (day === last.current) return;
    last.current = day;
    onMove(day);
  };

  const onPointerDown = (e: PointerEvent<HTMLDivElement>) => {
    if (e.button !== 0) return;
    e.preventDefault();
    e.currentTarget.setPointerCapture(e.pointerId);
    handleRef.current?.focus({ preventScroll: true });
    last.current = null;
    setDragging(true);
    move(e.clientX);
  };

  const finish = () => {
    if (!dragging) return;
    setDragging(false);
    if (last.current) onCommit(last.current);
    last.current = null;
  };

  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const next = stepDate(at, e.key, min, max);
    if (!next) return;
    e.preventDefault();
    onCommit(next);
  };

  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-baseline justify-between gap-3">
        <span className="text-sm font-semibold">
          Drag to the correct <Term explain={GLOSSARY.effectiveDate}>effective date</Term>
        </span>
        <span className="text-[13px] tabular-nums text-ink-2">
          <Term explain={GLOSSARY.policyPeriod}>Policy period</Term> {formatShortDate(start)} → {formatDate(end)}
        </span>
      </div>

      <div aria-hidden="true" className="relative h-[18px]">
        {monthTicks(start, end).map((t, i) => (
          <span key={`${t.label}-${i}`} className="absolute top-0 -translate-x-px text-xs text-ink-2" style={{ left: pct(t.pct) }}>
            {t.label.charAt(0)}
          </span>
        ))}
      </div>

      <div
        ref={trackRef}
        onPointerDown={onPointerDown}
        onPointerMove={(e) => dragging && move(e.clientX)}
        onPointerUp={finish}
        onPointerCancel={finish}
        onLostPointerCapture={finish}
        className={`relative h-11 cursor-pointer touch-none select-none rounded-[10px] bg-track ${dragging ? 'cursor-grabbing' : ''}`}
      >
        <div
          aria-hidden="true"
          className={`absolute inset-y-0 right-0 rounded-r-[10px] ${value ? (invalid ? 'bg-bad/70' : 'bg-accent') : 'bg-accent/35'} ${
            dragging ? '' : 'transition-[left] duration-150'
          }`}
          style={{ left: pct(covered.left) }}
        />
        {/* Where the cover starts now: a dashed outline, drawn over the fill so the move reads against it. */}
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-y-1 z-[5] rounded-lg border-[1.5px] border-dashed border-ink opacity-60"
          style={{ left: pct(now.left), width: pct(now.width) }}
        />
        <div
          ref={handleRef}
          role="slider"
          tabIndex={0}
          aria-label="Correct effective date"
          aria-valuemin={daysBetween(start, min)}
          aria-valuemax={daysBetween(start, max)}
          aria-valuenow={daysBetween(start, at)}
          aria-valuetext={sliderValueText(start, end, at)}
          aria-invalid={invalid || undefined}
          onKeyDown={onKeyDown}
          className={`absolute top-1/2 z-10 flex h-14 w-6 -translate-x-1/2 -translate-y-1/2 items-center justify-center gap-[3px] rounded-full border-2 border-ink bg-card shadow-[0_2px_6px_rgba(0,0,0,.18)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink ${
            dragging ? 'cursor-grabbing scale-110' : 'cursor-grab transition-[left] duration-150'
          }`}
          style={{ left: pct(handlePct) }}
        >
          <span aria-hidden="true" className="h-5 w-px bg-ink-2" />
          <span aria-hidden="true" className="h-5 w-px bg-ink-2" />
        </div>
      </div>

      {/* The day under the handle follows it, kept on the track near the edges. */}
      <div aria-hidden="true" className="relative h-7">
        <span
          className={`absolute top-1 -translate-x-1/2 whitespace-nowrap rounded-md px-1.5 py-0.5 text-sm font-bold tabular-nums ${
            value ? 'bg-ink text-bg' : 'text-ink-2'
          }`}
          style={{ left: pct(Math.min(92, Math.max(8, handlePct))) }}
        >
          {value ? formatShortDate(value) : `Now ${formatShortDate(currentFrom)}`}
        </span>
      </div>

      <p className={`${HINT} m-0 tabular-nums`}>
        {value ? (
          <>
            <Term explain={GLOSSARY.proRata}>Pro-rata</Term>: {coveredDaysLine(start, end, currentFrom, value)}
          </>
        ) : (
          'Drag the handle or tap the bar, or type the date below. Arrow keys move one day, Page Up and Down a month.'
        )}
      </p>
    </div>
  );
}
