import { describe, expect, it } from 'vitest';
import { balanceNote, balanceText, lastActionText, listSummary, policyPeriod, policyStatus } from './policyCopy';

// covers AC8, AC9, T16
describe('policy copy', () => {
  it('says whether an active policy is paid up or has a balance due', () => {
    expect(policyStatus('ACTIVE', 120_000)).toEqual({ label: 'Active · balance due', tone: 'wait' });
    expect(policyStatus('ACTIVE', 0)).toEqual({ label: 'Active · paid up', tone: 'good' });
    expect(policyStatus('ACTIVE', -500)).toEqual({ label: 'Active · paid up', tone: 'good' });
    expect(policyStatus('CANCELLED', 0)).toEqual({ label: 'Cancelled', tone: 'neutral' });
  });

  // covers more-policies AC4, T2 — B1 says which policy replaced a cancelled one
  it('says a cancelled policy was replaced, and by which', () => {
    expect(policyStatus('CANCELLED', -120_000, false, 'GL-2026-0014')).toEqual({
      label: 'Cancelled · replaced by GL-2026-0014',
      tone: 'neutral',
    });
    expect(policyStatus('CANCELLED', 0, false, null)).toEqual({ label: 'Cancelled', tone: 'neutral' });
  });

  it('shows owed balances as "$X due" and anything else as the amount', () => {
    expect(balanceText(120_000)).toBe('$1,200.00 due');
    expect(balanceText(0)).toBe('$0.00');
    expect(balanceText(-64_200)).toBe('$642.00 credit');
  });

  it('names the last action in plain words with a short date', () => {
    expect(lastActionText({ type: 'ISSUE', recordedOn: '2026-09-01' })).toBe('Issued · Sep 1');
    expect(lastActionText({ type: 'SOMETHING_NEW', recordedOn: '2026-11-05' })).toBe('Something new · Nov 5');
    expect(lastActionText(null)).toBe('—');
  });

  it('summarises the list with correct plurals', () => {
    expect(listSummary(5, 2)).toBe('5 policies · 2 owe money');
    expect(listSummary(1, 1)).toBe('1 policy · 1 owes money');
    expect(listSummary(3, 0)).toBe('3 policies · nobody owes money');
  });

  it('explains the balance under it in plain words', () => {
    expect(balanceNote(120_000)).toBe('Not paid yet');
    expect(balanceNote(0)).toBe('Nothing owed');
    expect(balanceNote(-500)).toBe('We owe the customer this');
  });

  // covers AC11, T4 — B3 and B1 say when a payment is with the bank
  it('says a payment is waiting for the bank instead of "not paid yet"', () => {
    expect(policyStatus('ACTIVE', 120_000, true)).toEqual({ label: 'Active · waiting for the bank', tone: 'wait' });
    expect(balanceNote(120_000, true)).toBe('Waiting for the bank');
    expect(balanceNote(120_000, false)).toBe('Not paid yet');
  });

  // covers cancel-refund AC9, T10 — a cancelled policy's period ends on the cancel day, and a pending refund is named
  it('ends the period on the cancel day once cancelled', () => {
    expect(policyPeriod('2026-09-01', '2027-09-01', null)).toBe('Sep 1, 2026 → Sep 1, 2027');
    expect(policyPeriod('2026-09-01', '2027-09-01', '2026-12-01')).toBe('Sep 1, 2026 → Dec 1, 2026 (cancelled)');
  });

  it('says a refund is on its way under the balance', () => {
    expect(balanceNote(-143_482, false, 143_482)).toBe('Refund of $1,434.82 on its way');
    expect(balanceNote(0, false, 0)).toBe('Nothing owed');
  });
});

// covers remove-cover AC7, T2 — the broker's list names a removal as the last action
describe('last action after a removal', () => {
  it('reads "Product liability removed"', () => {
    expect(lastActionText({ type: 'REMOVE_COVERAGE', recordedOn: '2026-11-22' })).toBe('Product liability removed · Nov 22');
  });
});
