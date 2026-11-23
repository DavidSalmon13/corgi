import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { Mark } from './Mark';
import { DemoBadge, DemoFooter } from './DemoChrome';

/** The sign-in and sign-up page shell, from design/plainly/SignIn: a way back home, demo badge, mark, wordmark, footer. */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh justify-center bg-bg">
      <main className="flex w-full max-w-[430px] flex-col px-5 py-6">
        <div className="flex items-center justify-between gap-3">
          <Link
            to="/"
            className="-ml-1 inline-flex min-h-11 items-center gap-1 px-1 text-base font-semibold text-accent-ink no-underline hover:text-ink"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
              <path d="M15 18l-6-6 6-6" />
            </svg>
            Home
          </Link>
          <DemoBadge />
        </div>

        <div className="mt-10 flex flex-col gap-2.5 sm:mt-20">
          <Mark />
          <p className="m-0 text-[44px] font-bold leading-tight tracking-[-0.025em]">Plainly</p>
          <p className="m-0 text-[19px] leading-snug text-ink-2">Business insurance, explained in plain language.</p>
        </div>

        {children}

        <DemoFooter className="pt-12" />
      </main>
    </div>
  );
}
