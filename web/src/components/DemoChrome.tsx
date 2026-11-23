import { ThemeToggle } from './ThemeToggle';

// The demo framing every public page carries (sign-in, sign-up, landing): the badge that says the people and money
// are fictional, and the footer with the Stripe test-mode note and the theme toggle.

const BADGE = 'Demo · fictional people and money';
const NOTE = "This is a demo. No real insurance is sold, and payments use Stripe's test mode.";

/** The "Demo" pill. `small` is the landing page's size; sign-in and sign-up use the default. */
export function DemoBadge({ small = false }: { small?: boolean }) {
  return (
    <span
      className={`inline-block self-start rounded-full bg-accent-soft px-2.5 py-1 font-bold uppercase tracking-[.06em] text-accent-ink ${
        small ? 'text-xs' : 'text-[13px]'
      }`}
    >
      {BADGE}
    </span>
  );
}

/** The test-mode note with the light/dark switch beside it, pinned to the bottom of a public page. */
export function DemoFooter({ className = '' }: { className?: string }) {
  return (
    <div className={`mt-auto flex items-end justify-between gap-4 ${className}`}>
      <p className="m-0 text-sm leading-normal text-ink-2">{NOTE}</p>
      <ThemeToggle />
    </div>
  );
}
