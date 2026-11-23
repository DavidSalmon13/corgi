import { describe, expect, it } from 'vitest';
import { DEMO_ACCOUNTS } from '../pages/SignIn';
import { glossParts } from '../components/Glossed';
import {
  HERO_LEAD,
  NO_BUY_ONLINE,
  OFFER_INTRO,
  PRICES_UNAVAILABLE,
  SHOWS,
  STEPS,
  offerDescription,
  offerPrice,
  offerRole,
  reviewerEntries,
} from './landingCopy';

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

// covers AC12, T3.2
describe('landingCopy: what we offer', () => {
  it('shows the per-day price only when the annual premium splits into whole cents', () => {
    expect(offerPrice(73_000)).toEqual({ amount: '$730.00', per: 'a year · $2.00 a day' });
    expect(offerPrice(120_000)).toEqual({ amount: '$1,200.00', per: 'a year' });
  });

  it('calls general liability the policy and product liability an endorsement', () => {
    expect(offerRole('BASE')).toBe('The policy');
    expect(offerRole('ENDORSEMENT')).toBe('Endorsement');
  });

  it('says what each cover pays for, and that product liability is charged pro-rata from its start', () => {
    expect(offerDescription('BASE')).toMatch(/hurt/);
    expect(offerDescription('PRODUCT_LIABILITY')).toMatch(/pro-rata from the day it starts/);
  });

  it('marks the premium term in the intro so it gets its "?"', () => {
    expect(glossParts(OFFER_INTRO).some((p) => typeof p !== 'string' && p.term === 'annualPremium')).toBe(true);
  });

  it('has a quiet line for when prices cannot be loaded', () => {
    expect(PRICES_UNAVAILABLE).toMatch(/aren't available right now/);
  });
});
