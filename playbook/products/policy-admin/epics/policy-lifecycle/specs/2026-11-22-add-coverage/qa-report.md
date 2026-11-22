# QA Report — Add coverage

> Spec: spec.md
> Date: 2026-11-22 · QA by: david-schwartzman · Mode: setup
> Verdict: PASS WITH NOTES

## Acceptance Criteria

Runtime checks ran against this branch: API on :8081 (its own `plainly_addcov` database, with the demo seed's policy Nov 1, 2026 → Nov 1, 2027) and web on :5181, driven by headless Chrome. Screens were checked in light and dark mode at 1280 and 390 px.

| AC | Criterion | Result | Evidence |
|---|---|---|---|
| AC1 | B3 "Add cover" button only for an active policy without the extra cover | pass | `PolicyWorkspace.tsx` `canAddCover` (status ACTIVE and no PRODUCT_LIABILITY); runtime: the button was shown before the add, linked to `/broker/policies/1/change`, and was gone after it (count 0) |
| AC2 | B4 Add cover tab layout and copy | pass | `ChangePolicyPage.tsx:77-78` heading and subtitle, `:224` hint; runtime text: back link "‹ Juniper & Rye Bakery LLC · GL-2026-0001", option "Product liability ($730.00 a year, $2.00 a day)", "Runs to the end of the cover period, Nov 1, 2027.", default start today (Nov 22); no Correct or Cancel tabs; `addCoverCopy.test.ts` |
| AC3 | Live preview, saves nothing; worked examples | pass | `AddCoverageServiceTest`: `fromOct15ItCharges321DaysOfTheYearlyPrice`, `startingOnTheLastDayChargesOneDay`, `startingOnThePolicyStartChargesTheFullYear`, `aTermSpanningFeb29Divides366Days`, `previewSavesNothing`; `AddCoverageEndpointsTest.previewShowsTheChargeAndItsArithmetic`; runtime: Jan 15 → "$730.00 × 290 ÷ 365 · charged by the day +$580.00", "You owe $580.00 … 290 days from Jan 15 at $2.00 a day." |
| AC4 | 422 field errors with the exact messages | pass | Service tests for all six cases (incl. `aCancelledPolicyCannotTakeCover`); endpoint tests; runtime 422 bodies for no date, on the policy end, BASE kind and "already has", and B4 showed "can't start before the policy does (Nov 1, 2026)" under the field |
| AC5 | One DB transaction: ADD_COVERAGE tx, coverage row, linked CHARGE with calc | pass | `addingRecordsOneTransactionOneCoverAndOneLinkedCharge`, `AddCoverageAtomicityTest.ifTheChargeCannotBePostedNoTransactionOrCoverIsLeftBehind`, `anInvalidAddSavesNothing`; runtime balance went $1,200.00 → $1,780.00 (+ the previewed $580.00) |
| AC6 | Saved banner on B3, timeline bar, ledger line with LE/PTX and calc | pass | runtime (dark, 390 px): "Extra cover added. Maya now owes $1,780.00.", product liability bar from Jan 15, ledger "Extra cover from Jan 15 · $730.00 × 290 ÷ 365 · LE-0002 PTX-0002"; banner gone after reload |
| AC7 | Row lock: two simultaneous adds → one coverage, one charge | pass | `AddCoverageServiceTest.twoSimultaneousAddsMakeOneCoverAndOneCharge` |
| AC8 | Save disabled and preview dimmed while repricing; preview-failed message; focus summary once | pass | inspection: `ChangePolicyPage.tsx:134` `repricing`, `:264` `opacity-60`, `:308` `disabled={save.isPending \|\| repricing}`, `:292-294` "We couldn't work out the price just now." + "Try again", `:157` focus once per failed save, `:175` summary `role="alert"` |
| AC9 | P1: amount and reason, extra cover listed and drawn, protection sentence | pass | `MyCoverTest.addedExtraCoverIsWhyMoreIsDueAndFromWhen`, `myCoverCopy.test.ts`; runtime as Maya: "$1,780.00 due · Your new extra cover, charged by the day from Jan 15.", "Extra cover: products you sell · from Jan 15", timeline bar, "… or because of something you sell." |
| AC10 | B1 last action "Added extra cover · {date}", counts as owing | pass | `BrokerPolicyReadTest.addedExtraCoverIsTheLastActionInTheList`; runtime B1: "1 owes money … Added extra cover · Nov 22" |
| AC11 | Broker only (403); unknown id 404 | pass | `aPolicyholderCannotPreviewOrAddCover`, `anUnknownPolicyIsNotFound`; runtime: Maya got 403 on preview and add, an unknown id got 404 |
| AC12 | No unexplained jargon; scan includes `lib/*Copy.ts` | pass | `jargon.test.ts` glob now includes `./lib/*Copy.ts` and `./lib/ledgerRows.ts`, with a case asserting they're scanned; "charged by the day" is explained by a Term |

## Checks Run

- build: `cd web && npm run build` (tsc + vite) — pass
- lint: no lint script in `web/package.json` or the Gradle build — not run
- typecheck: `cd web && npm run typecheck` — pass
- tests: `cd api && ./gradlew test` — pass (105 passed, 0 failed); `cd web && npx vitest run` — pass (85 passed)
- coverage (T13): add-coverage API classes 100% of lines (`MyCoverController` 7/10 branches); `addCoverCopy.ts` and `myCoverCopy.ts` 100% of lines

## Issues Found

| # | Severity | Issue | Route |
|---|---|---|---|
| 1 | low | At 390 px the native Cover select clips its label ("…$2.00 a d") because a `<select>` can't wrap. The full text is still readable when it's opened, and the hint below explains the cover. | /hotfix (copy or layout tweak) |
| 2 | low | `/favicon.ico` is 404 on every page (console error). It was missing before this spec. | outside this spec |

## Notes

- AC8's preview-failed branch and AC4's cancelled-policy message were checked by inspection and service test, not at runtime: there's no cancel flow yet, and forcing a non-422 preview failure would mean stopping the API mid-session.
- The pages have no component tests (the repo has none). They were verified in the browser, as above. The coverage tool reports 100% for them only because the jargon test loads them as raw text.
- Hygiene: all tasks are ticked after this run. No PR yet. The spec's `> Status:` still reads `shaped`. The branch changes about 20 non-playbook files, over git-workflow's 10-files-per-PR limit, so it will need splitting into several PRs (for example API, then web, then docs).
