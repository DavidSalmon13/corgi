# Tasks — Landing page for guests

> Epic: policy-lifecycle
> Spec: 2026-11-23-landing-page
> Branch: feat/policy-lifecycle/landing-page

## Design

- [x] T1 Design L1 landing screen: design/plainly/L1-Landing.dc.html (Plainly tokens, 390 px, light/dark) + row in design/plainly/README.md

## Public coverages API

- [ ] T8 Failing PublicCoveragesEndpointTest for AC13: signed out gets 200 with both covers (kind, label, role, annualPremiumCents matching CoverKind), no customer data; /api/me and /api/broker still 401 signed out (testing skill, TDD)
- [ ] T9 CoverageCatalogService + Coverage record in app.plainly.catalog, PublicCoverageController GET /api/public/coverages, `/api/public` open prefix in AuthFilter; T8 passes (service + controller)

## Page

- [ ] T2 Write web/src/lib/landingCopy.ts: hero, three steps, "what your policy page shows you" lines (standard terms), no-buy-online line, reviewer box copy
- [ ] T3 Build web/src/pages/Landing.tsx from L1: header with Sign in, hero, How it works with Create your account, terms section via Term/Glossed, reviewer box from DEMO_ACCOUNTS, demo badge + footer + ThemeToggle
- [ ] T3.1 Share the demo badge and footer between AuthLayout and Landing without changing SignIn/SignUp's look
- [ ] T3.2 "What we offer" section in Landing.tsx: PublicCoverage type + fetch in api/client.ts, per-kind description and price line (per-day only when exact) in landingCopy.ts with tests first, Term-wrapped terms, prices-unavailable line on failure (AC12)
- [ ] T4 Replace HomeRedirect with Home in web/src/components/RouteGuards.tsx (pending → Waiting, signed in → homeFor, guest → Landing); wire `/` in web/src/App.tsx
- [ ] T5 SignIn.tsx: prefill a demo account from `?demo=<email>` when it is in DEMO_ACCOUNTS; ignore anything else

## Docs

- [ ] T10 DECISIONS.md: landing page for guests with a design screen, prices served from CoverKind through a public endpoint rather than typed into the page, no buy button until Phase 2

## Verification

- [ ] T6 Tests (testing skill): landingCopy.test.ts (wording, terms present), RouteGuards.test.ts (guest → landing, each role → its home), SignIn demo-param helper; jargon.test.ts passes over the new page
- [ ] T7 Run QA against acceptance criteria (qa skill), incl. 390 px and dark-mode screenshots
