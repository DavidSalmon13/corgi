# QA Report — Landing page for guests

> Spec: spec.md
> Date: 2026-11-23 · QA by: david-schwartzman · Mode: setup
> Verdict: PASS WITH NOTES

Rerun after the final clean-ups on branch `ccr-9430ac06-v0i0a3`: the "Your cover · Juniper & Rye Bakery" preview card was
removed from the hero, a broker contact card (Sam Reyes, email and phone) was added to the landing page, P1 and P6, and
back links were added to sign-in, sign-up and the broker's new-policy / rewrite form. Related ACs in other specs that the
same change touches are checked in their own section below.

Screens were driven with Playwright (Chromium) against the local app (`npm run dev` on :5173, `./gradlew bootRun` on
:8080, V14 migrated) at 390 px and 1280 px, in light and dark: 7 screens × 4 = 28 captures.

## Acceptance Criteria

| AC | Criterion | Result | Evidence |
|---|---|---|---|
| AC1 | Guest sees landing; signed-in owner → `/me`, broker → `/broker/policies`; calm loading | pass | `RouteGuards.test.ts` (covers AC1, T4) green; observed: signing in as Maya landed on `/me`, as Sam on `/broker/*` |
| AC2 | Header with mark, wordmark, "Sign in" visible at 390 px | pass | Observed `landing-m-light`: mark, "Plainly" and "Sign in" in the first 60 px |
| AC3 | Hero says in one plain sentence what Plainly is | pass | `landingCopy.test.ts` "says what Plainly is…"; observed: hero h1 + lead unchanged, now full-width without the preview card |
| AC4 | Three steps; "Create your account" to `/signup` with the email note | pass | `landingCopy.test.ts` "has three steps…"; observed in all 4 landing captures |
| AC5 | Policy-page section uses premium, endorsement, pro-rata, ledger with "?"; jargon scan passes | pass | `landingCopy.test.ts` terms tests + `jargon.test.ts` green; observed "?" on premium, ledger, reversal |
| AC6 | No quote/buy button; one quiet "ask your broker" line | pass | `landingCopy.test.ts` "never offers a quote or a purchase" green; the line is now followed by the broker contact card (`Landing.tsx` Offer section) |
| AC7 | Reviewer box from `DEMO_ACCOUNTS`, prefilled sign-in | pass | `landingCopy.test.ts` "builds one reviewer entry per demo account…" green; observed both entries |
| AC8 | Demo badge, Stripe test-mode note, theme toggle; right in light and dark | pass | Observed in `landing-{m,d}-{light,dark}`: badge, footer note and toggle present; contact card legible in dark |
| AC9 | 390 px with 16–20 px gutter, no horizontal scroll; desktop widens to a column | pass | Script: `scrollWidth > clientWidth` false on all 28 captures; desktop shows offers in two columns, contact on one row |
| AC10 | Matches `design/plainly/L1-Landing.dc.html` | pass (with note) | Matches except the hero, which no longer has the P1 miniature the design shows (design line 40). Removed at the owner's request; recorded in DECISIONS.md 2026-11-23 "Final clean-ups". See Notes |
| AC11 | One h1, labelled sections, correct links, visible focus, keyboard "?" | pass | Script: exactly one h1 on every capture; 5 `aria-labelledby` sections in `Landing.tsx`; global `:focus-visible` at `web/src/theme.css:95`; contact items are real `mailto:`/`tel:` anchors |
| AC12 | "What we offer" from the public API with prices and per-day rule | pass | `landingCopy.test.ts` "what we offer" suite green; observed $1,200.00 / $730.00 · $2.00 a day |
| AC13 | `GET /api/public/coverages` public, no customer data, other routes keep auth | pass | `PublicCoveragesEndpointTest` green in the full API suite |

### Related ACs in other specs touched by this change

| Spec · AC | Criterion | Result | Evidence |
|---|---|---|---|
| issue-policy · AC10 | P1 shows … the broker contact | pass | `MyCoverTest` asserts `broker.name`, `broker.email` = `sam@plainly.test`, `broker.phone` = `(415) 555-0142`; observed P1: card with `mailto:sam@plainly.test` and `tel:+14155550142` (1 each, all 4 captures) |
| cancel-refund · P6 | P6 tells the owner who to ask | pass | Observed `p6-*` on a fictional cancelled policy (Fernwood Pottery Studio, GL-2026-0017): contact card with both links, after "How we worked it out" |
| real-sign-in · AC3 | `/signin` links to `/signup` with the broker line | pass | Observed `signin-*`: "New here? Your broker set up your policy. Create your account" unchanged; new "‹ Home" link returns to `/` (script) |
| real-sign-in · sign-up | `/signup` reachable and usable | pass | Observed `signup-*`; "‹ Home" returns to `/` (script) |
| more-policies · AC1 / issue-policy · AC2 | B2 new policy / rewrite form | pass | Observed `b2-new-*`, `b2-rewrite-*`: "‹ All policies" goes to `/broker/policies`, "‹ Back to the policy" goes to `/broker/policies/3` (script); Cancel uses the same target |

## Checks Run

- build: `npx vite build` (web) — pass
- lint: no lint script in `web/package.json`, none configured for `api/` — not run
- typecheck: `npx tsc --noEmit` (web) — pass
- tests: `npx vitest run` (web) — pass (27 files, 397 tests); `./gradlew test` (api) — pass (full suite, exit 0)

## Issues Found

(none)

## Notes

- **AC10 now departs from the design on purpose.** The L1 design still shows the P1 miniature beside the hero. The spec
  and design weren't changed (QA doesn't edit them). If the departure should be part of the contract, run
  `/spec-changes` on this spec to reword AC10 and update `design/plainly/L1-Landing.dc.html`.
- **Hygiene.** All tasks in `tasks.md` are `[x]` and PR #95 is recorded. `spec.md` still says `> Status: shaped`
  although the spec has shipped; this was already the case before this run.
- **Test data.** To see P6, the run created one fictional customer in the local database (Fernwood Pottery Studio LLC,
  owner Jonah Fern, `qa.owner.<timestamp>@example.test`), issued a policy and cancelled it from Dec 1, 2026. Local only;
  `docker compose down -v` removes it.
- **Pre-existing, not from this change:** P6's page title and its first card both say "Your cover ends on Dec 1, 2026".
