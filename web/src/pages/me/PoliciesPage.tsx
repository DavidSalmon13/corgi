import { Link } from 'react-router';
import { useSession } from '../../api/client';
import type { MyPolicies, MyPolicyRow } from '../../api/types';
import { StatusPill } from '../../components/StatusPill';
import { Term } from '../../components/Term';
import { ThemeToggle } from '../../components/ThemeToggle';
import { GLOSSARY } from '../../lib/glossary';
import { mePolicyPath } from '../../lib/mePaths';
import { periodLine } from '../../lib/myCoverCopy';
import { NO_NETTING, YOUR_POLICIES, policyCardAmount, policyCardStatus, totalDueText } from '../../lib/policiesCopy';
import { MeHeader } from './HomePage';

const NAV_ROW =
  'flex min-h-14 items-center justify-between gap-3 border-t border-line px-5 py-2.5 font-semibold text-ink no-underline hover:bg-track hover:text-ink';

function Chevron() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className="flex-none">
      <path d="M9 18l6-6-6-6" />
    </svg>
  );
}

/** "Replaces GL-…" / "Replaced by GL-…", with the rewrite "?" so the link between the two policies is explained. */
function RewriteLine({ row }: { row: MyPolicyRow }) {
  if (!row.replaces && !row.replacedBy) return null;
  return (
    <p className="m-0 text-sm leading-snug text-ink-2 tabular-nums">
      <Term explain={GLOSSARY.rewrite}>Rewrite</Term>
      {row.replaces && ` · replaces ${row.replaces.policyNumber}`}
      {row.replacedBy && ` · replaced by ${row.replacedBy.policyNumber}`}
    </p>
  );
}

/** One P1-style card: its number and state, what is due on it (or coming back from it), its period, and the way in. */
function PolicyCard({ row }: { row: MyPolicyRow }) {
  const status = policyCardStatus(row);
  const money = policyCardAmount(row);
  const due = row.balanceCents > 0 && !row.refundWaiting;
  return (
    <li
      aria-label={`Policy ${row.policyNumber}`}
      className={`flex flex-col overflow-hidden rounded-2xl bg-card ${due ? 'border-[1.5px] border-accent' : 'border border-line'}`}
    >
      <div className="flex flex-col gap-2.5 px-5 pb-4 pt-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <span className="text-[17px] font-bold tabular-nums">{row.policyNumber}</span>
          <StatusPill tone={status.tone}>{status.label}</StatusPill>
        </div>
        <div>
          <div className={`text-[28px] font-bold leading-tight tracking-[-0.015em] tabular-nums ${due ? 'text-accent-ink' : 'text-ink'}`}>
            {money.amount}
          </div>
          <div className="text-[15px] text-ink-2">{money.note}</div>
        </div>
        <div className="flex flex-wrap justify-between gap-x-3 gap-y-1 text-sm text-ink-2">
          <Term explain={GLOSSARY.policyPeriod}>Policy period</Term>
          <span className="font-semibold text-ink tabular-nums">{periodLine(row.start, row.end)}</span>
        </div>
        <RewriteLine row={row} />
      </div>
      <Link to={mePolicyPath(row.policyId)} className={NAV_ROW}>
        Open {row.policyNumber}
        <Chevron />
      </Link>
    </li>
  );
}

/**
 * "Your policies" (more-policies AC7): for an owner with two or more policies, what is due across them, then one card
 * per policy, newest first. Each policy is paid on its own, so the total never nets a refund against a balance.
 */
export function PoliciesPage({ data }: { data: MyPolicies }) {
  const session = useSession();
  const total = totalDueText(data.totalDueCents);
  const mixed = data.totalDueCents > 0 && data.policies.some((p) => p.refundWaiting);
  return (
    <div className="flex min-h-dvh justify-center bg-bg">
      <div className="flex w-full max-w-[480px] flex-col">
        <MeHeader name={session.data?.displayName} />
        <main className="flex flex-col gap-[18px] px-5 pb-8 pt-1">
          <div>
            {session.data?.subtitle && <p className="m-0 text-[15px] text-ink-2">{session.data.subtitle}</p>}
            <h1 className="mt-0.5 mb-0 text-[30px] font-bold tracking-[-0.015em]">{YOUR_POLICIES}</h1>
          </div>

          <section aria-label="Total due" className="flex flex-col gap-1 rounded-2xl border border-line bg-card p-5">
            <div className={`text-[40px] font-bold leading-none tracking-[-0.02em] tabular-nums ${data.totalDueCents > 0 ? 'text-accent-ink' : 'text-ink'}`}>
              {total.amount}
            </div>
            <p className="m-0 text-[17px] leading-snug">{total.note}</p>
            {mixed && <p className="m-0 mt-1 text-[15px] leading-snug text-ink-2">{NO_NETTING}</p>}
          </section>

          <ul aria-label={YOUR_POLICIES} className="m-0 flex list-none flex-col gap-3.5 p-0">
            {data.policies.map((row) => (
              <PolicyCard key={row.policyId} row={row} />
            ))}
          </ul>

          <div className="flex items-center justify-end pt-2">
            <ThemeToggle />
          </div>
        </main>
      </div>
    </div>
  );
}
