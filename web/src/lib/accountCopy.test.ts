import { describe, expect, it } from 'vitest';
import { ownerAccountLine } from './accountCopy';

// covers real-sign-in AC11, T14 — the broker can tell whether to nudge the owner
describe('ownerAccountLine', () => {
  it('says the owner has not signed up yet, by first name', () => {
    expect(ownerAccountLine('Maya Okafor', null)).toBe("Maya hasn't created an account yet");
  });

  it('gives the New York day they signed up', () => {
    expect(ownerAccountLine('Maya Okafor', '2026-11-22T15:30:00Z')).toBe('Signed up Nov 22, 2026');
    // 02:00 UTC on Nov 23 is still Nov 22 in New York
    expect(ownerAccountLine('Maya Okafor', '2026-11-23T02:00:00Z')).toBe('Signed up Nov 22, 2026');
  });
});
