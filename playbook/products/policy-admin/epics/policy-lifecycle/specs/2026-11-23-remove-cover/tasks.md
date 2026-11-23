# Tasks — Remove extra cover mid-term

> Epic: policy-lifecycle
> Spec: 2026-11-23-remove-cover
> Branch: feat/policy-lifecycle/remove-cover
> Reviewed: 2026-11-23 APPROVE
> SecurityReviewed: 2026-11-23 APPROVE

## Failing tests first (TDD)

- [x] T1 Write failing API tests:
  - RemoveCoverServiceTest: preview equals save, credit to the cent, every validation, open intents cancelled, refund after commit, once per policy
  - RemoveCoverEndpointsTest: 422, 201, 403
  - ripple cases in CancelPolicyServiceTest (no double credit), TimelineServiceTest, MyCoverTest, HistoryServiceTest and NotifyServiceTest

  (testing skill)
- [x] T2 Write failing web tests: removeCoverCopy.test.ts, plus ripple cases in statementCopy, historyCopy, policyCopy, myCoverCopy, cancelCopy and ledgerRows tests (testing skill)

## Schema

- [x] T3 Add V12__cover_removal.sql: coverages.ended_on with its check; drop and re-add the policy_transactions type check with REMOVE_COVERAGE, confirming the constraint name first (repository/migration)

## Remove API

- [x] T4 CoverageRepository.markEnded(id, day) guarded by `ended_on is null`; endedOn on Live and PolicyViews.Coverage (repository)
- [x] T5 RemoveCoverPlan (pure): one-cover credit via CancelPolicyPlan.credits, plus kept-back, balance-after and still-owed (service)
- [x] T6 RemoveCoverService preview/remove:
  - lock, then read the cover after the lock
  - validate (AC1, AC2)
  - cancelOpenIntents
  - insert REMOVE_COVERAGE, post the CREDIT, markEnded
  - recordAndSendAfterCommit

  (service)
- [x] T7 RemoveCoverController POST /api/broker/policies/{id}/cover-removal and /preview; 422 field maps, 201, 403 for a policyholder (controller)
- [x] T8 Blocks after removal: removed-cover messages in AddCoverageService and CorrectCoverageService (AC5) (service)

## Ripple

- [x] T9 CancelPolicyPlan.credits ends each cover at coalesce(endedOn, to); TimelineService keptTo = min(cancelledOn, endedOn) (service)
- [x] T10 MyCoverService.DueReason.COVER_REMOVED with the broker note; EmailTemplates.RefundReason.COVER_REMOVED mapped in NotifyService (service)

## Web

- [x] T11 types.ts unions (REMOVE_COVERAGE, KeptBackFor); maps in cancelCopy, statementCopy.changeWhat, historyCopy, policyCopy.ACTIONS, myCoverCopy and the ledgerRows removal title; new lib/removeCoverCopy.ts using the glossary terms (lib)
- [x] T12 RemoveCoverForm.tsx: date, reason, debounced preview, confirm dialog, 422 mapped onto fields, saved banner (page)
- [x] T13 ChangePolicyPage "Remove" tab with blockedReason; PolicyWorkspace "Remove extra cover" action and ended-cover state; CoverTimeline ended bar (page)
- [x] T13.1 P1 after a removal (from QA, AC7): the refund card gives the removal as the reason, not a corrected effective date; the broker's note shows on a COVER_REMOVED balance; the protection line says product liability ends/ended and stops counting it once ended (page)

## Docs

- [x] T14 DECISIONS.md: a removal is a CREDIT, not reverse-and-rebill; extra cover can be removed once per policy; ended_on rather than a replaced row
- [x] T15 README demo steps: issue with extra cover, pay, remove from Dec 1, see the $548.00 return premium settle and the customer's view

## Verification

- [x] T16 Run both suites with coverage on changed files (testing skill)
- [x] T17 Run QA against acceptance criteria (qa skill)
