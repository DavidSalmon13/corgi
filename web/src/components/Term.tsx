import { useEffect, useId, useRef, useState, type ReactNode } from 'react';

type Props = {
  /** The plain-language explanation, e.g. "The dates your policy runs." */
  explain: string;
  children: ReactNode;
};

/**
 * An insurance word with its meaning one tap away. Dotted underline as in the designs; hover shows the
 * native title on desktop, and a tap or keyboard focus opens a small bubble so it works on phones too.
 */
export function Term({ explain, children }: Props) {
  const [open, setOpen] = useState(false);
  const id = useId();
  const ref = useRef<HTMLSpanElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (e: Event) => {
      if (e instanceof KeyboardEvent && e.key !== 'Escape') return;
      if (e instanceof PointerEvent && ref.current?.contains(e.target as Node)) return;
      setOpen(false);
    };
    document.addEventListener('keydown', close);
    document.addEventListener('pointerdown', close);
    return () => {
      document.removeEventListener('keydown', close);
      document.removeEventListener('pointerdown', close);
    };
  }, [open]);

  return (
    <span ref={ref} className="relative inline">
      <abbr
        title={explain}
        tabIndex={0}
        role="button"
        aria-expanded={open}
        aria-describedby={open ? id : undefined}
        onClick={() => setOpen((o) => !o)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            setOpen((o) => !o);
          }
        }}
        className="cursor-help underline decoration-dotted underline-offset-[3px]"
      >
        {children}
      </abbr>
      {open && (
        <span
          id={id}
          role="tooltip"
          className="absolute left-0 top-full z-20 mt-1.5 block w-max max-w-[min(18rem,80vw)] rounded-control border border-line bg-card px-3 py-2 text-left text-sm font-normal normal-case tracking-normal text-ink shadow-lg"
        >
          {explain}
        </span>
      )}
    </span>
  );
}
