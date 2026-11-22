# QA Report — Issue a policy

> Spec: spec.md
> Date: 2026-11-22 · QA by: david-schwartzman · Mode: setup
> Verdict: PASS WITH NOTES

## Acceptance Criteria

| AC | Criterion | Result | Evidence |
|---|---|---|---|
| AC1 | Sign-in with demo notice; role-based landing; each role refused the other's routes (403 API, redirect SPA) | pass | `DemoAuthTest` (7 tests: 401 without session, 403 cross-role, HttpOnly cookie); web `client.test.ts`, `RouteGuards.test.ts`. Runtime in Chrome: signed out `/broker/policies` and `/me` → `/signin`; broker `/me` → `/broker/policies`; policyholder `/broker/policies/1` → `/me`. SignIn shows "Continue as Sam, the broker", one entry per owner, the demo badge and the Stripe test-mode note (`web/src/pages/SignIn.tsx`) |
| AC2 | B2 collects four required fields, valid email; summary + per-field errors; nothing saved | pass | `IssuePolicyServiceTest.everyMissingFieldGetsItsOwnMessageAndNothingIsSaved`, `anEmailWithoutADomainIsRejected`; `BrokerIssueEndpointsTest.anEmptyFormListsEveryProblemInOneAnswer`. Runtime: empty submit showed "5 things to fix before you can save." with a linked list and a message under each field |
| AC3 | One-year period from start, "Sep 1, 2026 → Sep 1, 2027" + hint; end-exclusive NY days; leap = 366 | pass | `ProRataTest` (endDateIsNotCounted, leapTermHas366Days), `IssuePolicyServiceTest.aTermSpanningFeb29Has366Days`, `TodayTest.todayIsTheNewYorkCalendarDay`; web `dates.test.ts`. Runtime: period field read "Sep 1, 2026 → Sep 1, 2027" with "One year. The end date isn't counted." |
| AC4 | Base cover always on; extra cover optional; one-line plain description each | pass | `IssuePolicyServiceTest.previewWithExtraCoverAddsAFullYearOfProductLiability`; runtime: base shown ticked and fixed, extra a checkbox, descriptions as in B2 (`NewPolicyPage.tsx` CoverChoice) |
| AC5 | Live server preview, saves nothing; line + calculation, "Customer pays", "Customer will see"; no-date message | pass | `IssuePolicyServiceTest.previewSavesNothing`, `BrokerIssueEndpointsTest.previewPricesEachCoverWithItsArithmetic`, `previewWithoutAStartDateExplainsWhatIsMissing`; web `issueCopy.test.ts`. Runtime: panel read "Base cover, full year · $1,200.00 × 365 ÷ 365 · +$1,200.00 · Customer pays $1,200.00 · You're covered from Sep 1, 2026 to Sep 1, 2027. The price is $1,200.00 for the year."; with no date: "Add a start date and we'll show the price here, before anything is saved." |
| AC6 | Create writes customer, policy (GL-year-seq, Active), covers, one ISSUE PTX, one charge per cover, atomically; amounts = preview | pass | `IssuePolicyServiceTest.issuingWritesThePolicyItsCoversOneTransactionAndOneChargePerCover` (per-line amounts and total equal preview), `IssueAtomicityTest`, `ProRataTest.roundsHalfUpOnceToTheCent`. Runtime: two policies created via the B2 UI (GL-2026-0001, GL-2026-0002), redirected to their B3 page |
| AC7 | DB rejects UPDATE/DELETE on ledger entries; integration test | pass | `LedgerAppendOnlyTest` (updateIsRefused, deleteIsRefused, truncateIsRefused), Testcontainers Postgres 16 |
| AC8 | B1 list: customer, owner, number, status pill, "$X due" in accent, last action, summary, All/Owes chips, empty state | pass | `BrokerPolicyReadTest.theListShowsEachPolicyWithItsBalanceAndLastAction`; web `policyCopy.test.ts`. Runtime: "2 policies · 2 owe money", "$1,930.00 due" in accent, "Issued · Nov 22", chips "All · 2" / "Owes money · 2"; empty state "No policies yet … Add your first customer" observed on an empty DB |
| AC9 | B3 header, status, balance; timeline (month axis, bar per cover, Today); ledger table with refs and caption; no action buttons | pass | `BrokerPolicyReadTest.thePolicyViewHasTheHeaderCoversAndEveryLedgerLineWithItsSource`; web `timeline.test.ts`, `ledgerRows.test.ts`. Runtime: "Active · balance due", Nov→Oct month ticks, "Today, Nov 22" marker, columns Date/Line/Calculation/Amount/Balance/References with LE-000n + PTX-000n, caption present; `PolicyWorkspace.tsx` renders no Add cover/Correct/Cancel |
| AC10 | P1 at 390 px: business, "$X due" and why, protection sentence, period + timeline, broker contact, no Pay | pass | `MyCoverTest` (own business only; amount and reason); web `myCoverCopy.test.ts`. Runtime at 390 px: "$1,200.00 due · Your first year of cover.", protection sentence with "(bakery and café)" and, for Fernwood, "or because of something you sell", period + bars + Today, "Your broker, Sam Reyes", no pay action |
| AC11 | Insurance terms wrapped in an explained Term or avoided | pass | web `jargon.test.ts` scans every `.tsx` screen and component for premium / pro-rata / endorsement / effective date / underwriting / deductible outside a Term explanation (with a self-check). Inspection: Term on General Liability, Base/Extra cover, Starts on, Cover period, Price of the cover, the per-cover sums, Ledger, Calculation, References |
| AC12 | Plainly tokens, Source Sans 3, tabular nums, dark mode, no horizontal scroll at 390 px; `$1,200.00`, `+$`, true minus | pass | web `money.test.ts` (U+2212, +$, cents never via floats). Chrome sweep: SignIn, B1, B2, B3, P1 × 390/1280 px × light/dark = 20/20 with scrollWidth = viewport, no "-$" in page text, body background #F8F4EC / #15181F, font "Source Sans 3", no console errors |

## Checks Run

- build: `cd api && ./gradlew build` — pass; `cd web && npm run build` (tsc + vite) — pass
- lint: not configured (no lint task in `api/build.gradle.kts`, no `lint` script in `web/package.json`)
- typecheck: `cd web && npm run typecheck` — pass
- tests: `cd api && ./gradlew test --rerun` — pass (50 passed, 0 failed); `cd web && npm test` — pass (49 passed, 0 failed)

## Issues Found

| # | Severity | Issue | Route |
|---|---|---|---|
| 1 | low | With extra cover, the "Customer will see" sentence adds ", including the products you sell" to the AC5 template. It's a deliberate extension that keeps the template's wording; confirm or trim | note; `/spec-changes` if the wording should be fixed in the spec |
| 2 | low | B2 pre-fills the start date from the browser's local date, not America/New_York. The server validates and prices in NY time, so only the default can differ, near midnight | note |
| 3 | low | No linter is configured for `api/` or `web/` | note |

## Notes

- Hygiene: T21 (this run) is the only open task, and it will be ticked with this report. Spec `> Status:` still reads `shaped` although the work is built. No PR yet (`gh pr list` empty), no `> PR:` line, and no hotfix rows in the Changelog.
- Runtime evidence (redirects, B2 flow, 20-way screen sweep) came from Playwright driving local Chrome against the dev servers in this session. The scripts live outside the repo, so a reviewer re-running QA needs the same manual pass or a committed e2e suite.
- Screens show "how we got this" in the broker ledger (calculation + references on every line). The policyholder's breakdown (P3/P4) belongs to later specs, per DECISIONS.md, 2026-11-22.
- All demo data is fictional (Juniper & Rye Bakery LLC / Maya Okafor, Fernwood Florist LLC / Priya Natarajan, Sam Reyes).
