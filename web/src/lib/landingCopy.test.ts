import { describe, expect, it } from 'vitest';
import { DEMO_ACCOUNTS } from '../pages/SignIn';
import { HERO_LEAD, NO_BUY_ONLINE, SHOWS, STEPS, reviewerEntries } from './landingCopy';

// covers AC3, AC4, AC5, AC6, AC7, T2
describe('landingCopy', () => {
  it('says what Plainly is in the standard term', () => {
    expect(HERO_LEAD).toMatch(/General liability/);
  });

  it('has three steps, ending with creating the account', () => {
    expect(STEPS).toHaveLength(3);
    expect(STEPS[2].title).toBe('You create your account');
  });

  it('uses the standard terms on the policy-page lines', () => {
    const text = SHOWS.join(' ');
    for (const term of ['premium', 'endorsement', 'pro-rata', 'ledger', 'reversal']) expect(text).toContain(term);
  });

  it('says buying online is not available', () => {
    expect(NO_BUY_ONLINE).toMatch(/can't buy online/);
  });

  it('builds one reviewer entry per demo account, linking to a prefilled sign-in', () => {
    const entries = reviewerEntries();
    expect(entries).toHaveLength(DEMO_ACCOUNTS.length);
    expect(entries[0]).toEqual({
      label: 'Try as Maya, the policyholder',
      note: "Opens sign-in with Maya's details filled in",
      href: '/signin?demo=maya%40juniperandrye.test',
      initials: 'MO',
    });
    expect(entries[1].label).toBe('Try as Sam, the broker');
  });
});
