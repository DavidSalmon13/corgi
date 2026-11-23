# QA Report — Landing page for guests

> Spec: spec.md
> Date: 2026-11-23 · QA by: david-schwartzman · Mode: setup
> Verdict: PASS WITH NOTES

## Acceptance Criteria

| AC | Criterion | Result | Evidence |
|---|---|---|---|
| AC1 | Guest at `/` sees landing; signed-in go home; calm loading, no flash | pass | `homeTarget` tests in `web/src/components/RouteGuards.test.ts` (guest → landing, policyholder → `/me`, broker → `/broker/policies`); `Home` returns `Waiting` while pending before choosing (`web/src/components/RouteGuards.tsx:41-46`); `App.tsx:32,72` routes `/` and `*`. Observed: guest at `/` gets L1. |
| AC2 | Header with mark, wordmark, Sign in visible at 390 px | pass | `Landing.tsx` header; 390 px dark screenshot shows Sign in top-right without scrolling |
| AC3 | Hero says what Plainly is in one sentence | pass | `HERO_TITLE`/`HERO_LEAD` in `landingCopy.ts`; test "says what Plainly is in the standard term" |
| AC4 | Three steps; Create your account → `/signup` with the broker-email line | pass | `STEPS` test (3 steps, ends with account creation); `HowItWorks` links `/signup` with `CREATE_ACCOUNT_NOTE` |
| AC5 | Standard terms with "?", no substitutes, jargon scan passes | pass | landingCopy test "marks general liability, premium, endorsement, pro-rata and ledger"; `jargon.test.ts` passes over `Landing.tsx` and `landingCopy.ts` |
| AC6 | No quote/buy button; one quiet line | pass | landingCopy test "never offers a quote or a purchase"; `NO_BUY_ONLINE` under the prices |
| AC7 | Reviewer box from `DEMO_ACCOUNTS`; Try as opens prefilled sign-in | pass | `reviewerEntries` test; `demoPrefill` tests in `SignIn.test.ts` (known fills, unknown/empty/missing ignored); observed `/signin?demo=maya%40…` shows Maya's email and password filled, not submitted |
| AC8 | Demo badge, Stripe test-mode footer, theme toggle; light and dark | pass | `DemoBadge`/`DemoFooter` (`components/DemoChrome.tsx`) used by Landing and AuthLayout; screenshots in light (1280 px) and dark (390 px) |
| AC9 | 390 px with 16–20 px gutter, no horizontal scroll; desktop content column | pass | `px-5` (20 px), `max-w-[430px]` → `sm:max-w-[760px]`; 390 px screenshot fits with no overflow; 1280 px shows a centred column with a two-column hero and two-up offers/reviewer tiles |
| AC10 | Matches L1 design, listed in README | pass | `design/plainly/README.md:24`; section order, card stack, step badges, offer cards and reviewer tiles follow `L1-Landing.dc.html` |
| AC11 | One h1, sections labelled, correct links/buttons, focus, keyboard "?" | pass | one `<h1>` (`Landing.tsx:72`); every `section` has `aria-labelledby` its `h2`; navigation uses `Link`; global `:focus-visible` outline (`theme.css`); `Term` is focusable with Enter/Space |
| AC12 | What we offer from the API; per-day only when exact; roles; failure line | pass | `offerPrice`/`offerRole`/`offerDescription` tests; observed 1280 px: "$1,200.00 a year", "$730.00 a year · $2.00 a day", "The policy" / "Endorsement ?"; with the API down, the page rendered with "Prices aren't available right now" (earlier run this session) |
| AC13 | Public endpoint, no session, kind/label/role/premium; other routes 401 | pass | `PublicCoverageEndpointTest` 3/3; observed `curl /api/public/coverages` → 200 with both covers, `/api/me` → 401, `/api/broker/policies` → 401 |

## Checks Run

- build: `cd web && npm run build` (tsc + vite) — pass
- lint: no lint script in `web/` or `api/` — n/a
- typecheck: part of `npm run build` — pass
- tests: `cd web && npx vitest run` — pass (369 passed, 0 failed)
- tests: `cd api && ./gradlew test` — pass (525 passed, 0 failed)

## Issues Found

| # | Severity | Issue | Route |
|---|---|---|---|
| 1 | low | The preview card's "product liability endorsement's" puts the term's "?" between "endorsement" and "'s", which reads a little awkwardly ("endorsement ? 's start") | copy tweak in `landingCopy.ts` `PREVIEW.why` (e.g. "moved the start of your product liability endorsement a week earlier") |

## Notes

- Headless Chrome won't go narrower than ~500 px, so 390 px shots were rendered in a 390 px iframe. In that iframe the light-mode shot sometimes caught the page before the session call settled ("Loading…"). Dark at 390 px and light at 1280 px rendered fully, and the layout and tokens are the same in both themes.
- Hygiene: all tasks except T7 (this run) were `[x]` before QA. No PR exists yet for `feat/policy-lifecycle/landing-page`. The spec's `> Status:` is still `shaped`, which the pr flow moves on.
- Coverage on changed files (web): `landingCopy.ts` 100% lines. Render code (`Landing.tsx`, `SignIn.tsx`) is covered by the jargon source scan and runtime checks rather than component tests, as elsewhere in the repo.
