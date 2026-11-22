# Cancel a policy and refund unused days

> Epic: ../../epic.md
> Status: shaped
> Branch: feat/policy-lifecycle/cancel-refund
> Covers: /api/broker/policies/{id}/cancellation, /api/broker/policies/{id}/cancellation/preview, api/src/main/java/**/policy/Cancel*.java, api/src/main/resources/db/migration/V9__cancellations.sql, web/src/pages/broker/CancelPolicyForm.tsx, web/src/lib/cancelCopy.ts, /me/cancellation, web/src/pages/me/CancellationPage.tsx

## Overview

A broker cancels a policy from a chosen day. Nothing is edited. A CANCEL policy transaction posts one credit line per live cover for its unused days (annual price × unused days ÷ days in term, rounded half-up once per line). Every original charge stays visible above the credits. The credit first pays off anything still owed. The rest goes back to the customer's card through Stripe straight away: latest payment first, never more than a payment collected, and settled only by a signed webhook. If the credit is smaller than what's owed, the rest stays due for the days the customer was covered. This is the last spec in the epic. It serves these success criteria:
- the cancellation refund equals the unearned premium minus any unpaid balance, and Stripe refund webhooks settle it
- every confirm screen previews the money
- no ledger row is ever edited

It reuses the refund machinery that correct-effective-date built (the refunds table, sending after commit, webhook settlement and retry).

## Goals & User Stories

- As a **broker**, I can cancel a policy from a chosen day, give the customer's reason, and see each cover's unused days, anything kept back and the refund before I confirm, so the customer gets back exactly what's owed.
- As a **broker**, when a cancellation refund fails I see it and can retry, the same as for a correction.
- As a **policyholder**, I see that my cover ends (or has ended), what's coming back to my card and how it was worked out, in plain words.
- As a **policyholder**, if I still owe for the days I was covered, I see that amount and can pay it.

## Acceptance Criteria

Worked example, matching the designs. A GL policy runs Sep 1, 2026 → Sep 1, 2027 (365 days) at $1,200.00/yr, paid. Extra cover at $730.00/yr was corrected to start Oct 8 ($656.00). The $642.00 for it is paid, and the $14.00 from the correction is still owed. Today is Nov 22, 2026. The broker cancels from Dec 1, 2026: 91 days used, 274 unused.

1. AC1: On B3, an active policy shows a **Cancel** action that opens B4 on the **Cancel** tab. B4's tabs read Add cover · Correct · Cancel. A cancelled policy shows no Add cover, Correct or Cancel actions, and B4 shows each tab as blocked ("This policy is cancelled.").

2. AC2: The Cancel form has a **Cover ends on** date field, with the hint "All cover stops at the start of this day. No cancellation fee." It also has a required **Reason**, with a note that the customer sees it word for word.

3. AC3: The preview runs the same code path as save and writes nothing. For Dec 1 it shows:
   - "Base cover, unused days · $1,200.00 × 274 ÷ 365 · −$900.82"
   - "Extra cover, unused days · $730.00 × 274 ÷ 365 · −$548.00"
   - "Unpaid balance kept back · Correction still owed · +$14.00"
   - "Refund to card ending 4242 · Sent through Stripe when you confirm · $1,434.82", listing each piece: $642.00 from the latest payment, then $792.82 from the first
   - "Customer will see: Your cover ends on Dec 1, 2026. We're sending $1,434.82 back to your card ending 4242."
   - the save label "Cancel policy and refund $1,434.82", plus a **Keep the policy** button

4. AC4: Each live cover gets one credit line, priced over max(its start, the cancel day) → its end. A cover that starts on or after the cancel day is credited in full, equal to its charge to the cent. Cancelling on the policy start day credits everything, and the paid amount is refunded in full.

5. AC5: When the credit is smaller than the balance, nothing is refunded. Example: an unpaid policy ($1,200.00 due) cancelled from Dec 1. The preview shows −$900.82, then "Still owed for the 91 days covered · $299.18", and the save label "Cancel policy". The customer-will-see line reads "Your cover ends on Dec 1, 2026. You owe $299.18 for the days you were covered."

6. AC6: Validation returns 422 with messages under the field to fix:
   - `cancelDate`: missing; before the policy start; or on or after the policy end.
   - `reason`: blank, or over 500 characters.
   - `policy`: already cancelled; or a payment is waiting for the bank ("A payment is waiting for the bank. Cancel once it settles.").

7. AC7: Save asks "Cancel this policy?" with a one-line summary ("All cover ends on Dec 1, 2026. Maya gets $1,434.82 back and sees your reason."). Confirming writes, in one DB transaction:
   - a CANCEL transaction (PTX) carrying the reason and the cancel day
   - one CREDIT ledger line per cover, with its calculation, linked to the cover
   - the policy set to CANCELLED, with `cancelled_on`
   - any open (unpaid, not processing) PaymentIntent cancelled
   - each refund recorded as PENDING

   Refunds are sent only after commit, with per-attempt idempotency keys. The save returns 201. B3 then shows a once-only banner: "Cancelled. Cover ends on Dec 1, 2026 and $1,434.82 is on its way to Maya's card. Cancellation PTX-… · LE-…, LE-…".

8. AC8: No ledger row is ever updated or deleted. Two concurrent cancellations of the same policy result in one; the second gets a 422 ("This policy is already cancelled."). REFUND lines post only on a signed succeeded webhook, and replaying the event posts nothing. A failed refund shows on B3 with **Retry refund**, as for corrections.

9. AC9: For a cancelled policy, B3 shows:
   - the status pill "Cancelled"
   - the period "Sep 1, 2026 → Dec 1, 2026 (cancelled)"
   - the balance note "Refund of $1,434.82 on its way" while the refund is pending
   - the timeline with the unused part striped from Dec 1
   - the ledger lines "Base cover, unused days refunded" and "Extra cover, unused days refunded" with their calculations and PTX, followed by the refund line(s)

10. AC10: On P1, a cancelled policy shows a **Cancelled** card:
    - while the refund is pending: "Your cover ends on Dec 1, 2026. We're sending $1,434.82 back to your card ending 4242." ("ended on" once the day has passed)
    - once settled: "We sent $1,434.82 back to your card ending 4242."
    - while still owing: "$299.18 due · for the 91 days you were covered", with a Pay button

    The protection line and cover period end on the cancel day, and the timeline stripes the unused days. A "See how we worked it out" link opens P6.

11. AC11: P6 (`/me/cancellation`) shows:
    - "Your cover has ended" (or "Your cover ends on Dec 1, 2026")
    - a bar with the days used against the striped unused days ("91 days used. The striped part, 274 unused days, comes back to you.")
    - each credit line with its calculation, the amount kept back, and "Money back to card ending 4242 · No cancellation fee · $1,434.82"
    - the pill "Refund on its way" / "Refunded", with the matching sentence about Stripe and 5–10 days

    For a policy that isn't cancelled, P6 sends the user back to P1.

12. AC12: All new copy is plain language. jargon.test passes: no "premium", "pro-rata", "unearned" or "endorsement" in customer-facing text unless it is explained.

## Technical Approach

Layered controller → service → repository ([backend/layers](../../../../../../standards/backend/layers.md)). It builds on the merged correction code.

- **Schema (V9 `V9__cancellations.sql`):**
  - `policies.cancelled_on date`, with checks: `(status = 'CANCELLED') = (cancelled_on is not null)`, and `cancelled_on >= start_date and cancelled_on < end_date`. `end_date` stays the original term end because it drives the days in the term.
  - `ledger_entries.entry_type` gains `CREDIT`, with a check `ledger_credit_traced`: a CREDIT has `amount_cents < 0`, a transaction, a coverage and a calc.
- **Ledger:**
  - `EntryType.CREDIT` and `NewEntry.credit(date, desc, coverageId, ProRata.Charge unused)`, which negates the amount.
  - A CREDIT branch in `LedgerPostingService.check`. SQL stays in `LedgerRepository`.
  - Why a credit line and not reverse plus rebill: it gives the designs' two lines, and the original charges stay live and unstruck. It is recorded in DECISIONS.md.
- **Refund planning moves to billing:**
  - `allocate` and `CorrectionPlan.PlannedRefund` move from `CorrectCoverageService` into `RefundService.plan(policyId, owedCents)`, which returns `List<PlannedRefund>`, and `RefundService.recordAndSendAfterCommit(policyId, txId, plan)`.
  - `CorrectCoverageService` calls both. The existing correction tests must stay green.
- **`CancelPolicyService` (policy, `@Transactional`):**
  - Flow: load → validate → pure `CancelPolicyPlan`, shared by `preview` and `cancel`.
  - Load locks through `PolicyRepository.findTerm(id, true)`, checks `hasPaymentProcessing`, and reads live covers through `CoverageRepository` (non-replaced).
  - Each cover's credit is `ProRata.charge(annual, max(cancelDate, from), to, start, end)`, skipping zero days. A cover's `effective_to` is the term end.
  - `balanceAfter = ledger.balance + Σcredits`, and `owed = −balanceAfter − refunds.unsettledTotal`. If `owed > 0` → `refunds.plan`.
  - Save:
    1. `transactions.insert("CANCEL", cancelDate, today, broker, reason)`
    2. `poster.post(credits)`
    3. new `PolicyRepository.markCancelled(id, cancelDate)`
    4. `payments.cancelOpenIntents` (422 if it returns false)
    5. `refunds.recordAndSendAfterCommit`
  - Returns `Cancelled(txDisplayId, creditDisplayIds, balanceCents, refunds)`.
- **`CancellationController`:** `POST /api/broker/policies/{id}/cancellation` and `/preview`. It parses a string request record (`cancelDate`, `reason`), returns 422 field maps and 201 on save, and calls one service method per endpoint.
- **Reads:**
  - `findTerm` and `PolicyViews.Detail` carry `cancelledOn`. Ledger lines include CREDIT with its calc.
  - `MyCoverService`:
    - adds `DueReason.CANCELLATION`
    - adds `cancellation {endsOn, usedDays, unusedDays, termDays, credits[{kind, annualCents, days, amountCents}], keptBackCents, refundCents, refundStatus, cardLast4, settledOn}`, used by P1 and P6
    - lets `RefundWaiting` show alongside the cancelled state
  - Policyholders can still pay a balance left on a cancelled policy (`PaymentService.start` already allows it; a test pins it).
- **Web:**
  - `ChangePolicyPage`: `Tab` gains `'cancel'`, with `blockedReason` per tab.
  - New `CancelPolicyForm`: a debounced preview `useQuery`, a confirm dialog and 422 mapping, reusing `changeForm.tsx` pieces.
  - New `lib/cancelCopy.ts`.
  - `PolicyWorkspace`: the Cancel action, the cancelled header and balance note, `CoverTimeline cancelledFrom`, and the saved banner.
  - `ledgerRows`: CREDIT rows ("… unused days refunded").
  - `HomePage`: the cancelled card variants, ahead of `homePaymentState`'s plain "ended".
  - New `pages/me/CancellationPage.tsx` at `/me/cancellation`.

## Out of Scope

- Short-rate fees, minimum premiums or any cancellation fee.
- Reinstating a cancelled policy, or changing the cancel day once it is saved.
- Cancelling a single cover (the extra cover only); that would be a correction or a future spec.
- Writing off a balance still owed after the credit.
- Cancelling while a payment is still with the bank; it is blocked until the payment settles.
- Emails, the full statement, and the "how we got this" drawer (separate epic).
- A scheduled job to flip status on the cancel day (status changes at save; the copy uses tense from the date).

## Standards Applied

- [global/tech-stack](../../../../../../standards/global/tech-stack.md): Spring Boot 4 / JdbcClient, Flyway, React + Vite + Tailwind v4, Stripe test mode.
- [backend/layers](../../../../../../standards/backend/layers.md): controller → service → repository; only repositories run SQL; cross-module calls go service to service.
- [global/git-workflow](../../../../../../standards/global/git-workflow.md): branch naming, commits and PRs.

## Changelog

| Date | Author | Type | Change | Ref |
|---|---|---|---|---|
| 2026-11-22 | david-schwartzman | created | Initial shaping | — |
