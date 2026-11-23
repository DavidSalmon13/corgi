import type { MyPolicyRow, PolicyLink } from '../api/types';
import type { PillTone } from '../components/StatusPill';
import { formatDate, type IsoDate } from './dates';
import { formatCents } from './money';

// The words for several policies per customer: the policyholder's "Your policies" overview, the "All policies"
// link, and the broker's rewrite. Each policy keeps its own money: a refund on one is never netted against another.

export const YOUR_POLICIES = 'Your policies';
export const ALL_POLICIES = 'All policies';
export const NEW_POLICY_FOR_CUSTOMER = 'New policy for this customer';
export const REWRITE_POLICY = 'Rewrite this policy';

/** The overview's headline: what they owe across every policy (positive balances only, as the API adds them). */
export function totalDueText(totalDueCents: number): { amount: string; note: string } {
  return totalDueCents > 0
    ? { amount: formatCents(totalDueCents), note: 'Due across your policies' }
    : { amount: formatCents(0), note: 'Nothing due on any policy' };
}

/** Under the total, when one policy has a refund on its way and another has money due. */
export const NO_NETTING = "Each policy is paid on its own: a refund on one isn't taken off what's due on another.";

/** The pill on one policy card. */
export function policyCardStatus(row: MyPolicyRow): { label: string; tone: PillTone } {
  if (row.status === 'CANCELLED') {
    return {
      label: row.replacedBy ? `Cancelled · replaced by ${row.replacedBy.policyNumber}` : 'Cancelled',
      tone: 'neutral',
    };
  }
  if (row.paymentWaiting) return { label: 'Active · waiting for your bank', tone: 'wait' };
  return row.balanceCents > 0 ? { label: 'Active · payment due', tone: 'wait' } : { label: 'Active · paid up', tone: 'good' };
}

/** The amount on one policy card, and the line under it: due, waiting for the bank, refund on its way, or nothing. */
export function policyCardAmount(row: MyPolicyRow): { amount: string; note: string } {
  if (row.refundWaiting) {
    const card = row.refundWaiting.cardLast4 ? `your card ending ${row.refundWaiting.cardLast4}` : 'your card';
    return { amount: formatCents(row.refundWaiting.amountCents), note: `Refund on its way to ${card}` };
  }
  if (row.balanceCents > 0) {
    return { amount: formatCents(row.balanceCents), note: row.paymentWaiting ? 'Payment waiting for your bank' : 'Due now' };
  }
  return { amount: formatCents(0), note: 'Nothing due' };
}

/** "Replaces GL-…" / "Replaced by GL-…", or null when the policy is neither. */
export function replacedNote(row: { replaces?: PolicyLink | null; replacedBy?: PolicyLink | null }): string | null {
  const parts: string[] = [];
  if (row.replaces) parts.push(`Replaces ${row.replaces.policyNumber}`);
  if (row.replacedBy) parts.push(`${parts.length ? 'replaced' : 'Replaced'} by ${row.replacedBy.policyNumber}`);
  return parts.length ? parts.join(' · ') : null;
}

/** B2's note on a rewrite: "Replaces GL-2026-0013 (cancelled Sep 1, 2026)". */
export function rewriteNote(policyNumber: string, cancelledOn: IsoDate): string {
  return `Replaces ${policyNumber} (cancelled ${formatDate(cancelledOn)})`;
}

/** Whether a page shows the "All policies" link: only when the owner has two or more. */
export function showAllPolicies(policyCount: number): boolean {
  return policyCount >= 2;
}

/** Whether B3 offers "Rewrite this policy" as its main action: cancelled and not replaced yet. */
export function canRewrite(policy: { status: MyPolicyRow['status']; replacedBy?: PolicyLink | null }): boolean {
  return policy.status === 'CANCELLED' && !policy.replacedBy;
}
