import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { api } from '../../api/client';
import type { PolicyListRow } from '../../api/types';
import { Mark } from '../../components/Mark';
import { StatusPill } from '../../components/StatusPill';
import { balanceText, lastActionText, listSummary, policyStatus } from '../../lib/policyCopy';

type Filter = 'all' | 'owes';

const BTN =
  'inline-flex min-h-11 items-center justify-center rounded-control bg-accent px-[18px] text-[15px] font-bold text-on-accent no-underline hover:text-on-accent';
const TH = 'px-4 py-3 text-left text-xs font-semibold uppercase tracking-[.05em] text-ink-2 border-b-[1.5px] border-ink';
const TD = 'px-4 py-3.5 text-[15px] border-b border-line';

function Chip({ on, onClick, children }: { on: boolean; onClick: () => void; children: string }) {
  return (
    <button
      type="button"
      aria-pressed={on}
      onClick={onClick}
      className={`min-h-11 rounded-full border-[1.5px] px-4 text-[15px] font-semibold ${
        on ? 'border-ink bg-ink text-bg' : 'border-line bg-transparent text-ink'
      }`}
    >
      {children}
    </button>
  );
}

function Balance({ row }: { row: PolicyListRow }) {
  return (
    <span className={`font-bold tabular-nums whitespace-nowrap ${row.owesMoney ? 'text-accent-ink' : 'text-ink'}`}>
      {balanceText(row.balanceCents)}
    </span>
  );
}

function Status({ row }: { row: PolicyListRow }) {
  const s = policyStatus(row.status, row.balanceCents);
  return <StatusPill tone={s.tone}>{s.label}</StatusPill>;
}

function EmptyState() {
  return (
    <section className="flex flex-col items-center gap-3 rounded-2xl border border-line bg-card px-6 py-16 text-center">
      <Mark size={80} />
      <h2 className="m-0 text-[22px] font-bold">No policies yet</h2>
      <p className="m-0 max-w-[420px] text-base leading-normal text-ink-2">
        Add a customer and their first policy. You'll see the price before anything is saved.
      </p>
      <Link to="/broker/policies/new" className={`${BTN} mt-2`}>
        Add your first customer
      </Link>
    </section>
  );
}

/** B1: every policy, who owes money, and what happened last. */
export function PoliciesPage() {
  const [params, setParams] = useSearchParams();
  const filter: Filter = params.get('show') === 'owes' ? 'owes' : 'all';
  const setFilter = (f: Filter) => setParams(f === 'all' ? {} : { show: f }, { replace: true });

  const policies = useQuery({ queryKey: ['policies'], queryFn: () => api<PolicyListRow[]>('/api/broker/policies') });
  const all = policies.data ?? [];
  const owing = all.filter((p) => p.owesMoney).length;
  const rows = filter === 'owes' ? all.filter((p) => p.owesMoney) : all;
  const empty = policies.isSuccess && all.length === 0;

  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="m-0 text-[32px] font-bold tracking-[-0.015em]">Policies</h1>
          <p className="mt-1 text-base text-ink-2">
            {policies.isPending
              ? 'Loading…'
              : policies.isError
                ? "We couldn't load the policies. Reload to try again."
                : empty
                  ? 'Your customers’ policies will show up here.'
                  : listSummary(all.length, owing)}
          </p>
        </div>
        {!empty && (
          <Link to="/broker/policies/new" className={BTN}>
            New customer and policy
          </Link>
        )}
      </div>

      {empty && <EmptyState />}

      {all.length > 0 && (
        <div className="flex flex-col gap-4">
          <div role="group" aria-label="Filter" className="flex flex-wrap gap-2">
            <Chip on={filter === 'all'} onClick={() => setFilter('all')}>{`All · ${all.length}`}</Chip>
            <Chip on={filter === 'owes'} onClick={() => setFilter('owes')}>{`Owes money · ${owing}`}</Chip>
          </div>

          <section aria-label="Policy list" className="overflow-hidden rounded-2xl border border-line bg-card">
            {rows.length === 0 ? (
              <p className="m-0 px-4 py-6 text-[15px] text-ink-2">Nobody owes money right now.</p>
            ) : (
              <>
                <table className="hidden w-full border-collapse md:table">
                  <thead>
                    <tr>
                      <th className={TH}>Customer</th>
                      <th className={TH}>Policy no.</th>
                      <th className={TH}>Status</th>
                      <th className={`${TH} text-right`}>Balance</th>
                      <th className={TH}>Last action</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((r) => (
                      <tr key={r.policyId}>
                        <td className={TD}>
                          <Link to={`/broker/policies/${r.policyId}`} className="font-bold text-ink no-underline hover:underline">
                            {r.customerName}
                          </Link>
                          <span className="block text-[13px] text-ink-2">{r.ownerName}</span>
                        </td>
                        <td className={`${TD} tabular-nums`}>{r.policyNumber}</td>
                        <td className={TD}>
                          <Status row={r} />
                        </td>
                        <td className={`${TD} text-right`}>
                          <Balance row={r} />
                        </td>
                        <td className={`${TD} text-ink-2`}>{lastActionText(r.lastAction)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>

                <ul className="m-0 list-none p-0 md:hidden">
                  {rows.map((r) => (
                    <li key={r.policyId} className="border-b border-line last:border-b-0">
                      <Link
                        to={`/broker/policies/${r.policyId}`}
                        className="flex flex-col gap-1.5 px-4 py-3.5 text-ink no-underline hover:bg-bg hover:text-ink"
                      >
                        <span className="flex items-baseline justify-between gap-3">
                          <span className="font-bold">{r.customerName}</span>
                          <Balance row={r} />
                        </span>
                        <span className="text-[13px] text-ink-2 tabular-nums">
                          {r.ownerName} · {r.policyNumber}
                        </span>
                        <span className="flex flex-wrap items-center justify-between gap-2">
                          <Status row={r} />
                          <span className="text-[13px] text-ink-2">{lastActionText(r.lastAction)}</span>
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </section>
        </div>
      )}
    </>
  );
}
