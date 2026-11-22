import type { ReactNode } from 'react';
import { Mark } from './Mark';
import { ThemeToggle } from './ThemeToggle';

/** The sign-in and sign-up page shell, from design/plainly/SignIn: demo badge, mark, wordmark, footer. */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh justify-center bg-bg">
      <main className="flex w-full max-w-[430px] flex-col px-5 py-6">
        <div>
          <span className="inline-block rounded-full bg-accent-soft px-2.5 py-1 text-[13px] font-bold uppercase tracking-[.06em] text-accent-ink">
            Demo · fictional people and money
          </span>
        </div>

        <div className="mt-12 flex flex-col gap-2.5 sm:mt-20">
          <Mark />
          <p className="m-0 text-[44px] font-bold leading-tight tracking-[-0.025em]">Plainly</p>
          <p className="m-0 text-[19px] leading-snug text-ink-2">Business insurance, explained in plain language.</p>
        </div>

        {children}

        <div className="mt-auto flex items-end justify-between gap-4 pt-12">
          <p className="m-0 text-sm leading-normal text-ink-2">
            This is a demo. No real insurance is sold, and payments use Stripe's test mode.
          </p>
          <ThemeToggle />
        </div>
      </main>
    </div>
  );
}
