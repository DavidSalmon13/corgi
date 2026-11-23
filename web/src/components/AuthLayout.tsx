import type { ReactNode } from 'react';
import { Mark } from './Mark';
import { DemoBadge, DemoFooter } from './DemoChrome';

/** The sign-in and sign-up page shell, from design/plainly/SignIn: demo badge, mark, wordmark, footer. */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-dvh justify-center bg-bg">
      <main className="flex w-full max-w-[430px] flex-col px-5 py-6">
        <div>
          <DemoBadge />
        </div>

        <div className="mt-12 flex flex-col gap-2.5 sm:mt-20">
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
