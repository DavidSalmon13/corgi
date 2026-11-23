# QA Report — Remove extra cover mid-term

> Spec: spec.md
> Date: 2026-11-23 · QA by: david-schwartzman · Mode: setup
> Verdict: PASS WITH NOTES

## Acceptance Criteria

| AC | Criterion | Result | Evidence |
|---|---|---|---|
| AC1 | B3 action opens B4's Remove tab; blocked tab names its reason | pass | `PolicyWorkspace.tsx` "Remove product liability" → `?tab=remove`; `ChangePolicyPage.tsx` `blockedReason` → `removeBlockedReason`; removeCoverCopy.test.ts "the Remove tab is blocked with a reason" (5 cases); RemoveCoverServiceTest `aPolicyWithoutExtraCoverHasNothingToRemove`, `aCancelledPolicyCannotHaveCoverRemoved`, `aPaymentStillWithTheBankBlocksARemoval`, `extraCoverCanBeRemovedOncePerPolicy` |
| AC2 | Date + required reason (≤500), range checks, field messages | pass | RemoveCoverServiceTest `aMissingEndDateIsRejected`, `anEndDateBeforeTheExtraCoverStartsIsRejected`, `anEndDateOnOrAfterThePolicyEndIsRejected`, `savingNeedsAReasonTheCustomerCanRead`, `savingReportsEveryFieldToFixAtOnce`; RemoveCoverEndpointsTest 422 cases; removeCoverCopy.test.ts "the Remove form (AC2)" |
| AC3 | Preview = save path, writes nothing; $730.00 × 274 ÷ 365 = −$548.00; refund line, save label, Keep button | pass | RemoveCoverServiceTest `previewSavesNothing`, `previewEqualsWhatSavingPosts`, worked-example test; RemoveCoverPlanTest; removeCoverCopy.test.ts "the preview (AC3)" (9 cases) |
| AC4 | One transaction under lock: REMOVE_COVERAGE, one traced CREDIT, `ended_on`, intents cancelled, refunds PENDING then sent after commit; start-day removal credits the whole charge | pass | RemoveCoverServiceTest `savingPostsOneTracedCreditEndsTheCoverAndRecordsTheRefundThePreviewShowed`, `theRefundGoesToStripeAfterCommitWithOneKeyPerAttempt`, `savingCancelsAnUnpaidPaymentForTheOldAmount`, `twoSimultaneousRemovalsSaveOnce`, `removingOnTheCoversStartDayCreditsTheWholeChargeToTheCent`; CoverRemovalRepositoryTest |
| AC5 | After removal Add/Correct/Remove blocked with plain message; API 422 under `policy` | pass | RemoveCoverServiceTest `afterARemovalExtraCoverCannotBeAddedAgain`, `afterARemovalTheExtraCoversStartCannotBeCorrected`; RemoveCoverEndpointsTest `removingTwiceIsAPolicyError`; `ChangePolicyPage.tsx` `blockedReason` uses `removedCoverReason` |
| AC6 | Later cancellation credits removed cover only up to `ended_on` | pass | CancelPolicyServiceTest `cancellingOnOrAfterTheRemovalDayCreditsTheRemovedExtraCoverNothing`, `cancellingBeforeTheRemovalDayCreditsTheExtraCoverOnlyUpToIt`; TimelineServiceTest `cancellingAfterARemovalKeepsTheExtraCoverEndedOnItsRemovalDay` |
| AC7 | P1 explains the change and shows the broker's note; P2 bar ends Dec 1; P3/B5 credit titled as removal; list "Removed…"; refund email reason | pass | P1: removeCoverCopy.test.ts "P1 after a removal (AC7)", `HomePage.tsx` refund card uses `refundAfterRemoval`, `BrokerNote` on COVER_REMOVED, `coverRemovedSentence` under the protection line (fixed in T13.1 after the first run failed); MyCoverTest removal cases. P2/P3/B5/list/email: TimelineServiceTest, HistoryServiceTest, NotifyServiceTest, ledgerRows/statementCopy/historyCopy/policyCopy/timelineCopy tests, `CoverTimeline` `endedOn` |
| AC8 | Standard terms with "?" explanation; `jargon.test.ts` passes | pass | `jargon.test.ts` green in the 310-test web run; copy uses return premium, pro-rata, effective date, endorsement through `Glossed`/`Term` |

## Checks Run

- build: `cd web && npm run build` — pass
- lint: none configured (`npm run lint` doesn't exist)
- typecheck: `cd web && npx tsc -b` — pass
- tests: `cd api && ./gradlew test` — pass (453 passed, 0 failed); `cd web && npx vitest run` — pass (312 passed, 0 failed)
- coverage: JaCoCo (throwaway init script) RemoveCoverPlan/Controller, CoverageRepository, TimelineService 100%, RemoveCoverService 97.3%; web copy libs for this spec 100% lines

## Issues Found

| # | Severity | Issue | Route |
|---|---|---|---|
| 1 | high · fixed in T13.1 (46a1907) | P1's "Refund on its way" card always says "We corrected the effective date of your product liability endorsement…" (`web/src/pages/me/HomePage.tsx:150-154`). After a removal, the customer is told the wrong reason for their $548.00. | new task T13.1 (P1 for a removal) |
| 2 | med · fixed in T13.1 | P1's amount-due card shows the broker's note only when `dueReason === 'CORRECTION'` (`HomePage.tsx:209`), so a COVER_REMOVED balance hides the broker's reason, which AC7 requires. | T13.1 |
| 3 | med · fixed in T13.1 | `coverRemovedSentence` ("Your product liability ends on Dec 1, 2026.") is built and tested but no screen uses it. P1's protection sentence still counts the removed cover (`HomePage.tsx:244` `hasExtra` ignores `endedOn`), so it keeps saying "or because of something you sell" after product liability ended. | T13.1 |

## Notes

- The first run on 2026-11-23 failed AC7 on P1. No task in tasks.md covered the P1 screen. T10 added the API's `COVER_REMOVED` reason and T11 the copy, but T13 wired only B3, B4 and the timeline. This was a gap in the plan, not a regression. T13.1 was added and done, and this rerun passes AC7.
- Copy follows the glossary rather than the spec's literal examples ("Remove product liability", "No short-rate penalty", tab "Add endorsement"), as recorded in DECISIONS.md on 2026-11-23. That isn't counted as a failure.
- Hygiene: all tasks are done (T17 is ticked with this report); no PR yet; spec `> Status: shaped` should move on when the PR opens.
- Why this is PASS WITH NOTES rather than PASS: no runtime check was run against the dev server. API behaviour is covered by Testcontainers integration tests, and the screens are covered only through their copy helpers (no render tests).
