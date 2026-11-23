import { describe, expect, it } from 'vitest';
import type { SignedInUser } from '../api/types';
import { homeFor, homeTarget } from './RouteGuards';

// covers AC1, T14 — each role lands on its own home
describe('homeFor', () => {
  it('sends a broker to the policy list and a policyholder to their cover', () => {
    expect(homeFor('BROKER')).toBe('/broker/policies');
    expect(homeFor('POLICYHOLDER')).toBe('/me');
  });
});

// covers AC1, T4 (landing-page spec) — `/` shows guests the landing page, signed-in people their home
describe('homeTarget', () => {
  const user = (role: SignedInUser['role']) => ({ role }) as SignedInUser;

  it('shows the landing page to a guest', () => {
    expect(homeTarget(null)).toBe('landing');
  });

  it('sends each signed-in role to its home', () => {
    expect(homeTarget(user('POLICYHOLDER'))).toBe('/me');
    expect(homeTarget(user('BROKER'))).toBe('/broker/policies');
  });
});
