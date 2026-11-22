# References for Cancel a policy and refund unused days

## Related Specs

- **[Related]** [2026-11-22-issue-policy](../2026-11-22-issue-policy/spec.md): it owns `api/src/main/java/**/policy/**`, `db/migration/**` and `web/**` as foundation-wide globs. This spec owns only `policy/Cancel*.java`, `V9__cancellations.sql`, `CancelPolicyForm.tsx`, `lib/cancelCopy.ts` and `pages/me/CancellationPage.tsx` inside them.
- **[Related]** [2026-11-22-correct-effective-date](../2026-11-22-correct-effective-date/spec.md): it owns `billing/Refund*.java`, the `refunds` table, refund webhooks and retry. This spec reuses that code and moves the allocation (`allocate`, `PlannedRefund`) from `CorrectCoverageService` into `RefundService`, so both changes share one refund plan. Kept separate because a correction changes one cover's dates, while a cancellation ends the whole policy.
- **[Related]** [2026-11-22-add-coverage](../2026-11-22-add-coverage/spec.md): its B4 `ChangePolicyPage.tsx` hosts the new Cancel tab next to Add cover and Correct.

## Upstream Meeting

None. The sources are `ASSIGNMENT.md` and the epic's product brief ("the cancellation refund equals the unearned premium minus any unpaid balance, and Stripe refund webhooks settle it").

## Visuals

The designs in `design/plainly/` (see its README):
- `B4-ChangePreview.dc.html`: the Cancel tab ("Cover ends on", reason, "What Maya gets back", "Cancel policy and refund $1,434.82", "Keep the policy").
- `B3-Workspace.dc.html`: the `cancelled` state (pill, period, balance note, unused-days ledger lines LE-0009/LE-0010, refund LE-0011).
- `P1-Home.dc.html`: the `cancelled` state; `P2-Timeline.dc.html`: the `cancelled` state (striped unused days).
- `P6-Cancellation.dc.html`: `refund waiting` and `refunded`.

## Similar Implementations

### Correct coverage service
- **Location:** `api/src/main/java/app/plainly/policy/CorrectCoverageService.java`, `CorrectionPlan.java`, `CorrectionController.java`
- **Relevance:** the same preview/save shape, and the refund plan this spec reuses.
- **Key patterns:** load (`PolicyRepository.findTerm(id, true)` lock, `hasPaymentProcessing`) → validate (policy problem alone under `policy`, field errors in form order, reason ≤ 500) → pure plan; `payments.cancelOpenIntents`; `refunds.recordPending` + after-commit `refunds.send`; `allocate` (newest payment first, capped at collected minus non-failed refunds).

### Refunds and webhooks
- **Location:** `api/src/main/java/app/plainly/billing/RefundService.java`, `RefundRepository.java`, `StripeWebhookService.java`, `RefundController.java`
- **Relevance:** sending, retrying and settling cancellation refunds needs no new billing code beyond the moved plan.
- **Key patterns:** idempotency key `refund-<id>-<attempt>`; only a definite refusal marks FAILED; REFUND line posted once on PENDING→SUCCEEDED under a row lock; `unsettledTotal`, `refundedPerPayment`.

### Ledger and pricing
- **Location:** `api/src/main/java/app/plainly/ledger/LedgerPostingService.java`, `NewEntry.java`, `EntryType.java`, `LedgerQueryService.java`; `pricing/ProRata.java`
- **Relevance:** the new CREDIT line type and the unused-days price.
- **Key patterns:** `check()` enforces tracing per entry type; `ProRata.charge(annual, from, to, termStart, termEnd)` (end-exclusive, one half-up rounding).

### B4 forms
- **Location:** `web/src/pages/broker/ChangePolicyPage.tsx`, `CorrectCoverForm.tsx`, `changeForm.tsx`, `web/src/lib/correctCopy.ts`
- **Key patterns:** `?tab=` with `blockedReason` per tab; debounced preview `useQuery`; confirm dialog; `validationOf` → field errors; shared `INPUT`/`BTN`/`BlockedCard`/`ChangeSavedState`.

### Read side
- **Location:** `api/.../policy/PolicyService.java`, `PolicyViews.java`, `api/.../me/MyCoverService.java`; web `PolicyWorkspace.tsx`, `HomePage.tsx`, `lib/ledgerRows.ts`, `lib/paymentCopy.ts` (`homePaymentState`), `components/CoverTimeline.tsx` (`cancelledFrom`, not used yet)

### Test fixtures
- `CorrectionTestData` (`paidWithExtraCoverFromOct15`, `payInFull`), `PaymentTestData` (`ownerWithBalanceDue`), `StripeTestSupport` (`refundSucceeded`, `refundFailed`), `FakeStripeGateway` (`refundsFor`, `failRefunds`); `@IntegrationTest` fixes today at 2026-11-22.
