# Tasks — Cancel a policy and refund unused days

> Epic: policy-lifecycle
> Spec: 2026-11-22-cancel-refund
> Branch: feat/policy-lifecycle/cancel-refund

## Schema & ledger

- [x] T1 Add V9__cancellations.sql: policies.cancelled_on + status/date checks; CREDIT entry type + ledger_credit_traced check (repository/migration)
- [x] T2 Ledger CREDIT: EntryType.CREDIT, NewEntry.credit, LedgerPostingService.check branch; LedgerTest cases incl. append-only (ledger service)
- [x] T3 Move refund allocation to billing: RefundService.plan + recordAndSendAfterCommit, PlannedRefund to billing; CorrectCoverageService uses them; correction + refund tests stay green (service)

## Cancel API

- [x] T4 PolicyRepository.markCancelled + cancelledOn on findTerm/detail; CoverageRepository live-cover read for cancel (repository)
- [x] T5 CancelPolicyPlan (pure) + CancelPolicyService.preview/cancel: lock, validate (AC6), per-cover credits, kept-back/refund plan, save in one transaction, cancel open intents, refunds after commit; CancelPolicyServiceTest covering AC3–AC8 (service)
- [x] T6 CancellationController POST /cancellation and /preview with 422 field maps and 201; CancellationEndpointsTest incl. 403 for a policyholder (controller)
- [x] T7 Reads: PolicyService detail cancelledOn + CREDIT lines; MyCoverService DueReason.CANCELLATION + cancellation block + refund-waiting alongside cancelled; pay-after-cancel works; BrokerPolicyReadTest + MyCoverTest cases (service)

## Web

- [x] T8 lib/cancelCopy.ts with cancelCopy.test.ts (preview lines, settle/still-owed lines, save label, confirm summary, banner, P1/P6 sentences)
- [x] T9 B4 Cancel tab: ChangePolicyPage tab + blocked states, CancelPolicyForm (debounced preview, refund pieces, confirm dialog, 422 mapping, Keep the policy)
- [x] T10 B3 cancelled state: Cancel action, pill/period/balance note, CoverTimeline cancelledFrom, CREDIT rows in ledgerRows (+ ledgerRows.test), saved banner
- [ ] T11 P1 cancelled card variants (refund on its way / refunded / still owes + Pay) and timeline striping in HomePage
- [ ] T12 P6 CancellationPage at /me/cancellation (used/unused bar, credit lines, kept back, refund pill and Stripe sentence; redirect when not cancelled)

## Docs

- [ ] T13 DECISIONS.md: CREDIT line instead of reverse+rebill; status flips at save with tense from the date; shortfall stays owed and payable

## Verification

- [ ] T14 Run both suites with coverage on the changed files (testing skill)
- [ ] T15 Run QA against acceptance criteria, incl. the app at 1280 px light and 390 px dark (qa skill)
