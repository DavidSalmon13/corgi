import { formatCents, type MoneyFormat } from '../lib/money';

type Props = MoneyFormat & {
  cents: number;
  className?: string;
};

/** An amount in integer cents, in tabular figures so columns line up. */
export function Money({ cents, signed, abs, className = '' }: Props) {
  return <span className={`tabular-nums whitespace-nowrap ${className}`}>{formatCents(cents, { signed, abs })}</span>;
}
