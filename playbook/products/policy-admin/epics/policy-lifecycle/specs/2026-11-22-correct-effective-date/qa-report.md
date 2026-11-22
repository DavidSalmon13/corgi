# QA Report — Correct a start date

> Spec: spec.md
> Date: 2026-11-22 · QA by: david-schwartzman · Mode: setup
> Verdict: PASS WITH NOTES

## Acceptance Criteria

| AC | Criterion | Result | Evidence |
|---|---|---|---|
| AC1 | B3 shows **Correct** on an active policy with extra cover; B4 opens on the Correct tab; tabs Add cover · Correct with per-tab blocked states | pass | `PolicyWorkspace.tsx:195` (link to `/change?tab=correct`), `ChangePolicyPage.tsx:72` `blockedReason` per tab; observed in the running app: B3 → Correct → B4 Correct tab |
| AC2 | Form names the cover, shows the current start, a corrected date field and a required reason with the word-for-word note | pass | `correctCopy.test.ts` "names the cover being corrected…"; `CorrectCoverForm.tsx:156`, `:221`; observed at 1280 px light and 390 px dark |
| AC3 | Preview runs the save's code path, writes nothing; Oct 15 → Oct 8 lines, net +$14.00, customer sentence | pass | `CorrectCoverageServiceTest.movingTheStartAWeekEarlier…`, `.previewSavesNothing`; `CorrectionEndpointsTest.previewShowsTheUndo…`; `correctCopy.test.ts` preview-line cases |
| AC4 | Oct 15 → Oct 22: rebill +$628.00, net −$14.00, settle line, "Save and refund $14.00" | pass | `CorrectionEndpointsTest.previewOfALaterStartListsEachRefundWithItsCard`; `correctCopy.test.ts` settle line and save label cases |
| AC5 | Credit pays the open balance first; refund split latest payment first, capped per payment; preview lists each piece | pass | `CorrectCoverageServiceTest.aCreditFirstPaysOff…`, `.aCreditLargerThan…`, `.aRefundLargerThanTheLatestPaymentSpills…`, `.moneyAlreadyBeingRefunded…`; refund rows in `CorrectCoverForm.tsx` |
| AC6 | 422 with messages under startDate / reason / policy | pass | `CorrectCoverageServiceTest` validation cases (missing, before start, on/after end, same start, blank or >500 reason, cancelled, no extra cover, payment with the bank); `CorrectionEndpointsTest` 422 cases |
| AC7 | Confirm dialog with summary; one transaction writes CORRECT PTX with reason, REVERSAL, corrected CHARGE, replacement cover; 201; once-only banner with ids | pass | `CorrectCoverageServiceTest.savingUndoesTheOldChargeAddsTheCorrectedOneAndReplacesTheCover`, `.anInvalidCorrectionSavesNothing`; `CorrectionEndpointsTest.savingAnswers201…`; `correctCopy.test.ts` confirm/banner; observed: dialog → save → B3 banner "Saved. Priya now owes $1,916.00 and can see why. Correction PTX-0007 · undo LE-0012 · corrected line LE-0013" (see Notes on wording) |
| AC8 | Ledger rows never updated; second correction reverses the corrected line; concurrent corrections → one | pass | `CorrectCoverageServiceTest.aSecondCorrectionUndoesTheCorrectedLineNotTheOriginal`, `.twoSimultaneousCorrectionsSaveOnce`; `LedgerTest` correction cases; append-only trigger (V2) |
| AC9 | Open PaymentIntents for the old amount are cancelled on save | pass | `CorrectCoverageServiceTest.savingCancelsAnUnpaidPaymentForTheOldAmount`; `CorrectCoverageService.java:89` |
| AC10 | Refund recorded PENDING in the same transaction, sent after commit with a per-attempt key; REFUND line only on a signed succeeded webhook, traced to refund + event; replay posts nothing; failures marked FAILED with reason | pass | `RefundWebhookTest` (sent with key `refund-<id>-1`, one line traced, replay, second event, pending event, unknown refund, failed event, refused send); `RefundServiceTest` (settle twice, stale attempt); `CorrectCoverageService.java:112` |
| AC11 | B3 lists a failed refund with the stated text and **Retry refund**; retry sends a new attempt; broker only (403) | pass | `RefundWebhookTest.retryingAFailedRefundSendsAttemptTwoWithANewKey`, `.aPolicyholderCannotRetryARefund`, `.onlyAFailedRefundCanBeRetried`, `.retryingAnUnknownRefundIsNotFound`; `correctCopy.test.ts` failed-refund text; `PolicyWorkspace.tsx:81` |
| AC12 | B3 ledger: original struck "Undone by LE-…", "Undo of LE-…", corrected line highlighted together; refund refs; timeline "from Oct 8 · was Oct 15" | pass | `ledgerRows.test.ts`; `BrokerPolicyReadTest.afterACorrectionThePolicyShowsOneExtraCover…`, `.thePolicyListsItsRefunds…`; `PolicyWorkspace.tsx:231`; observed on B3 after a real save |
| AC13 | P1: "$14.00 due · We fixed when your extra cover started." + Pay; "Refund on its way" with card; one extra cover at the corrected date | pass | `MyCoverTest.aCorrectionThatOwesMore…`, `.aRefundOnItsWay…`, `.aFailedRefundIsStillShownAsOwed…`; `myCoverCopy.test.ts`; `HomePage.tsx:89`, `:150` |
| AC14 | Plain language; jargon.test passes | pass | `web/src/jargon.test.ts` scans every `.tsx`, `lib/*Copy.ts` and `ledgerRows.ts`, including `CorrectCoverForm.tsx` and `correctCopy.ts`; passes. The design's "reversal"/"rebill" became "undone in full"/"charged by the day", each with an explanation |

## Checks Run

- build: `cd web && npm run build` (`tsc --noEmit && vite build`) — pass
- lint: no lint script in `web/package.json` or the Gradle build — not run
- typecheck: included in `npm run build` — pass
- tests: `cd api && ./gradlew test` — pass (236 passed, 0 failed); `cd web && npm test` — pass (133 passed, 0 failed). Run in T15 at 89b9305; nothing but tasks.md has changed since.
- coverage (T15, changed files): CorrectCoverageService 99% lines, RefundService 90%, StripeWebhookService 91%, RefundRepository 95%, CorrectionController/RefundController 100%, correctCopy.ts 94% lines / 74% branches; StripeJavaGateway 23% (real Stripe calls, faked in tests).

## Issues Found

(none)

## Notes

- **AC7 banner wording:** the spec text reads "Maya now owes $14.00 and we've told her why"; the app says "…and can see why", because the copy doesn't guess a customer's pronoun (DECISIONS.md, 2026-11-22 "Correction copy names the customer instead of guessing a pronoun"). Same meaning; worth updating the spec text through /spec-changes.
- **Not seen in the running app:** the B3 failed-refund alert and the P1 "Refund on its way" card. Local Stripe test keys aren't set up, so no paid payment exists to refund. Both are covered by API tests and copy tests, and by reading the code.
- **Behaviour beyond the spec text:** P1 keeps showing "We owe you $14.00" while a refund is FAILED and waiting for a retry, as the B3 design says (DECISIONS.md, "P1 shows every unsettled refund").
- **Local data:** one correction was saved on the local dev database (fictional Fernwood Florist policy, PTX-0007) to check B3. The ledger is append-only, so it stays until `docker compose down -v`.
- **Hygiene:** T16 is this run. No PR yet (the next step is the pr skill). spec.md `> Status:` still reads `shaped` and should move on when the PR opens. No lint tooling is configured.
