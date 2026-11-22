/** U+2212, the true minus sign the designs use for credits. */
export const MINUS = '−';

export type MoneyFormat = {
  /** Show `+$` for positive amounts (charges) as well as `−$` for negative ones (credits). */
  signed?: boolean;
  /** Drop the sign entirely, for a magnitude such as "Refund of $1,434.82". */
  abs?: boolean;
};

/**
 * Formats integer cents as US dollars without ever going through floating point.
 * 120000 → "$1,200.00"; with `signed`, "+$1,200.00"; −64200 → "−$642.00".
 */
export function formatCents(cents: number, { signed = false, abs = false }: MoneyFormat = {}): string {
  if (!Number.isSafeInteger(cents)) {
    throw new Error(`Money must be integer cents, got ${cents}`);
  }
  const magnitude = Math.abs(cents);
  const dollars = Math.trunc(magnitude / 100).toLocaleString('en-US');
  const rest = String(magnitude % 100).padStart(2, '0');
  const body = `$${dollars}.${rest}`;
  if (abs || cents === 0) return body;
  if (cents < 0) return `${MINUS}${body}`;
  return signed ? `+${body}` : body;
}
