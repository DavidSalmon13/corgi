# QA Report — Policy history for the broker

> Spec: spec.md
> Date: 2026-11-23 · QA by: david-schwartzman · Mode: setup
> Verdict: PASS WITH NOTES

## Acceptance Criteria

| AC | Criterion | Result | Evidence |
|---|---|---|---|
| AC1 | Reach History from B3 and back; policyholder 403; unknown 404 + "not found" page | pass | `BrokerHistoryEndpointTest.aPolicyholderIsForbidden`, `anUnknownPolicyIsNotFound`, `signedOutIsUnauthorized`. Live: "History" link on B3 → `/broker/policies/9/history`; back link "‹ QA Hearth & Hops LLC · GL-2026-0009"; policyholder → 403, signed out → 401, `/99999/history` → 404 `{"error":"not_found"}` and the page reads "We couldn't find that policy." |
| AC2 | One card per action, oldest first, date + actor + title with ID + toned rail dot | pass | `HistoryServiceTest.everyChangeAndPaymentIsOneCardOldestFirstWithWhoDidIt`, `aCancellationIsOneCard…`, `aDeclinedPaymentIsAZeroCard…`, `aSettledRefundIsItsOwnCard…`; `historyCopy.test.ts` "actionTitle", "actionTone". Live: 3 then 4 cards "Policy created · PTX-0016", "Extra cover added · PTX-0017", "Start date corrected · PTX-0018/0019", each "Nov 22, 2026 · Sam Reyes"; neutral dots for changes, amber for corrections |
| AC3 | Card money = sum of its ledger lines; net on corrections; pending marked and not counted; settled sum = ledger balance = statement last balance | pass | `settledCardsAddUpToTheLedgerBalanceAndTheStatementsLastBalance`, `aPaymentWithTheBankIsAPendingCardOutsideTheBalance`; `historyCopy.test.ts` "actionMoney". Live: settled cards 120000 + 64200 + 1400 = 185600 = `balanceCents` = statement balance; after re-correction 182800 on all three |
| AC4 | Detail line (sums or card/Stripe status/refs), reason as written, LE chips, struck when undone | pass | `theLineACorrectionUndidIsMarkedUndoneOnTheCardItCameFrom`, `aSettledRefundIsItsOwnCardWithItsStripeReferences`; `historyCopy.test.ts` "actionDetail", "ledgerChips". Live: "$730.00 × 321 ÷ 365.", reason quoted verbatim, LE-0026 struck after the first correction, LE-0028 struck after the second |
| AC5 | Failed refund says "Refund failed" + Stripe reason + retry link; decline reason; policyholder wording unchanged | pass | `aRefundStripeFailedSaysSoWithStripesReason`, `aRefundNotYetConfirmedIsOnItsWay`, `aDeclinedPaymentIsAZeroCardWithTheBanksReason`; `historyCopy.test.ts` (AC5 cases); `HistoryPage.tsx:128` retry link; `statementCopy.test.ts` policyholder cases unchanged and green. Tests only (see Notes) |
| AC6 | Oct 15 → Oct 8 is one correction card: undo +$642, charge +$656, net +$14, reason + name; same as statement | pass | `theOct15ToOct8CorrectionIsOneCardNetFourteenDollars`, `BrokerHistoryEndpointTest.theBrokerGetsEveryActionWithTheCorrectionAsOneCard`. Live (GL-2026-0009): one card "+$14.00 net … (−$642.00) … ($730.00 × 328 ÷ 365 = +$656.00)", reason and Sam Reyes; statement rows LE-0025…0028 = 120000, 64200, −64200, 65600, the same cents |
| AC7 | Every amount and chip opens the drawer for that line, broker voice, same maths/refs; keyboard, focus return | pass | `statementCopy.test.ts` "how we got this, for the broker" (no "you"/"your", same maths and refs). Live at 1280 light and 390 dark: all 8 (then 12) amounts and chips opened via keyboard Enter, focus moved into the dialog, Close and Esc each returned focus to the opener (40/40), no "you"/"your" in any drawer; refs "Change PTX-0017 · Line LE-0026" |
| AC8 | Correcting again adds a new card; earlier cards unchanged | pass | `correctingAgainAddsANewCardAndLeavesEarlierCardsAlone`. Live: re-corrected Oct 8 → Oct 22 → new PTX-0019 "−$28.00 net" (−$656.00, +$628.00 = $730.00 × 314 ÷ 365) with its reason. JSON diff of earlier cards: PTX-0016, PTX-0017 identical; PTX-0018 differs only by `undoneOn` on LE-0028 (the struck chip of AC4) |
| AC9 | Jargon test passes; matches B5 light + dark at 1280; 390 px date/actor above card, no h-scroll | pass | `web/src/jargon.test.ts` passes (in the 215 web tests). Live 390 dark: each date block ends 8 px above its card (336/344, 549/557, 771/779), `scrollWidth − innerWidth = 0`; 1280 light: date column right-aligned left of the rail, card at x=332. Layout matches `B5-History.dc.html`: back link, title, subtitle, date/actor column, card with title + money, detail, Reason block, Ledger lines chips |

## Checks Run

- build: `cd web && npm run build` — pass
- lint: no lint script in `web/` or `api/` — n/a
- typecheck: `cd web && npm run typecheck` — pass
- tests: `cd api && ./gradlew test` — pass (347 passed, 0 failed; `HistoryServiceTest` 12, `BrokerHistoryEndpointTest` 4); `cd web && npm test` — pass (216 passed, 19 files, after the fix below)
- runtime: current API on :8081 against the local DB, a second Vite dev server on :5174 proxying to it, headless Chromium (Playwright). Fictional policy GL-2026-0009 for "QA Hearth & Hops LLC" (owner Jordan Ash) was issued, given extra cover from Oct 15, corrected to Oct 8, then corrected again to Oct 22.

## Issues Found

| # | Severity | Issue | Route |
|---|---|---|---|
| 1 | med | Found by the code review after this run: a card with more than one line (a policy issued with extra cover, a cancellation of both covers) described only its first line, and neither the total nor any line amount opened the drawer (AC4, AC7) | Fixed on this branch: `historyCopy.ts` `eachLine` gives every line its sums and signed amount, so each one becomes a button. Test: `historyCopy.test.ts` "describes every line on a card with more than one". Live re-check (GL-2026-0010, issued with extra cover) at 1280 light and 390 dark: "+$1,200.00" and "+$730.00" each open their own drawer with focus returned, no h-scroll |

## Notes

- **AC5 and the payment/refund cards were verified by tests, not live.** Stripe isn't set up for the local run, so no live payment, decline or refund was possible. `HistoryServiceTest` covers these paths with signed webhook deliveries.
- **B5 comparison.** `B5-History.dc.html` is a template that renders unstyled outside its design runtime, so the comparison covered structure and copy, not a pixel overlay of colours and spacing.
- **Earlier cards and `undoneOn`.** After a re-correction, the earlier correction card's line gains `undoneOn` and its chip is struck. That is AC4's struck chip; the card's amounts, text and IDs don't change.
- **Stale API.** The API on :8080 was started before this branch's commits and doesn't serve `/api/broker/policies/{id}/history` until it's restarted.
- **Hygiene.** T1–T10 are done; T11 is ticked with this report. No PR yet, so the next step is the pr skill. spec.md `> Status: shaped` should move on when the PR opens. `playbook/config.yml` says `default_branch: main`, but this repo's PR base is `dev`.
