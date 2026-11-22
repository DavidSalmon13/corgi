# Real sign-in

> Epic: ../../epic.md
> Status: done
> Branch: feat/policy-lifecycle/real-sign-in
> Covers: /signin, /signup, api/src/main/java/**/auth/**, api/src/main/resources/db/migration/V5__accounts_and_sessions.sql, web/src/pages/SignIn.tsx, web/src/pages/SignUp.tsx, web/src/api/client.ts

## Overview

Replaces the simulated role switcher from issue-policy with real email-and-password sign-in, the way a broker-led insurance portal works. Brokers don't sign up: their accounts are created for them (seeded). A policyholder can't buy online. Their broker issues the policy and enters the owner's email; the owner then **signs up** with that email and a password of their choice, and from then on signs in normally. Sessions are random tokens stored (hashed) in Postgres, so a cookie can't be edited to become someone else. Role rules stay as they are (`/api/broker/**` needs a broker, `/api/me/**` a policyholder, scoped to their own customer). A seeded, already-signed-up policyholder means reviewers get demo credentials for both roles. Self-serve quote-and-buy, email confirmation and password reset are Phase 2.

## Goals & User Stories

- As a **broker**, I sign in with my email and password and land on my policies.
- As a **business owner** whose broker just issued my policy, I sign up with the email my broker has on file and a password I choose, and land on my cover.
- As a **policyholder**, I sign in with my email and password, and sign out when I'm done.
- As a **broker**, I can see on a policy whether the customer has signed up yet, so I know whether to nudge them.
- As a **reviewer**, I find demo credentials for both roles on the sign-in page and in the README.

## Acceptance Criteria

1. AC1: `/signin` shows an email and password form in the Plainly sign-in layout (demo badge, wordmark, footer, theme toggle). A correct email and password signs you in; a broker lands on `/broker/policies`, a policyholder on `/me`. Emails match case-insensitively and ignoring surrounding spaces.
2. AC2: A wrong password and an unknown email both show the same message: "That email and password don't match. Check them and try again." The API answers 401 in both cases and never says which part was wrong.
3. AC3: `/signin` links to `/signup` ("New here? Your broker set up your policy. Create your account"). `/signup` asks for email, password and confirm password, with plain hints: "Use the email your broker has on file for your business" and "At least 10 characters".
4. AC4: Sign-up succeeds only when the email is the owner email on a customer whose owner has no account yet. It sets the password, signs the owner in and lands on `/me`.
5. AC5: Sign-up refuses, with a field error and no account created: an email no broker has entered ("We can't find a policy for that email. Check with your broker that they used this address."); an email that already has an account ("This email already has an account. Sign in instead", linking to `/signin`); a password under 10 characters; passwords that don't match. Broker emails can never be used to sign up.
6. AC6: The broker can't issue a second customer with an owner email another customer already uses (field error on the B2 form: "Another customer already uses this email."), so each email belongs to exactly one person.
7. AC7: The session cookie (`plainly_session`) is httpOnly, SameSite=Lax, Secure when configured for HTTPS, and holds a random token. The database stores only the token's SHA-256 hash. Sessions expire after 7 days. An expired, unknown or edited token is treated as signed out (401), and the old `plainly_demo_user` cookie no longer works.
8. AC8: Signing out ends the session on the server (the token stops working even if replayed) and clears the cookie. Signing in again creates a new token.
9. AC9: Passwords are stored only as BCrypt hashes and never returned by any endpoint or written to logs.
10. AC10: Role rules from issue-policy still hold: a policyholder gets 403 on `/api/broker/**` and is redirected in the SPA, the reverse too, and `/api/me/**` only ever shows the signed-in owner's own customer.
11. AC11: The broker's policy workspace (B3) shows whether the owner has signed up: "Maya hasn't created her account yet" / "Signed up Nov 22, 2026" (using the owner's name).
12. AC12: Demo data: the broker Sam Reyes (`sam@plainly.test`) and the policyholder Maya Okafor of Juniper & Rye Bakery LLC (`maya@juniperandrye.test`, with an issued policy) can both sign in with the demo password. The sign-in page shows both in a "Demo accounts" box, and the README lists them. Seeding runs at startup, only adds what's missing, and is off in tests.
13. AC13: `DECISIONS.md` replaces "Demo sign-in is simulated" with a dated entry. Sign-in is real, sign-up matches the broker-entered email with no email confirmation, and the risk (anyone who knows an owner's email before they sign up could claim it) is accepted for the trial. Production would send a confirmation link. The README moves sign-in out of "simulated".

## Technical Approach

**Schema (`V5__accounts_and_sessions.sql`):**
- `app_users` gains `email text`, `password_hash text` and `signed_up_at timestamptz`, plus a unique index on `lower(email)`.
- Sam Reyes gets `sam@plainly.test` and the BCrypt hash of the demo password as a literal.
- New table `sessions` (id, `token_hash bytea unique`, `user_id` → app_users, `created_at`, `expires_at`).
- A unique index on `lower(customers.owner_email)`. Before creating it, check existing local and Railway data for duplicates; tests and dev DBs are recreated anyway.

**Dependencies:** `org.springframework.security:spring-security-crypto` for `BCryptPasswordEncoder` only. Full Spring Security is not added, so the existing filter, the cookie model and all MockMvc tests stay simple.

**auth package (replaces the Demo* classes):**
- `Passwords`: wraps BCrypt (strength 10). `matches` runs against a dummy hash when the email is unknown, so timing doesn't reveal which emails exist.
- `Sessions`: `create(userId)` returns a token (32 bytes from `SecureRandom`, base64url) and stores its SHA-256 hash. `find(token)` returns the user while `expires_at > now()`; `end(token)` deletes the row. Sessions are not ledger data, so deleting them is fine.
- `Accounts` (from `DemoUsers`): `findByEmail`, `signUp(email, password)` (one transaction, `select … for update` on the unclaimed owner row) and `find(id)`.
- `AuthFilter` (from `DemoAuthFilter`): same path-to-role rules, reading `plainly_session` through `Sessions.find`.
- `AuthController` at `/api/auth`: `POST /signin {email,password}`, `POST /signup {email,password,confirmPassword}`, `DELETE /session`, `GET /session`. The `/api/demo/*` endpoints are removed.
- `SignedInUser` record (from `DemoUser`): id, role, displayName, subtitle, customerId, email. Update `@RequestAttribute` users in `MyCoverController` and `BrokerIssueController`.
- Cookie built as in `DemoSessionController.cookie()`, with `secure` from `plainly.session.secure-cookie` (`${SESSION_COOKIE_SECURE:false}`; set it to true on Railway).

**CSRF:** SameSite=Lax plus JSON-only mutation endpoints is enough for the trial (a cross-site form can't send `application/json` with the cookie). This goes in DECISIONS.

**Issue policy:** `IssuePolicyService.validate` adds the duplicate owner-email check (case-insensitive) as an `ownerEmail` field error. It still creates the POLICYHOLDER `app_users` row, with `email = owner email` and no password, so sign-up fills in `password_hash` and `signed_up_at` on that row. `PolicyQueries` / `PolicyViews` expose `ownerSignedUpAt` for B3.

**Demo seed:** `DemoSeeder` is an `ApplicationRunner` behind `plainly.demo.seed` (default `true`, `false` in `@IntegrationTest`). If no customer has `maya@juniperandrye.test`, it issues her policy through `IssuePolicyService.issue` (start = today − 21 days, base cover, created by Sam) so the ledger is real. It then sets her password hash and `signed_up_at`. The demo password comes from `plainly.demo.password` (default `plainly-demo-2026`).

**Web:**
- `api/client.ts`: `fetchSession` reads `/api/auth/session`.
- `types.ts`: `DemoUser` becomes `SignedInUser`.
- `SignIn.tsx`: rebuilt as a form (labelled inputs, a show/hide password toggle, a pending state, an `alert` for errors), plus the Demo accounts box with a "Use these" button that fills the form.
- New `SignUp.tsx` with the same layout and field-level errors from the API `fields` map, as `NewPolicyPage` does.
- `App.tsx`: `/signup` route.
- Sign-out (wherever it lives now) calls `DELETE /api/auth/session` and clears the query cache.
- B3: a signed-up line in the customer block.

**Tests:**
- An `AuthTestSupport.signIn(mvc, email, password)` helper returns the session cookie.
- A `TestData.uniqueEmail()` helper, since owner emails are now unique across customers.
- Move `MyCoverTest`, `BrokerIssueEndpointsTest`, `BrokerPolicyReadTest` and `DemoAuthTest` onto it. `DemoAuthTest` becomes `AuthTest`. `IssuePolicyServiceTest` and `IssueAtomicityTest` switch to unique emails.

## Out of Scope

- Email confirmation, password reset, changing email, MFA, SSO.
- Rate limiting or lockout after failed attempts. The DECISIONS entry names it as a production must.
- Broker self-signup or broker admin (adding brokers).
- Self-serve quote-and-buy (Phase 2).
- Multiple policies or multiple users per customer.

## Standards Applied

- [global/git-workflow](../../../../../../standards/global/git-workflow.md): branch naming and commits.
- [global/tech-stack](../../../../../../standards/global/tech-stack.md): Spring Boot + JdbcClient + Flyway on the API, React + TanStack Query + Tailwind on the web.

## Changelog

| Date | Author | Type | Change | Ref |
|---|---|---|---|---|
| 2026-11-22 | david-schwartzman | created | Initial shaping | — |
| 2026-11-22 | david-schwartzman | change | Implemented T1–T18; QA PASS WITH NOTES | [qa-report](qa-report.md) |
