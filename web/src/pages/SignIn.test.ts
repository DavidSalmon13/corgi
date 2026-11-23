import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/client';
import { DEMO_ACCOUNTS, DEMO_PASSWORD, demoPrefill, signInProblem } from './SignIn';

// covers real-sign-in AC2, T12 — one message for every wrong combination; other failures say so
describe('signInProblem', () => {
  it('never says which part was wrong', () => {
    expect(signInProblem(new ApiError(401, { error: 'wrong_credentials' }))).toBe(
      "That email and password don't match. Check them and try again.",
    );
  });

  it('tells people when Plainly could not be reached', () => {
    expect(signInProblem(new ApiError(500, null))).toMatch(/couldn't reach Plainly/);
    expect(signInProblem(new TypeError('Failed to fetch'))).toMatch(/couldn't reach Plainly/);
  });
});

// covers real-sign-in AC12, T12 — reviewers get one account per role
describe('demo accounts', () => {
  it('lists a policyholder and a broker with the seeded password', () => {
    expect(DEMO_ACCOUNTS.map((a) => a.role)).toEqual(['Policyholder', 'Broker']);
    expect(DEMO_ACCOUNTS.map((a) => a.email)).toEqual(['maya@juniperandrye.test', 'sam@plainly.test']);
    expect(DEMO_PASSWORD).toBe('plainly-demo-2026');
  });
});

// covers landing-page AC7, T5 — "Try as Maya" opens sign-in filled in; anything else in ?demo= is ignored
describe('demoPrefill', () => {
  it('fills in a known demo account and the demo password', () => {
    expect(demoPrefill('?demo=maya%40juniperandrye.test')).toEqual({
      email: 'maya@juniperandrye.test',
      password: DEMO_PASSWORD,
    });
    expect(demoPrefill('?demo=sam@plainly.test')?.email).toBe('sam@plainly.test');
  });

  it('ignores an unknown email, an empty value and a missing parameter', () => {
    expect(demoPrefill('?demo=someone@example.com')).toBeNull();
    expect(demoPrefill('?demo=')).toBeNull();
    expect(demoPrefill('')).toBeNull();
  });
});
