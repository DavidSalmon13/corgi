# QA Report — Several policies per customer, and rewrite

> Spec: spec.md
> Date: 2026-11-23 · QA by: david-schwartzman · Mode: setup
> Verdict: PASS WITH NOTES

## Acceptance Criteria

| AC | Criterion | Result | Evidence |
|---|---|---|---|
| AC1 | B3 "New policy for this customer" opens B2 in customer mode; customer read-only, same price preview | pass | `IssueForCustomerTest` (`thePreviewForACustomerIsTheSamePriceAsIssuing`, `theBrokerReadsTheCustomerB2ShowsReadOnly`, `theEndpointIssuesForTheCustomerAndSaysWhereToFindIt`); route `web/src/App.tsx:45`; action `web/src/pages/broker/PolicyWorkspace.tsx:263` |
| AC2 | Unreplaced cancelled policy's main action is "Rewrite this policy"; `?replaces=`, cover pre-set, "Replaces … (cancelled …)" note; saves `replaces_policy_id` | pass | `IssueForCustomerTest.aRewriteOfAFlatCancelledPolicyRecordsWhatItReplaces`; `policiesCopy.test.ts` (`rewriteNote`, `canRewrite`); `PolicyWorkspace.tsx:193` (ACTION_MAIN), `NewPolicyPage.tsx:301-303` |
| AC3 | Same customer, cancelled, not already replaced; checked under row lock + unique index; 422 under `replaces`; unknown customer 404 | pass | `IssueForCustomerTest` (`onlyACancelledPolicyCanBeRewritten`, `aPolicyOfAnotherCustomerCannotBeReplaced`, `aPolicyCanBeReplacedOnlyOnce`, `twoRewritesAtOnceOnlyOneWins`, `theEndpointPutsAReplaceProblemUnderReplaces`, `theEndpointAnswers404ForAnUnknownCustomer`); `PolicyRepository.java:146` (`for update`); `V13__policy_rewrites.sql:9` unique constraint |
| AC4 | B3 "Replaces / Replaced by" links; B1 "Cancelled · replaced by GL-…" | pass | `IssueForCustomerTest.theBrokerSeesWhichPolicyReplacedWhich`; `PolicyRewriteRepositoryTest.aRewriteRecordsWhatItReplacesAndBothPoliciesShowTheLink`; `policyCopy.test.ts:13`; `PolicyWorkspace.tsx:41-58` |
| AC5 | No customer/account rows for an existing customer; issue email "Sign in to see it" vs "Set up your account" | pass | `IssueForCustomerTest.issuingForAnExistingCustomerWritesNoCustomerOrAccountRows`; `NotifyServiceTest:127,136`; `EmailTemplatesTest:49`; `EmailTemplates.java:69-75` |
| AC6 | `GET /api/me/policies` newest first with balance, money in flight, links; `totalDueCents` = positive balances only | pass | `MyPoliciesTest` (`aRewriteListsBothPoliciesNewestFirstAndOnlyWhatIsOwedIsDue`, `aPaymentWaitingWithTheBankShowsOnItsPolicy`); `MyPoliciesServiceTest.listsEveryPolicyNewestFirstAndAddsUpWhatIsDue` |
| AC7 | `/me`: one policy → its home; two+ → "Your policies" with total due and per-policy cards | pass | `mePaths.test.ts` (`meLanding`); `policiesCopy.test.ts`; `web/src/pages/me/MeRoutes.tsx:18-26`; `web/src/pages/me/PoliciesPage.tsx:72` |
| AC8 | Pages under `/me/policies/:id/...`; "All policies" link from two up; old routes redirect to newest policy | pass | `App.tsx:62-70`; `MeRoutes.tsx:28-33`; `mePaths.test.ts` (`legacyRedirect`); `policiesCopy.test.ts:97`; `MeNav.tsx:36` |
| AC9 | Per-policy reads and `POST /api/me/payments {policyId}` 404 for a policy not owned; payment pays only that policy | pass | `MyPoliciesTest` (`anotherOwnersPolicyIsNotFoundEverywhere`, `aPaymentPaysOnlyThePolicyItNames`, `eachPolicyOpensItsOwnCoverStatementAndTimeline`, `theOneCoverRouteIsGone`); `MyCoverTest:85`; `StatementEndpointTest:71,81`; `TimelineEndpointTest:70,80`; `MyPaymentsTest` |
| AC10 | Issue, receipt and refund emails link to the policy's own pages | pass | `NotifyServiceTest:127,136,152`; `EmailTemplatesTest:49` |
| AC11 | Rewrite, Flat cancellation, Policy period use the standard term with "?"; `jargon.test.ts` passes | pass | `web/src/lib/glossary.ts` (`rewrite`, `flatCancellation`, `policyPeriod`); `jargon.test.ts` green; `myCoverCopy.test.ts` (flat-cancellation sentence marked for its "?"); P1 renders it through `<Glossed>` (`HomePage.tsx:284`) |

## Checks Run

- build (web): `npm run build` (tsc --noEmit && vite build): pass
- typecheck (web): `npm run typecheck`: pass
- lint: no lint script in `web/package.json` and no lint task in `api/`, so nothing was run
- tests (api): `./gradlew test`: pass (494 passed, 0 failed, 0 skipped)
- tests (web): `npx vitest run`: pass (348 passed in 23 files)

## Issues Found

(none)

## Notes

- The code review (2026-11-23) found that AC11 had been passed too early: P1 showed "flat cancellation" without its "?", and the Pay page didn't name the policy being paid when the owner has several. Both are fixed and tested, and the web suite now has 348 tests.

- Screens (AC1, AC2, AC4, AC7, AC8) were verified by copy/path unit tests and code inspection, not by driving the app in a browser. A click-through of the README demo (flat-cancel GL-2026-0013, rewrite it, pay the new one from "Your policies") is worth doing before the PR.
- The repo has no lint command configured. That is a gap in the repo, not in this spec.
- The spec's `> Status:` still reads `shaped`, and there is no PR yet. Both get updated when the pr skill opens the PR.
