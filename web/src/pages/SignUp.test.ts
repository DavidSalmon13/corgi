import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/client';
import { ALREADY_SIGNED_UP, signUpProblems } from './SignUp';

// covers real-sign-in AC3, AC5, T13 — refusals land on the field they belong to
describe('signUpProblems', () => {
  it('puts each 422 field error under its field', () => {
    const p = signUpProblems(
      new ApiError(422, {
        error: 'validation',
        message: '2 things to fix before you can save.',
        fields: { email: ALREADY_SIGNED_UP, confirmPassword: "The two passwords don't match." },
      }),
    );
    expect(p.general).toBeNull();
    expect(p.fields).toEqual({ email: ALREADY_SIGNED_UP, confirmPassword: "The two passwords don't match." });
  });

  it('gives one general message for anything else', () => {
    const p = signUpProblems(new ApiError(500, null));
    expect(p.fields).toEqual({});
    expect(p.general).toMatch(/couldn't reach Plainly/);
  });

  it('matches the API wording for an email that already has an account', () => {
    expect(ALREADY_SIGNED_UP).toBe('This email already has an account. Sign in instead.');
  });
});
