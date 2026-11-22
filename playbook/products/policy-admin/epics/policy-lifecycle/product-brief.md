# Policy Lifecycle — Product Brief

> Epic: epic.md · Product: ../../mission.md

## Goals

**Problem.** When brokers fix a mistake, they usually edit records in place, so the history of what was charged, and why it changed, is lost. Policyholders, small-business owners with no insurance background, see pro-rata amounts and "endorsements" they can't follow.

**Goals.**

1. A broker can issue a General Liability policy, add coverage to it, correct it and cancel it.
2. Every action posts append-only, pro-rata ledger entries that a policyholder can see and understand.
3. Premiums are collected and refunds paid through Stripe test mode, and status changes only through idempotent webhooks.
4. The trial scenario works: three weeks after paying, the add-on's start moves a week earlier, and the original charge stays visible next to the correction.

## User Stories

- As a **broker**, I can create a customer (the business plus its owner as the contact) and issue a GL policy with start and end dates at the $1,200/yr premium, so coverage starts with a clear first bill.
- As a **broker**, I can add the $730/yr add-on mid-term and see the pro-rata charge before I confirm, so I know what the customer will owe.
- As a **broker**, I can correct an add-on's effective date and see the reversal, the rebill and the net change (or refund) before I confirm, so the fix is visible rather than silent.
- As a **broker**, I can cancel a policy and see the refund for unused days, net of any unpaid balance, so the customer gets back exactly what's owed.
- As a **policyholder**, I can pay what's due by card and see whether the payment is pending, paid or failed, so I know where I stand.
- As a **policyholder**, after each broker action I can see in plain words what changed and what it costs, so no charge is a surprise.

## Success Criteria

- The trial scenario works end to end. A policy and its add-on are paid. Three weeks later, the add-on's start moves 7 days earlier. The original charge, its reversal and the rebill are all visible, and the extra $14.00 (7 days × $2.00) is shown as due.
- Pro-rata amounts match the hand calculation (end-exclusive days, one half-up rounding per line), and unit tests cover them.
- No ledger row is ever updated or deleted. The database enforces this, not just a code convention.
- Payment status changes only through signed Stripe webhooks. Replaying an event id posts nothing new.
- The cancellation refund equals the unearned premium minus any unpaid balance, and Stripe refund webhooks settle it.
- Every confirm screen previews the money effect before the broker commits.

## Out of Scope

- Policy history, financial statement and the "how we got this" tracing views. These are a separate epic; this epic only shows the amount due and what each action changed.
- Resend emails (policy issued, statements, receipts).
- Short-rate cancellation fees and minimum premiums (cancellation refunds unused days in full).
- Renewals, claims, broker commissions and installment billing (Phase 2).
