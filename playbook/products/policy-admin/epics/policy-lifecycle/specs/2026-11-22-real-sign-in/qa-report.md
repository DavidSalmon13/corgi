# QA Report — Real sign-in

> Spec: spec.md
> Date: 2026-11-22 · QA by: david-schwartzman · Mode: setup
> Verdict: PASS WITH NOTES

## Acceptance Criteria

| AC | Criterion | Result | Evidence |
|---|---|---|---|
| AC1 | Email/password form in the Plainly layout; correct login lands broker on `/broker/policies`, policyholder on `/me`; emails case- and space-insensitive | pass | `AuthTest.theBrokerSignsInWithEmailAndPasswordAndTheSessionReadsItBack`, `emailsMatchIgnoringCaseAndSurroundingSpaces`, `anOwnerEmailWithATabOrNonBreakingSpaceCanStillSignUp`; `web/src/pages/SignIn.tsx` (form, `homeFor` on success); `web/src/components/AuthLayout.tsx` (badge, mark, wordmark, footer, ThemeToggle); `RouteGuards.test.ts` |
| AC2 | Wrong password and unknown email: same message, 401, never says which | pass | `AuthTest.aWrongPasswordAndAnUnknownEmailGetTheSame401` (identical bodies); `PasswordsTest` (dummy BCrypt check); `SignIn.test.ts` `signInProblem` |
| AC3 | `/signin` links to `/signup`; sign-up asks email, password, confirm, with plain hints | pass | `SignIn.tsx` "New here? Your broker set up your policy. Create your account"; `SignUp.tsx` hints "Use the email your broker has on file for your business." and "At least 10 characters."; `/signup` route in `App.tsx` |
| AC4 | Sign-up only for an unclaimed owner email; sets password, signs in, lands on `/me` | pass | `AuthTest.theOwnerSignsUpWithTheEmailTheirBrokerEnteredAndIsSignedIn`, `AccountsTest.signingUpClaimsTheWaitingAccountOnce`, `IssuePolicyServiceTest.issuingGivesTheOwnerAnAccountWithTheirEmailButNoPasswordYet`; observed in T14 run (Lee signed up and saw only Lee's cover) |
| AC5 | Refusals: unknown email, existing account (with sign-in link), short password, mismatch, broker email; nothing created | pass | `AuthTest.anEmailNoBrokerEnteredCannotSignUp`, `anEmailThatAlreadyHasAnAccountCannotSignUpAgain`, `theBrokersEmailCanNeverBeUsedToSignUp`, `aShortPasswordOrPasswordsThatDoNotMatchAreRefusedAndNothingIsCreated`; `AccountsTest.everyProblemIsListedAndNothingChanges`; `SignUp.tsx` `errorAction` link; `SignUp.test.ts` |
| AC6 | B2 refuses an owner email another customer uses | pass | `IssuePolicyServiceTest.anOwnerEmailAnotherCustomerAlreadyUsesIsRejectedIgnoringCase`, `BrokerIssueEndpointsTest.anOwnerEmailAnotherCustomerUsesGetsAFieldError`; B2 shows `fields.ownerEmail` (`NewPolicyPage.tsx:211`); observed 422 in T14 run |
| AC7 | Cookie httpOnly, Lax, Secure over HTTPS, random token, only SHA-256 stored, 7 days; edited/unknown/expired/old demo cookie → 401 | pass | `AuthTest.theSessionCookieIsHttpOnlyLaxAndExpiresInSevenDays`, `theSessionCookieIsSecureOverHttps`, `onlyAHashOfTheTokenIsStored`, `anEditedUnknownExpiredOrOldDemoCookieCountsAsSignedOut`; `SessionsTest` |
| AC8 | Sign-out ends the session server-side and clears the cookie; new sign-in gives a new token | pass | `AuthTest.signingOutEndsTheSessionOnTheServerAndSigningInAgainGivesANewToken`; `SignOutButton.tsx` + `useSignOut` clears the query cache; observed replay → 401 in T14 run |
| AC9 | Passwords only as BCrypt hashes; never returned or logged | pass | `AuthTest.passwordsAreStoredAsBcryptHashesAndNeverReturned`, `PasswordsTest`; `SignedInUser` has no hash field; security review T17 found no leak |
| AC10 | Role rules hold: 403 across roles, SPA redirects, `/api/me` scoped to own customer | pass | `AuthTest.aPolicyholderCannotUseBrokerRoutesAndABrokerCannotUsePolicyholderRoutes`, `pathParametersAndEncodedCharactersDoNotSlipPastTheGuard`, `MyCoverTest.eachOwnerOnlySeesTheirOwnBusiness`; `RouteGuards.tsx:25-26` |
| AC11 | B3 shows whether the owner has signed up | pass | `BrokerPolicyReadTest.thePolicyViewSaysWhetherTheOwnerHasSignedUp`, `accountCopy.test.ts`; `PolicyWorkspace.tsx` owner line. Wording differs, see Notes |
| AC12 | Demo accounts for Sam and Maya sign in with the demo password; listed on sign-in and README; seed idempotent, off in tests | pass | `DemoSeederTest`, `AccountsTest.theSeededBrokerSignsInWithTheDemoPasswordOnly`, `SignIn.test.ts`; README "Demo accounts"; `IntegrationTest` sets `plainly.demo.seed=false`; observed seed log in T14 run |
| AC13 | DECISIONS replaces the simulated entry; README moves sign-in out of "simulated" | pass | `DECISIONS.md`: simulated entry marked superseded; new entries for real sign-in, no email confirmation (risk accepted), one email per customer, CSRF and brute force, demo broker login. README states sign-in is real and lists what's simplified |

## Checks Run

- build (web): `npm run build` — pass
- build (api): `./gradlew build` — pass
- lint: none configured in either package — n/a
- typecheck: `npm run typecheck` — pass
- tests: `./gradlew test` — pass (80 passed, 0 failed); `npx vitest run` — pass (65 passed, 13 files)
- security review: security-reviewer agent (T17) — PASS; 0 critical, 0 high; 2 medium and 3 low, of which 4 were fixed and 1 kept on purpose and documented

## Issues Found

(none blocking)

## Notes

- **No browser check of the new screens.** Sign-in and sign-up were verified by unit tests, code inspection and HTTP calls, not by opening them in a browser. Dark mode and the 390 px layout are unchecked. They reuse the existing tokens and the original sign-in layout, so the risk is low, but look at them once before the demo video.
- **AC11 wording.** The spec says "Maya hasn't created her account yet". The app says "Maya hasn't created an account yet" so it doesn't assume the owner's pronouns. Use `/spec-changes` to align the spec if you want them to match exactly.
- **Hygiene.** All 18 tasks are done once T18 is ticked. No PR is open for `feat/policy-lifecycle/real-sign-in` yet. The branch is stacked on `issue-policy--t20-t21` (PR #22), so it should merge after #14–#22.
- **Kept risk.** The public demo broker login works on every deploy (`DECISIONS.md`, "The demo broker login exists on every deploy"). That's fine for the trial, but this build must never hold real customers.
