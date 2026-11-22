# Tasks — Real sign-in

> Epic: policy-lifecycle
> Spec: 2026-11-22-real-sign-in
> Branch: feat/policy-lifecycle/real-sign-in

## Tests first (TDD)

- [x] T1 Write failing `AuthTest` (api/src/test/java/app/plainly/auth/): sign-in ok, wrong password and unknown email both 401 with the same body, sign-up ok, unknown email, already signed up, broker email refused, short and mismatched passwords, session cookie flags, edited/expired/old demo cookie → 401, sign-out kills the token, role 403s (AC1–AC10)
- [x] T2 Write a failing duplicate-owner-email test in `IssuePolicyServiceTest` and `BrokerIssueEndpointsTest` (AC6), plus `TestData.uniqueEmail()` and an `AuthTestSupport.signIn` helper

## Backend

- [x] T3 Add `spring-security-crypto` to api/build.gradle.kts
- [x] T4 Migration `V5__accounts_and_sessions.sql`: user email/password_hash/signed_up_at, lower(email) unique, sessions table, unique lower(owner_email), Sam's email and demo hash
- [x] T5 `Passwords` and `Sessions` (hashed random tokens, 7-day expiry, end on sign-out)
- [ ] T6 `Accounts` (from DemoUsers) with `signUp` claiming the unclaimed owner row in one transaction
- [ ] T7 `AuthFilter` + `AuthController` (`/api/auth/signin|signup|session`), `SignedInUser`; delete Demo* classes and `/api/demo/*`; update MyCoverController and BrokerIssueController
- [ ] T8 `IssuePolicyService`: duplicate owner-email field error; set the owner user's email; expose `ownerSignedUpAt` through PolicyQueries/PolicyViews
- [ ] T9 `DemoSeeder` (Maya's policy via IssuePolicyService, demo password; `plainly.demo.seed`, off in `@IntegrationTest`); `plainly.session.secure-cookie` config
- [ ] T10 Move MyCoverTest, BrokerIssueEndpointsTest, BrokerPolicyReadTest, IssueAtomicityTest onto the helpers; whole API suite green

## Web

- [ ] T11 `client.ts` + `types.ts`: `/api/auth/session`, `SignedInUser`; update `client.test.ts`
- [ ] T12 Rebuild `SignIn.tsx` as an email/password form with the Demo accounts box (AC1, AC2, AC12)
- [ ] T13 New `SignUp.tsx` + `/signup` route with field errors (AC3–AC5)
- [ ] T14 Sign-out calls `DELETE /api/auth/session` and clears the cache (AC8); B3 shows the owner's signed-up status (AC11); B2 shows the duplicate email error (AC6)

## Docs

- [ ] T15 DECISIONS.md entry replacing "Demo sign-in is simulated" (AC13); README demo credentials and integrations table; `SESSION_COOKIE_SECURE=true` noted for Railway

## Verification

- [ ] T16 Run the test suites and coverage on changed files (testing skill)
- [ ] T17 Security review of the auth, session and password path (security-reviewer agent)
- [ ] T18 Run QA against acceptance criteria (qa skill)
