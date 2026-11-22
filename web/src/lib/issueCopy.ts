import { formatDate, type IsoDate } from './dates';
import { formatCents } from './money';

/** The plain-language summary B2 shows under "Customer will see", and the policyholder later reads. */
export function customerWillSee(p: { start: IsoDate; end: IsoDate; totalCents: number; extra: boolean }): string {
  const including = p.extra ? ', including the products you sell' : '';
  return `You're covered from ${formatDate(p.start)} to ${formatDate(p.end)}${including}. The price is ${formatCents(p.totalCents)} for the year.`;
}
