# QA Report — Your statement and "How we got this"

> Spec: spec.md
> Date: 2026-11-22 · QA by: david-schwartzman · Mode: setup
> Verdict: PASS WITH NOTES

## Acceptance Criteria

| AC | Criterion | Result | Evidence |
|---|---|---|---|
| AC1 | Reach `/me/statement` from P1 Home and back; broker 403; own policy only | pass | `StatementEndpointTest.aBrokerIsForbidden`, `eachOwnerOnlySeesTheirOwnPolicy`, `signedOutIsUnauthorized`. Live: broker `GET /api/me/statement` → 403; Home link → `/me/statement`; "Your cover" → `/me` |
| AC2 | Every line oldest first with running balance; last balance = ledger balance | pass | `StatementServiceTest.everyLedgerLineIsListedOldestFirstWithItsRunningBalance`, `theLastBalanceEqualsTheLedgerBalanceToTheCent…`. Live (GL-2026-0008): rows 120000 → 184200 → 120000 → 185600, header `balanceCents` 185600 |
| AC3 | Banner states owed + why / waiting for bank / we owe you / all paid up | pass | `StatementServiceTest` banner tests (4 states), `statementCopy.test.ts` "statement banner". Live: "You owe $1,856.00. We fixed when your extra cover started." |
| AC4 | Pending payment and unsettled refund outside the balance; declined $0.00 line | pass | `aPaymentWithTheBankIsAPendingRowOutsideTheBalance`, `anUnsettledRefundIsAPendingRowOutsideTheBalance`, `onceTheRefundSettlesItIsALedgerRow…`, `aDeclinedAttemptIsAZeroLineThatMovesNoMoney`; `statementCopy.test.ts` "marks money on its way and a declined try". Tests only (see Notes) |
| AC5 | One correction card: old → new, day delta, net, per-day when exact, reason + name; expands to original/undo/corrected with maths | pass | `theTrialCorrectionIsOneCard…`, `thePerDayPriceIsLeftOut…`; live card: "Oct 15 → Oct 8 · 7 more days × $2.00 · +$14.00", struck original "$730.00 × 321 ÷ 365", undo, corrected "(corrected) $730.00 × 328 ÷ 365", quote + "Sam Reyes, your broker"; Show/Hide toggles details |
| AC6 | Oct 15 → Oct 8: +642, −642, +656, net +14; mirror −14 with refund row | pass | Live: +$642.00, −$642.00, +$656.00, "−$642.00 + $656.00 = +$14.00". Mirror: `theMirrorCaseNetsMinusFourteenDollarsAndShowsTheRefund` (−$14.00, pending refund $14.00) |
| AC7 | Every amount opens P4 for its kind: sentence, maths, cause, payment, references | pass | `StatementServiceTest` trace tests (charge, reversal, payment, refund, credit, per-kind refs); `statementCopy.test.ts` "how we got this". Live: all 5 then 8 amounts open the drawer; charge "$730.00 a year × 328 days ÷ 365 = $656.00 … end date isn't counted"; undo "Undoes $730.00 × 321 ÷ 365 … sign flipped", refs PTX/LE/Undoes LE |
| AC8 | Keyboard + screen reader; Esc/close return focus; phone bottom sheet, no h-scroll | pass | Live (1280): focus moves into the native modal `<dialog>` (Close button), stays inside after 12 Tabs, Esc and Close both return focus to the amount (5/5). 390 px: sheet 390 wide, bottom-anchored, no horizontal scroll on sheet or page |
| AC9 | Re-correction: new reversal and corrected line, drawer explains new amount, nothing earlier changes | pass | `aSecondCorrectionIsItsOwnCardAndTheFirstCardStaysAsItWas`. Live: re-correct Oct 8 → Oct 22 added LE-0023 −65600 and LE-0024 +62800; earlier rows identical apart from `undoneOn`; first card byte-identical; drawer for +$628.00 shows "× 314 days" and "Oct 8 → Oct 22" with the debrief reason |
| AC10 | Jargon test passes; screens match P3/P4 light + dark at 390 and 1280 | pass | `web/src/jargon.test.ts` globs every `.tsx` and `lib/*Copy.ts` (incl. StatementPage, HowWeGotThis, statementCopy) — passes. Screenshots at 1280 light and 390 dark compared to `Main.dc.html` / `P4-HowWeGotThis.dc.html`: receipt edge, card, struck original in its date slot and in the card, net band, quote, P4 sections in order |

## Checks Run

- build: `cd web && npm run build` — pass
- lint: no lint script in `web/` or `api/` — n/a
- typecheck: `cd web && npm run typecheck` — pass
- tests: `cd api && ./gradlew test` — pass (329 passed, 0 failed); `cd web && npm test` — pass (189 passed, 18 files)
- runtime: current API on :8081 against the local DB, the web dev server on :5173, headless Chromium (Playwright). Fictional policies GL-2026-0007/0008 for "QA Crumb & Co LLC" were created for the walk-through.

## Issues Found

(none)

## Notes

- **AC4 and the AC6 mirror case were verified by tests, not live.** Stripe isn't set up for the local run, so no live payment, decline or refund was possible. The service and copy tests cover these paths with signed webhook deliveries.
- **Enter right after closing (low, not a defect).** If Enter is pressed on an amount within milliseconds of closing the drawer, before the native `close` event fires, the new drawer doesn't open: `onClose` clears the row after `onOpen` set it. A person can't type that fast (48/48 opens once the dialog had unmounted), but an automated test that does this will flake.
- **Expected duplicate amount.** The undone original shows both in its own date slot (struck) and inside the correction card, as P3 does.
- **Hygiene.** T1–T9 are done; T10 is ticked with this report. No PR yet, so the next step is the pr skill. spec.md `> Status: shaped` should move on when the PR opens. `playbook/config.yml` says `default_branch: main`, but this repo's PR base is `dev`.
- The API running on :8080 during QA was started before the statement commits, so it doesn't serve `/api/me/statement` until it's restarted.
