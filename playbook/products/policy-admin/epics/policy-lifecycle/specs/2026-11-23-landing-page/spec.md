# A public landing page: what we offer, then sign in or sign up

> Epic: ../../epic.md
> Status: shaped
> Branch: feat/policy-lifecycle/landing-page
> Covers: /, /api/public/coverages, web/src/pages/LandingPage.tsx, web/src/lib/landingCopy.ts, api/src/main/java/**/catalog/**

## Overview

Today `/` is only a redirect, and a signed-out visitor lands straight on the sign-in form, with no idea what Plainly is or what it insures. This spec makes `/` a public landing page that needs no sign-in. It says in plain language:
- what Plainly is;
- which policies we offer: General liability (the base policy) and the Product liability endorsement, with their annual premiums;
- how getting covered works.

From there the visitor can **Sign in** or **Sign up**. A signed-in visitor to `/` still goes to their own home.

The prices come from the same source the broker prices with (`CoverKind`), through a small public endpoint, so the landing page can never show a different price from the one that's charged.

The page offers no purchase. Under the `real-sign-in` rules, the broker issues the policy and the owner signs up with the email the broker entered, and the page says so. Self-serve quote-and-buy stays Phase 2 (`roadmap.md`).

There is no landing screen in `design/plainly/`. The page reuses the Plainly tokens and the look of `SignIn.dc.html`. It serves the brief's rule that a policyholder understands their coverage in plain language, starting before they sign in.

## Goals & User Stories

- As a visitor, I see what Plainly insures and what it costs without signing in, so I know whether it's for my business.
- As a policyholder whose broker issued my policy, I find **Sign up** from the first page and know to use the email my broker has.
- As a returning user, I reach **Sign in** in one tap, and if I'm already signed in, `/` takes me to my home.

## Acceptance Criteria

1. AC1: Signed out, `/` shows the landing page; it no longer redirects to `/signin`. Signed in, `/` still goes to the role's home (`/broker/policies` or `/me`). Unknown routes keep today's behaviour.
2. AC2: The top of the page says in one or two plain sentences what Plainly is. It has two clear actions: **Sign up** (`/signup`) and **Sign in** (`/signin`). A broker uses the same Sign in.
3. AC3: A "What we offer" section lists each cover from `GET /api/public/coverages`:
   - name;
   - what it covers, in plain words;
   - annual premium, with the per-day price when it is a whole number of cents ("$730.00 a year · $2.00 a day").

   General liability is the base policy. Product liability is an endorsement that can be added mid-term, charged pro-rata from the day it starts. Every insurance term (premium, endorsement, pro-rata, general liability, product liability) is wrapped in `Term` with its "?" explanation.
4. AC4: A "How it works" section has three steps:
   1. your broker issues your policy with your email;
   2. you sign up with that email;
   3. you see your coverage, pay what's due and trace every amount.

   It states that policies can't be bought online yet.
5. AC5: `GET /api/public/coverages` needs no session and returns, for each cover: `kind`, `label`, `role` (BASE / ENDORSEMENT) and `annualPremiumCents`, with prices taken from `CoverKind`. It returns no customer or policy data and works when signed out. Every other `/api` route keeps its current auth rules.
6. AC6: Sign in and Sign up each link back to `/` through the Plainly wordmark.
7. AC7: The page is clearly a demo with a fictional company and fictional prices, worded like the notice on sign-in.
8. AC8: The jargon test (`web/src/jargon.test.ts`) passes. The page matches the Plainly look (no generic hero or template), in light and dark mode at 390 px and 1280 px, with no sideways scroll. If the coverages request fails, the page still renders, with a short "Prices are unavailable right now" note in place of the list.

## Technical Approach

- **API (new `app.plainly.catalog` module, controller → service; no repository, because the catalog is the `CoverKind` enum):**
  - `CoverageCatalogService.list()` maps `CoverKind` to `Coverage(kind, label, role, annualPremiumCents)`, with `BASE` → BASE and the rest → ENDORSEMENT.
  - `PublicCoverageController` maps `GET /api/public/coverages`.
  - `AuthFilter` adds `/api/public` to its open prefixes, alongside `/api/health` and `/api/auth`.
- **Web:**
  - `LandingPage.tsx` at `/`. `HomeRedirect` (`RouteGuards.tsx`) renders it when there's no session, and otherwise redirects as now.
  - `lib/landingCopy.ts` (pure, tested first) holds the per-kind plain description, the price line (reusing `formatCents`; the per-day price only when exact) and the step copy.
  - The fetch goes in `api/client.ts`, and `PublicCoverage` goes in `types.ts`.
  - The wordmark on `SignIn`/`SignUp` becomes a link to `/`.
- **Docs:** a dated `DECISIONS.md` entry with three points: a landing page without a design screen (reusing the SignIn look); prices served from `CoverKind` instead of being typed into the page; no buy button until Phase 2.

## Out of Scope

- Self-serve quote or purchase (Phase 2).
- Contact forms, broker directory, marketing analytics, SEO work.
- New products or a rating engine; prices stay flat as in `CoverKind`.
- Changes to sign-in or sign-up rules.

## Standards Applied

- [backend/layers](../../../../../../standards/backend/layers.md): the public endpoint is a thin controller over a service; no SQL is needed.
- [global/tech-stack](../../../../../../standards/global/tech-stack.md): React 18 + Vite + Tailwind v4; Spring Boot 4 / Java 21.
- [global/git-workflow](../../../../../../standards/global/git-workflow.md): branch and commit conventions.

## Changelog

| Date | Author | Type | Change | Ref |
|---|---|---|---|---|
| 2026-11-23 | david-schwartzman | created | Initial shaping | — |
