import { NavLink, Outlet, useLocation } from 'react-router';
import { useSession } from '../api/client';
import { SignOutButton } from './SignOutButton';
import { ThemeToggle } from './ThemeToggle';

const link = ({ isActive }: { isActive: boolean }) =>
  `inline-flex min-h-11 items-center px-1 font-semibold text-ink no-underline hover:text-accent-ink ${
    isActive ? 'underline decoration-accent decoration-[3px] underline-offset-[10px]' : ''
  }`;

/** Desktop shell for broker pages (B1–B3): wordmark, nav, who's signed in. */
export function BrokerShell() {
  const session = useSession();
  // "Policies" covers the list and every policy's workspace, but not the new-customer form.
  const onNew = useLocation().pathname.startsWith('/broker/policies/new');
  const who = session.data ? `${session.data.displayName} · Broker` : '';
  return (
    <div className="min-h-dvh bg-bg">
      <header className="border-b border-line bg-card">
        <div className="mx-auto flex max-w-[1200px] flex-wrap items-center gap-x-6 gap-y-1 px-4 py-2 md:px-8">
          <span className="mr-2 text-xl font-bold tracking-[-0.01em]">Plainly</span>
          <nav aria-label="Broker" className="flex gap-6">
            <NavLink to="/broker/policies" className={({ isActive }) => link({ isActive: isActive && !onNew })}>
              Policies
            </NavLink>
            <NavLink to="/broker/policies/new" className={link}>
              New customer
            </NavLink>
          </nav>
          <span className="flex-1" />
          {who && <span className="text-[15px] text-ink-2">{who}</span>}
          <SignOutButton />
          <ThemeToggle />
        </div>
      </header>
      <main className="mx-auto flex max-w-[1200px] flex-col gap-6 px-4 pb-12 pt-7 md:px-8">
        <Outlet />
      </main>
    </div>
  );
}
