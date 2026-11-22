# QA Report — Cancel a policy and refund unused days

> Spec: spec.md
> Date: 2026-11-22 · QA by: david-schwartzman · Mode: setup
> Verdict: PASS WITH NOTES

## Acceptance Criteria

| AC | Criterion | Result | Evidence |
|---|---|---|---|
| AC1 | B3 Cancel action opens B4's Cancel tab; tabs Add cover · Correct · Cancel; cancelled policy blocks all three | pass | `web/src/pages/broker/PolicyWorkspace.tsx:148` (`canCancel` only when ACTIVE), `:201`; `ChangePolicyPage.tsx:42-44` (tabs), `:77-78` ("This policy is cancelled.") |
| AC2 | "Cover ends on" with hint, required reason shown word for word | pass | `CancelPolicyForm.tsx:145`, `:161`, `:183`; reason required: `CancelPolicyServiceTest.savingNeedsAReasonTheCustomerCanRead` |
| AC3 | Preview uses the save path and writes nothing; worked example lines, refund pieces, customer line, save label, Keep the policy | pass | `CancelPolicyServiceTest.cancellingTheWorkedExampleFromDec1CreditsBothCoversKeepsBack14AndRefundsTheRest`, `previewSavesNothing`; `CancellationEndpointsTest.previewShowsEachCoversCreditWhatIsKeptBackAndTheRefund`; `cancelCopy.test.ts` "cancel preview rows", "lists each piece of the refund", "labels the save button"; `CancelPolicyForm.tsx:215`, `:239`, `:242`. Runtime: live preview of GL-2026-0003 from Dec 1 → $1,200.00 × 335 ÷ 365 = −$1,101.37, refund $1,101.37 to card 4242, nothing written |
| AC4 | One credit per live cover over max(start, cancel day) → end; future cover credited in full; start-day cancel refunds in full | pass | `CancelPolicyServiceTest.aCoverStartingAfterTheCancelDayIsCreditedInFull`, `cancellingOnTheStartDayCreditsEverythingAndRefundsThePaymentInFull`; `CancellationSchemaTest.cancellingOnTheStartDayIsAllowed` |
| AC5 | Credit smaller than balance → no refund, "Still owed for the 91 days covered", label "Cancel policy" | pass | `CancelPolicyServiceTest.anUnpaidPolicyRefundsNothingAndStillOwesForTheDaysCovered`; `CancellationEndpointsTest.previewOfAnUnpaidPolicyShowsWhatIsStillOwed`; `cancelCopy.test.ts` "shows what is still owed…". Runtime: unpaid GL-2026-0006 → credit −$1,170.41, kept back $1,170.41, still owed $29.59, no refunds |
| AC6 | 422 field errors for cancelDate, reason, policy (already cancelled, payment with the bank) | pass | `CancelPolicyServiceTest` (6 validation tests incl. `aPaymentStillWithTheBankBlocksACancellation`); `CancellationEndpointsTest.aMissingOrUnreadableDateIsAFieldErrorNotACrash`, `aDayOutsideTheTermIsAFieldErrorOnTheDate`, `aReasonOver500CharactersIsAFieldError`, `cancellingTwiceIsAPolicyError`. Runtime: POST with a Jan 1 date and no reason → HTTP 422 with both field messages |
| AC7 | Confirm dialog; one transaction writes PTX, CREDIT lines, CANCELLED + cancelled_on, cancels open intents, PENDING refunds; refunds after commit with per-attempt keys; 201; B3 banner | pass | `CancelPolicyServiceTest.savingPostsOneCreditPerCoverCancelsThePolicyAndRecordsTheRefundsThePreviewShowed`, `savingCancelsAnUnpaidPaymentForTheOldAmount`, `refundsGoToStripeAfterCommitWithOneKeyPerAttempt`, `anInvalidCancellationSavesNothing`; `CancellationEndpointsTest.savingAnswers201WithTheIdsTheBannerShows`; `cancelCopy.test.ts` "asks once more before cancelling", "the saved banner"; `CancelPolicyForm.tsx:81` |
| AC8 | No ledger row updated; concurrent cancels save once; REFUND only on signed webhook, replay posts nothing; failed refund → Retry refund | pass | `CancelPolicyServiceTest.twoSimultaneousCancellationsSaveOnce`; `LedgerTest` append-only cases (T2); `RefundWebhookTest`, `RefundServiceTest` (shared refund machinery); `PolicyWorkspace.tsx:106` (Retry refund). Runtime: cancelled GL-2026-0005 has its CREDIT line and no REFUND line yet (no signed webhook locally) |
| AC9 | B3 cancelled: pill, period "(cancelled)", "Refund of … on its way", striped timeline, "unused days refunded" lines | pass | `BrokerPolicyReadTest.aCancelledPolicyShowsItsCancelDayAndACreditLinePerCoverAfterTheCharges`; `policyCopy.ts:12`, `:49-59`; `ledgerRows.ts:52-53` + `ledgerRows.test.ts`; `PolicyWorkspace.tsx:177`, `:219` |
| AC10 | P1 Cancelled card: refund on its way / sent / still owing with Pay; protection line and period end on cancel day; striped timeline; link to P6 | pass | `MyCoverTest` (3 cancelled cases incl. `anUnpaidCancelledPolicyStillOwesForTheDaysCoveredAndCanPayIt`); `cancelCopy.test.ts` "P1: …"; `myCoverCopy.test.ts` cancelled protection sentence and cover dates; `HomePage.tsx:79-104`, `:233`, `:238`, `:246` |
| AC11 | P6 at /me/cancellation: title, used/unused bar, credit lines, kept back, money back, refund pill + Stripe sentence; redirect when not cancelled | pass | `cancelCopy.test.ts` "P6: …" (4 tests); `MyCoverTest.onceEveryCancellationRefundSettlesItReadsAsRefundedWithTheDay`; `App.tsx:62`; `CancellationPage.tsx:75`, `:80`, `:149` (redirect), `:152`, `:158`, `:200` |
| AC12 | Plain language; jargon.test passes | pass | `web/src/jargon.test.ts` scans every `.tsx` and `lib/*Copy.ts`: green; screens checked by eye at 1280 px light and 390 px dark |

## Checks Run

- build: `npm run build` (web, includes `tsc` typecheck) — pass
- lint: no lint script in `web/package.json` or the Gradle build — not run
- typecheck: covered by `npm run build` (web) and `./gradlew test` compilation (api) — pass
- tests: `./gradlew test` — pass (298 passed, 0 failed); `npx vitest run` — pass (162 passed, 0 failed)
- coverage (T14): changed API classes 91.5–100% lines; changed web `lib/` files 97.6–100% lines

## Issues Found

(none)

## Notes

- **App check at 1280 px light and 390 px dark (Playwright, headless Chromium, against the running app):** B3 cancelled (GL-2026-0005), B4 blocked tab, B4 Cancel preview on a paid policy (GL-2026-0003: −$1,101.37, refund $1,101.37, "Cancel policy and refund $1,101.37", Keep the policy) and on an unpaid one (GL-2026-0006: "What Wren still owes", $29.59, "Cancel policy"), P1 cancelled and P6 refund on its way (Robin Vale). Every screen rendered in both themes with no horizontal scroll (scrollWidth = clientWidth) and no page errors. P6 for Maya's active policy redirected to `/me`. A 404 logged once on B3's first load didn't happen again on rerun. Nothing was saved: the previews write nothing, and policy 5 was already cancelled.
- Cosmetic, not from this spec: B3's month ticks "Nov" and "Dec" crowd together when a policy starts late in a month, and P1's compact timeline labels both ends "Nov 22" when the term runs Nov 22 → Nov 22 (`CoverTimeline` short dates).
- The screens (`HomePage`, `CancellationPage`, `CancelPolicyForm`, `PolicyWorkspace`, `CoverTimeline`) have no component tests, because the repo has no jsdom/Testing Library setup. All their words come from tested `lib/*Copy.ts` functions.
- The demo data doesn't contain the worked example's two-cover, $14-kept-back policy. That case is proven by `CancelPolicyServiceTest` and `CancellationEndpointsTest`. The live previews used the single-cover demo policies.
- Hygiene: all 15 tasks are done. No PR exists yet. `spec.md` still says `> Status: shaped`. `playbook/config.yml` names `default_branch: main`, while the repo works against `dev`.
