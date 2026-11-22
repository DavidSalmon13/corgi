import { describe, expect, it } from 'vitest';
import { homeFor } from './RouteGuards';

// covers AC1, T14 — each role lands on its own home
describe('homeFor', () => {
  it('sends a broker to the policy list and a policyholder to their cover', () => {
    expect(homeFor('BROKER')).toBe('/broker/policies');
    expect(homeFor('POLICYHOLDER')).toBe('/me');
  });
});
