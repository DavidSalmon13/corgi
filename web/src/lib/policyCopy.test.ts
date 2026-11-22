import { describe, expect, it } from 'vitest';
import { balanceNote, balanceText, lastActionText, listSummary, policyStatus } from './policyCopy';

// covers AC8, AC9, T16
describe('policy copy', () => {
  it('says whether an active policy is paid up or has a balance due', () => {
    expect(policyStatus('ACTIVE', 120_000)).toEqual({ label: 'Active · balance due', tone: 'wait' });
    expect(policyStatus('ACTIVE', 0)).toEqual({ label: 'Active · paid up', tone: 'good' });
    expect(policyStatus('ACTIVE', -500)).toEqual({ label: 'Active · paid up', tone: 'good' });
    expect(policyStatus('CANCELLED', 0)).toEqual({ label: 'Cancelled', tone: 'neutral' });
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
});
