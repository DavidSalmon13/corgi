import { formatDate, type IsoDate } from './dates';
import { formatCents } from './money';

/** The summary B2 shows under "Customer will see", and the policyholder later reads. */
export function customerWillSee(p: { start: IsoDate; end: IsoDate; totalCents: number; extra: boolean }): string {
  const including = p.extra ? ', including the products you sell' : '';
  return `You're covered from ${formatDate(p.start)} to ${formatDate(p.end)}${including}. Your premium is ${formatCents(p.totalCents)} for the policy period.`;
}
