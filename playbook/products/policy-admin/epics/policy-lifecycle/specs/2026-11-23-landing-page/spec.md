# Landing page for guests

> Epic: ../../epic.md
> Status: shaped
> Branch: feat/policy-lifecycle/landing-page
> Covers: /, /api/public/coverages, api/src/main/java/**/catalog/**, web/src/pages/Landing.tsx, web/src/lib/landingCopy.ts, web/src/components/RouteGuards.tsx#HomeRedirect, design/plainly/L1-Landing.dc.html

## Overview

A public landing page at `/` for anyone not signed in. It replaces the redirect to a bare sign-in form. The page says what Plainly is and how someone becomes a policyholder: the broker issues the policy, then the owner creates an account with that email. It shows the policies we offer and their premiums, without signing in, and what a policy page will show them, using standard insurance terms with their "?" explanations, and gives reviewers a one-tap way into the demo accounts. Signed-in visitors are still sent straight to their home.

This spec doesn't advance the epic's money success criteria. It serves the product mission's "plain language comes first" and its secondary users, the Corgi reviewers. It lives in policy-lifecycle because that epic owns sign-in and sign-up. The tech plan has no entry for it. Its only API is a small public, read-only list of the covers we offer, so the prices on the page come from the same place the broker's prices do.

## Goals & User Stories

- As a **guest** (a small-business owner sent a link, or just curious), I understand in a few seconds what Plainly is and how I get access, with no insurance knowledge.
- As a **policyholder whose broker issued my policy**, I find "Create your account" and understand it needs the email my broker used.
- As a **guest**, I see which policies Plainly offers and what each costs, without signing in.
- As a **returning user** (policyholder or broker), I reach Sign in from the top of the page in one tap.
- As a **Corgi reviewer**, I can get into either demo role in one tap from the first screen.

## Acceptance Criteria

1. AC1: A guest opening `/` sees the landing page. A signed-in policyholder opening `/` lands on `/me` and a broker on `/broker/policies`, as before. While the session loads, a calm loading state shows, with no flash of the landing page.
2. AC2: The header shows the Plainly mark and wordmark and a "Sign in" link to `/signin`, visible without scrolling on a 390 px phone.
3. AC3: The hero says in one plain sentence what Plainly is: small-business General Liability insurance where you can see your coverage, what you owe and why.
4. AC4: A three-step "How it works" section explains: your broker issues your policy → you get an email → you create your account with that same email. The "Create your account" button to `/signup` sits in this section, with a line saying it needs the email your broker used.
5. AC5: A "What your policy page shows you" section uses the standard terms general liability, premium, endorsement, pro-rata and ledger, each with its "?" explanation from `GLOSSARY` via `Term`/`Glossed`. It uses no home-made substitutes, and the `jargon.test.ts` scan passes over the new page.
6. AC6: There is no "Get a quote" or "Buy" button. One quiet line says buying online isn't available yet and to ask your broker.
7. AC7: A reviewer box lists the demo accounts from `DEMO_ACCOUNTS`, without duplicating that data. "Try as Maya" or "Try as Sam" opens `/signin` with that account's email and the demo password filled in, ready to submit.
8. AC8: The page keeps the demo badge ("Demo · fictional people and money"), the Stripe test-mode footer note and the theme toggle, and it looks right in light and dark.
9. AC9: The layout works at 390 px with a 16–20 px gutter and no horizontal scroll. On desktop it widens into a content column rather than a stretched mobile page.
10. AC10: The page matches `design/plainly/L1-Landing.dc.html`, which exists in the design folder and is listed in `design/plainly/README.md`.
11. AC11: The page is accessible: one `h1`, sections labelled by their headings, links and buttons used correctly, visible focus, and "?" terms usable by keyboard.
12. AC12: A "What we offer" section lists every cover from `GET /api/public/coverages`. For each cover it shows the name; what it covers, in plain words; and the annual premium, with the per-day price only when it is a whole number of cents ("$730.00 a year · $2.00 a day"). General liability is shown as the base policy. Product liability is shown as an endorsement that can be added mid-term, charged pro-rata from the day it starts. The terms are wrapped in `Term`. If the request fails, the rest of the page still renders, with one quiet line saying prices are unavailable right now.
13. AC13: `GET /api/public/coverages` works with no session and returns, for each cover, `kind`, `label`, `role` (BASE / ENDORSEMENT) and `annualPremiumCents`, taken from `CoverKind`. It returns no customer or policy data. Every other `/api` route keeps its current auth rules (401 signed out).

## Technical Approach

- **Design first (L1):** add `design/plainly/L1-Landing.dc.html` in the canvas format and Plainly tokens of `SignIn.dc.html` (`--bg/--card/--ink/--accent…`, Source Sans 3, `dark` toggle), at mobile 390 px, plus a row in `design/plainly/README.md`. Keep it calm and editorial, not a SaaS-template hero: a warm card stack shows a miniature P1 "Your cover" snippet with an amount and a "how we got this" hint, beside the three numbered steps.
- **Routing:** `HomeRedirect` in `RouteGuards.tsx` becomes `Home`. While the session is pending it shows `Waiting`; a signed-in user gets `<Navigate to={homeFor(role)}>`; a guest gets `<Landing />`. `App.tsx`'s `/` route uses it. The catch-all `*` keeps redirecting to `/`, which now shows the landing page to guests. A pure helper `homeTarget(session) → 'landing' | path` keeps this unit-testable, like `homeFor`.
- **Page:** `web/src/pages/Landing.tsx` reuses `Mark`, `ThemeToggle`, `Term`/`Glossed`, `GLOSSARY`, and `DEMO_ACCOUNTS`/`DEMO_PASSWORD` from `SignIn.tsx`. Its shell is a wider sibling of `AuthLayout` with the same badge and footer. Extract the shared badge and footer into small components if that keeps the code DRY, without changing how SignIn and SignUp look.
- **Copy:** every sentence lives in `web/src/lib/landingCopy.ts`. This follows the `*Copy.ts` pattern, and `jargon.test.ts`'s `./lib/*Copy.ts` glob picks it up.
- **Demo prefill:** `SignIn.tsx` reads `?demo=<email>`. If the email is in `DEMO_ACCOUNTS`, it fills in the email and `DEMO_PASSWORD`, like its "Use these" button. Unknown values are ignored, and nothing submits automatically.
- **Public coverages API (new `app.plainly.catalog` module, controller → service; no repository, because the catalog is the `CoverKind` enum):**
  - `CoverageCatalogService.list()` maps `CoverKind` to `Coverage(kind, label, role, annualPremiumCents)`.
  - `PublicCoverageController` maps `GET /api/public/coverages`.
  - `AuthFilter` adds `/api/public` to its open prefixes, next to `/api/health` and `/api/auth`.
  - There is no migration. The existing `useSession` query still decides between guest and signed in.
- **What we offer:** `landingCopy.ts` holds the plain description for each kind and the price line, reusing `formatCents` and showing the per-day price only when exact. `Landing.tsx` fetches the list through `api/client.ts` (`PublicCoverage` in `types.ts`).

## Out of Scope

- Self-serve quote-and-buy (Phase 2). The page lists covers and premiums but offers no purchase.
- Marketing pages beyond `/` (about, pricing, FAQ), and SEO or meta tags beyond a title.
- Changing where sign-out goes (it stays `/signin`).
- Email confirmation and password reset.

## Standards Applied

- [backend/layers](../../../../../../standards/backend/layers.md): the public coverages endpoint is a thin controller over a service.
- [global/tech-stack](../../../../../../standards/global/tech-stack.md): React 18 + Vite + Tailwind v4 tokens; Spring Boot 4 / Java 21.

## Changelog

| Date | Author | Type | Change | Ref |
|---|---|---|---|---|
| 2026-11-23 | david-schwartzman | created | Initial shaping | — |
| 2026-11-23 | david-schwartzman | change | Added "What we offer" with premiums from a public `GET /api/public/coverages` (AC12, AC13); replaced the duplicate spec merged in #93 | #93 |
