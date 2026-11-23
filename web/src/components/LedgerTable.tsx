import type { LedgerLine, PolicyTransaction } from '../api/types';
import { GLOSSARY } from '../lib/glossary';
import { Glossed } from './Glossed';
import { Term } from './Term';
import { toLedgerRows, type LedgerRow, type Tone } from '../lib/ledgerRows';

const TONE: Record<Tone, string> = {
  ink: 'text-ink',
  good: 'text-good font-semibold',
  wait: 'text-wait font-semibold',
  bad: 'text-bad font-semibold',
};

export function Ref({ children }: { children: string }) {
  return (
    <span className="whitespace-nowrap rounded-[5px] bg-track px-1.5 py-px font-mono text-[12.5px]">{children}</span>
  );
}

function Refs({ refs }: { refs: string[] }) {
  return (
    <span className="flex flex-wrap gap-1">
      {refs.map((r) => (
        <Ref key={r}>{r}</Ref>
      ))}
    </span>
  );
}

function Title({ row }: { row: LedgerRow }) {
  return (
    <>
      <span className={`font-semibold ${row.struck ? 'text-ink-2 line-through' : ''}`}>
        <Glossed>{row.title}</Glossed>
      </span>
      {row.note && (
        <span className="block text-[13px] text-ink-2">
          <Glossed>{row.note}</Glossed>
        </span>
      )}
    </>
  );
}

const TH = 'px-3 py-2.5 text-left text-xs font-semibold uppercase tracking-[.05em] text-ink-2 border-b-[1.5px] border-ink';
const TD = 'px-3 py-3 align-top text-[15px] border-b border-dashed border-line';

/**
 * Every ledger line, oldest first, with how its amount was worked out and the records it came from.
 * A table on wide screens; a stacked list on phones so nothing scrolls sideways.
 */
export function LedgerTable({
  lines,
  transactions = [],
}: {
  lines: LedgerLine[];
  /** The policy's changes, so a removal's credit is titled as a removal. */
  transactions?: Pick<PolicyTransaction, 'displayId' | 'type'>[];
}) {
  const rows = toLedgerRows(lines, transactions);
  if (rows.length === 0) {
    return <p className="text-[15px] text-ink-2">No lines yet.</p>;
  }
  return (
    <>
      <table className="hidden w-full border-collapse md:table">
        <thead>
          <tr>
            <th className={TH}>Date</th>
            <th className={TH}>Line</th>
            <th className={TH}>
              <Term explain={GLOSSARY.proRata}>Pro-rata calculation</Term>
            </th>
            <th className={`${TH} text-right`}>Amount</th>
            <th className={`${TH} text-right`}>Balance</th>
            <th className={TH}>
              <Term explain="LE is this line in the ledger. PTX is the policy transaction it came from, so every amount can be traced back.">References</Term>
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.key} className={r.correction ? 'bg-accent-soft' : undefined}>
              <td className={`${TD} whitespace-nowrap text-ink-2 tabular-nums`}>{r.date}</td>
              <td className={TD}>
                <Title row={r} />
              </td>
              <td className={`${TD} whitespace-nowrap tabular-nums ${TONE[r.calcTone]}`}>
                <Glossed>{r.calc}</Glossed>
              </td>
              <td className={`${TD} whitespace-nowrap text-right font-semibold tabular-nums ${r.struck ? 'line-through' : ''}`}>
                {r.amount}
              </td>
              <td className={`${TD} whitespace-nowrap text-right tabular-nums`}>{r.balance}</td>
              <td className={TD}>
                <Refs refs={r.refs} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <ol className="flex flex-col md:hidden">
        {rows.map((r) => (
          <li
            key={r.key}
            className={`flex flex-col gap-1 border-b border-dashed border-line py-3 ${r.correction ? '-mx-2 bg-accent-soft px-2' : ''}`}
          >
            <div className="flex items-baseline justify-between gap-3">
              <span className="text-[13px] text-ink-2 tabular-nums">{r.date}</span>
              <span className={`text-[15px] font-semibold tabular-nums ${r.struck ? 'line-through' : ''}`}>{r.amount}</span>
            </div>
            <div className="text-[15px]">
              <Title row={r} />
            </div>
            <div className="flex flex-wrap items-center justify-between gap-2 text-[13px]">
              <span className={`tabular-nums ${TONE[r.calcTone]}`}>
                <Glossed>{r.calc}</Glossed>
              </span>
              <span className="text-ink-2 tabular-nums">Balance {r.balance}</span>
            </div>
            <Refs refs={r.refs} />
          </li>
        ))}
      </ol>
    </>
  );
}
