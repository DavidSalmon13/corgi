# Tasks — A public landing page

> Epic: policy-lifecycle
> Spec: 2026-11-23-landing-page
> Branch: feat/policy-lifecycle/landing-page

## Public coverages API

- [ ] T1 Failing PublicCoveragesEndpointTest for AC5: signed out gets 200 with both covers (kind, label, role, annualPremiumCents matching CoverKind); no customer data; /api/me and /api/broker still 401 signed out (testing skill, TDD)
- [ ] T2 CoverageCatalogService + Coverage record in app.plainly.catalog (service)
- [ ] T3 PublicCoverageController GET /api/public/coverages and the /api/public open prefix in AuthFilter; T1 passes (controller)

## Web

- [ ] T4 landingCopy.test.ts first, then lib/landingCopy.ts: per-kind plain description, price line with per-day only when exact, three "How it works" steps, demo note (AC3, AC4, AC7); PublicCoverage type + fetch in api/client.ts
- [ ] T5 RouteGuards test first: signed out `/` renders the landing page, signed in still redirects by role (AC1); then HomeRedirect change
- [ ] T6 LandingPage.tsx: intro with Sign up / Sign in, What we offer with Term-wrapped terms, How it works, demo note, prices-unavailable fallback; light/dark, 390 px and 1280 px (AC2–AC4, AC7, AC8)
- [ ] T7 Plainly wordmark on SignIn and SignUp links to `/` (AC6)

## Docs

- [ ] T8 DECISIONS.md entry: landing page without a design screen, prices served from CoverKind, no buy button until Phase 2; README route list gains `/`

## Verification

- [ ] T9 Run both suites with coverage on the changed files (testing skill)
- [ ] T10 Run QA against AC1–AC8 signed out, as policyholder and as broker, at 1280 px light and 390 px dark (qa skill)
