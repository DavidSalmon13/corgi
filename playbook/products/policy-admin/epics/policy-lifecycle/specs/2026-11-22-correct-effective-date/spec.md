# Correct a start date

> Epic: ../../epic.md
> Status: shaped
> Branch: feat/policy-lifecycle/correct-effective-date
> Covers: /api/broker/policies/{id}/corrections, /api/broker/policies/{id}/corrections/preview, /api/broker/refunds/{id}/retry, api/src/main/java/**/policy/Correct*.java, api/src/main/java/**/billing/Refund*.java, api/src/main/resources/db/migration/V8__corrections_and_refunds.sql, web/src/lib/correctCopy.ts, web/src/pages/broker/CorrectCoverForm.tsx

## Overview

A broker fixes the start date of a policy's extra cover (product liability) after it has been charged, and possibly paid. Nothing is edited. A CORRECT policy transaction posts a reversal that undoes the live charge to the cent, plus a corrected charge priced from the new date, so the original line stays visible next to the fix. If the customer now owes more, the extra is added to their balance. If they owe less, the credit first pays off any open balance, and the rest goes back to their card through Stripe straight away: latest payment first, never more than a payment collected, and settled only by a signed webhook. This delivers the epic's trial scenario. With the extra cover moved from Oct 15 to Oct 8 there are 7 more days at $2.00, so $14.00 is due. It also serves the brief's success criteria: pro-rata matches the hand calculation, ledger rows are never edited, every confirm screen previews the money, and status changes only through idempotent webhooks. The refund machinery (refunds table, gateway call, webhook settlement, retry) is built here so `cancel-refund` can reuse it.

## Goals & User Stories

- As a **broker**, I can correct when the extra cover started and see the undo, the corrected charge and the net change (or refund) before I save, so the fix is visible rather than silent.
- As a **broker**, I must give a reason, which the customer sees word for word, so every change explains itself.
- As a **broker**, when a refund fails I see it and can retry, so money we owe never gets stuck.
- As a **policyholder**, after a correction I see in plain words what changed and what I owe (or what's coming back to my card), so no charge is a surprise.

## Acceptance Criteria

Worked example throughout: a GL policy runs Sep 1, 2026 → Sep 1, 2027 (365 days), with extra cover at $730.00/yr ($2.00/day) from Oct 15. That charge is $730.00 × 321 ÷ 365 = $642.00, and it is paid.

1. AC1: On B3, an active policy with extra cover shows a **Correct** action. It opens B4 on the **Correct** tab. B4's tabs read Add cover · Correct, and each tab handles its own blocked states. A policy that already has extra cover can still use Correct.
2. AC2: The Correct form shows which cover is being corrected ("Extra cover · Product liability ($730.00 a year)"), the date it currently starts on, a **Correct start date** date field, and a required **Reason**. A note says the customer sees the reason word for word.
3. AC3: The preview runs the same code path as save and writes nothing. For Oct 15 → Oct 8 it shows:
   - "Undo: extra cover from Oct 15 · $730.00 × 321 ÷ 365 · −$642.00"
   - "Corrected: extra cover from Oct 8 · $730.00 × 328 ÷ 365 · +$656.00"
   - Net change "+$14.00 (7 more days × $2.00)"
   - "Customer will see: You owe $14.00 because we fixed when your extra cover started."
4. AC4: For Oct 15 → Oct 22 the preview shows a rebill of +$628.00 (314 days), a net change of −$14.00 (7 fewer days), the settle line "Nothing is owed, so the $14.00 is refunded to card ending 4242 through Stripe as soon as you save", and the save label "Save and refund $14.00".
5. AC5: A credit is applied to the open balance first, and only the rest is refunded. The refund is split across succeeded payments latest first, each capped at what that payment collected minus what has already been refunded from it, and the preview lists each piece (amount and card).
6. AC6: Validation returns 422 with messages under the field to fix:
   - `startDate`: missing; before the policy start; on or after the policy end; or the same as the current start ("Extra cover already starts on that day.").
   - `reason`: blank, or over 500 characters.
   - `policy`: the policy is cancelled; it has no extra cover; or a payment is waiting for the bank ("A payment is waiting for the bank. Correct this once it settles.").
7. AC7: Save asks "Save this correction?" with a one-line summary ("Product liability will start on Oct 8 instead of Oct 15…", "Undo −$642.00 · Corrected +$656.00 · Net +$14.00"). Confirming creates, in one DB transaction:
   - a CORRECT transaction (PTX) carrying the reason
   - a REVERSAL line (−$642.00) linked to the charge it undoes
   - the corrected CHARGE (+$656.00) with its calculation
   - a replacement coverage row from Oct 8, with the old row marked as replaced

   It returns 201, and B3 shows a once-only banner: "Saved. Maya now owes $14.00 and we've told her why. Correction PTX-… · undo LE-… · corrected line LE-…".
8. AC8: No ledger row is ever updated or deleted. A second correction reverses the corrected line, not the original. Two concurrent corrections on the same policy result in one correction; the second gets a 422 or re-validates against the new start.
9. AC9: Any open (unpaid, not processing) PaymentIntent for the old amount is cancelled when a correction saves, so an old pay page can't pay the wrong amount.
10. AC10: A refund is recorded as pending in the same transaction as the correction. It is sent to Stripe only after commit, with an idempotency key per refund attempt. A REFUND ledger line (+amount) is posted only when a signed webhook says the refund succeeded, and it traces to the refund and to the Stripe event. Replaying the event posts nothing new. A failed send or a failed-refund event marks the refund as failed, with the reason.
11. AC11: B3 lists any failed refund: "The $14.00 refund didn't go through. Stripe couldn't send the money to card ending 4242." It has a **Retry refund** button that sends a new attempt for the same refund. Only a broker can retry (403 for a policyholder).
12. AC12: The B3 ledger shows the original charge struck through ("Undone by LE-…"), the reversal ("Undo of LE-…") and the corrected line, highlighted together as one correction, with refund lines referencing their refund or Stripe id. The cover timeline shows a single extra-cover bar: "from Oct 8 · was Oct 15".
13. AC13: On P1, the policyholder sees:
   - when they owe more: "$14.00 due · We fixed when your extra cover started." and a Pay button
   - when a refund is pending: "Refund on its way · We owe you $14.00. It's on its way to your card ending 4242."

   Their cover list and timeline show one extra cover, starting on the corrected date.
14. AC14: All new copy is plain language. jargon.test passes: no "effective date", "pro-rata", "endorsement" or "reversal" in customer-facing text unless it is explained.

## Technical Approach

- **Schema (V8):**
  - `coverages.replaced_by_coverage_id` (unique, references coverages)
  - a `refunds` table (`display_id` RF-xxxx, `policy_id`, `payment_id`, `policy_transaction_id`, `amount_cents > 0`, `status` PENDING|SUCCEEDED|FAILED, `stripe_refund_id`, `attempt`, `failure_reason`, timestamps)
  - `ledger_entries.refund_id`, plus a check that REFUND lines carry `refund_id` and `stripe_event_id`

  `reverses_entry_id unique` and the CORRECT transaction type already exist (V1).
- **Plan/preview:** `CorrectionPlan` + `CorrectCoverageService.preview/correct`, mirroring `AddCoverageService`: load → validate → pure `plan()`, with `select … for update` on the policy, then separate reads. The line to undo is the live CHARGE for the live PRODUCT_LIABILITY coverage that has no reverser; it may be an ISSUE line. The reversal keeps `coverage_id`. The rebill uses `ProRata.charge(annual, newStart, policyEnd, policyStart, policyEnd)`. The refund plan comes from `balance + net`: if that is negative, it is allocated over SUCCEEDED payments, newest first, each capped at collected minus non-failed refunds.
- **Save:** insert the CORRECT transaction (with reason); `LedgerPostingService.post` the reversal and the rebill; insert the new coverage and set `replaced_by` on the old one; cancel OPEN intents (as `PaymentService.start` does); insert PENDING refunds; register an after-commit hook that calls `RefundService.send`.
- **Refunds:** `StripeGateway.createRefund(paymentIntentId, amount, idempotencyKey "refund-<id>-<attempt>", metadata)`, implemented in `StripeJavaGateway` and `FakeStripeGateway`. Webhook: `refund.created`/`refund.updated` with status succeeded post the REFUND line once (on the PENDING→SUCCEEDED transition, under a row lock); status failed and `refund.failed` set FAILED. Dedup uses `processed_stripe_events` as today. `POST /api/broker/refunds/{id}/retry` bumps `attempt` and sends again.
- **Reads:**
  - `PolicyQueries.coverages()` returns live rows plus `previousStart`.
  - Transactions return `reason`.
  - Policy detail returns refunds.
  - `MyCoverController` adds `refundWaiting {amount, cardLast4}`, and a CORRECT transaction maps to due-reason text "We fixed when your extra cover started."
- **Web:**
  - `ChangePolicyPage` gets tabs.
  - New `CorrectCoverForm` (debounced preview, `useQuery` like `AddCoverForm`, a confirm dialog, 422 field mapping).
  - New `lib/correctCopy.ts`.
  - `PolicyWorkspace`: Correct action, failed-refund alert with retry, timeline "was" label.
  - `HomePage`: refund-on-its-way state.
  - `ledgerRows`: refund refs.

## Out of Scope

- Correcting the base cover or the policy start/end dates (cancel and reissue instead).
- Correcting the extra cover's end date or price.
- The policy history, statement and full "how we got this" views (separate epic). This spec shows only the B3 ledger and the P1 state.
- Emailing the customer about the change (Resend belongs to the statements epic).
- Correcting while a payment is still with the bank; it is blocked until the payment settles.
- Holding a credit balance instead of refunding.
- A claims-free statement for back-dated starts.

## Standards Applied

- [global/tech-stack](../../../../../../standards/global/tech-stack.md): Spring Boot 4 / JdbcClient, Flyway, React + Vite + Tailwind v4, Stripe test mode.
- [global/git-workflow](../../../../../../standards/global/git-workflow.md): branch naming, commits and PRs.

## Changelog

| Date | Author | Type | Change | Ref |
|---|---|---|---|---|
| 2026-11-22 | david-schwartzman | created | Initial shaping | — |
