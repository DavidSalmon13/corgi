# Tasks — Correct a start date

> Epic: policy-lifecycle
> Spec: 2026-11-22-correct-effective-date
> Branch: feat/policy-lifecycle/correct-effective-date

## Failing tests first

- [x] T1 Write failing `CorrectCoverageServiceTest` (api/src/test/java/app/plainly/policy/): Oct 15→Oct 8 −$642.00/+$656.00/+$14.00; Oct 15→Oct 22 refund $14.00 to latest payment; credit pays open balance first; refund split latest-first and capped; second correction reverses the rebill; every AC6 message; preview writes nothing; concurrent corrections → one; OPEN intents cancelled (AC3–AC6, AC8, AC9)
- [x] T2 Write failing `CorrectionEndpointsTest` (201 + body, 422 fields, 403 policyholder, 404 unknown) and `RefundWebhookTest` (refund succeeded posts one REFUND line traced to refund + event; replay posts nothing; failed → FAILED; retry sends attempt 2; broker-only) plus `MyCoverTest`/`BrokerPolicyReadTest` cases for refundWaiting, live coverages + previousStart, reason (AC7, AC10–AC13)
- [x] T3 Write failing `web/src/lib/correctCopy.test.ts` (undo/corrected lines, net-why text, customer sentence owes-more/refund, settle line, save labels, confirm summary, banner) and refund/correction cases in `myCoverCopy.test.ts` and `ledgerRows.test.ts` (AC3, AC4, AC7, AC12, AC13)

## API

- [x] T4 `V8__corrections_and_refunds.sql`: `coverages.replaced_by_coverage_id`, `refunds` table + seq, `ledger_entries.refund_id` + REFUND trace check; new `RefundRepository` (insert, lockById, markSent/markSucceeded/markFailed, findByPolicy, refundedPerPayment)
- [x] T5 Ledger: `NewEntry.refund`, REFUND branch in `LedgerPostingService.check`, reversal keeps `coverage_id`; `LedgerQueryService.liveChargeFor(coverageId)` over new `LedgerRepository.liveChargeFor`
- [x] T6 `StripeGateway.createRefund` in `StripeJavaGateway` + `FakeStripeGateway`; refund event builders in `StripeTestSupport`
- [x] T7 `CorrectionPlan` + `CorrectCoverageService.preview`: new `CoverageRepository.findLive(policyId, kind)`, `PolicyRepository.hasPaymentProcessing`, live charge via `LedgerQueryService.liveChargeFor`, validation, rebill, refund allocation (payments via `PaymentService`, refunded amounts via `RefundService`)
- [ ] T8 `CorrectCoverageService.correct` (`@Transactional`): `PolicyRepository.findTerm(policyId, true)`, `PolicyTransactionRepository.insert` with new `reason` parameter, `LedgerPostingService.post` reversal + rebill, `CoverageRepository.insert` + new `markReplaced(oldId, newId)`, `PaymentService.cancelOpenIntents` (extracted from the stale-intent loop in `start`), `RefundService.recordPending`, after-commit `RefundService.send`
- [ ] T9 `RefundService` over `RefundRepository` (recordPending, send, retry, settle, fail; failure → FAILED) + `StripeWebhookService` refund events → `RefundService.settle|fail` → REFUND line once via `LedgerPostingService`
- [ ] T10 Thin `CorrectionController` (`/api/broker/policies/{id}/corrections`, `/preview`) → `CorrectCoverageService`, thin `RefundController` retry → `RefundService`; `PolicyService` detail + `CoverageRepository.listForPolicy`/`exists` skip replaced rows and return previousStart, `PolicyTransactionRepository.listForPolicy` returns reason, refunds via `RefundService`; `MyCoverService.cover` for refundWaiting + correction due reason; API suite green

## Web

- [ ] T11 `types.ts` additions + `lib/correctCopy.ts`; `myCoverCopy.ts` and `ledgerRows.ts` updates
- [ ] T12 `ChangePolicyPage.tsx` tabs (Add cover · Correct, per-tab blocked states) + `CorrectCoverForm.tsx` with preview, reason, confirm dialog, 422 mapping (AC1–AC7)
- [ ] T13 `PolicyWorkspace.tsx`: Correct action, saved banner, failed-refund alert + Retry, timeline "was" label; `HomePage.tsx` refund-on-its-way state (AC1, AC7, AC11–AC13)
- [ ] T14 DECISIONS.md: coverage replaced (not edited) on correction; refund sent after commit with per-attempt idempotency key; corrections blocked while a payment is with the bank; REFUND line posted only on webhook success

## Verification

- [ ] T15 Run the test suites and coverage on changed files (testing skill)
- [ ] T16 Run QA against acceptance criteria (qa skill)
