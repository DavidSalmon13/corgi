# Collect premium

> Epic: ../../epic.md
> Status: done
> Branch: feat/policy-lifecycle/collect-premium
> Covers: /me/pay, /api/me/payments, /api/stripe/webhook, api/src/main/java/**/billing/**, api/src/main/resources/db/migration/V6__payments.sql, api/src/main/resources/db/migration/V7__payments_stripe_canceled_at.sql, web/src/pages/me/PayPage.tsx, web/src/lib/paymentCopy.ts

## Overview

A policyholder can pay what they owe by card through Stripe test mode, and both roles see the payment as waiting, paid or didn't go through. The app creates a Stripe PaymentIntent for the open balance. The card is entered in the Stripe Payment Element on the Plainly P5 screen, and a card that paid before is offered as "Used last time". The status changes only when a signed Stripe webhook arrives, and each event id is processed once (DECISIONS, 2026-11-22: "Only Stripe webhooks change payment status"). A successful payment posts an append-only `PAYMENT` ledger line with a negative amount, which traces to the payment row, the PaymentIntent and the Stripe event. A declined attempt posts a $0.00 "No money moved" line, so the attempt stays visible, as in B3 and P4.

This spec serves the brief's goal 3 and the success criterion "payment status changes only through signed Stripe webhooks; replaying an event id posts nothing new", plus the story "as a policyholder I can pay what's due by card and see whether the payment is pending, paid or failed". Refunds and the refund webhook belong to correct-effective-date and cancel-refund. The statement and "How we got this" views are a separate epic. The first task is a spike: a real PaymentIntent and signed webhook round trip through `stripe listen`, which retires the tech plan's webhook-plumbing risk.

UI reference: `design/plainly/P5-Pay.dc.html` (card form, waiting for your bank, paid, didn't go through), `P1-Home.dc.html` (amount due, nothing due, payment waiting, payment failed), `B3-Workspace.dc.html` (payment and failed-attempt ledger rows) and `P4-HowWeGotThis.dc.html` (the payment refs only).

## Goals & User Stories

- As a **policyholder**, I pay what's due by card from my home screen. I see "Waiting for your bank" until Stripe confirms, then "Paid" or "Didn't go through", with a clear next step.
- As a **returning policyholder**, I can pay with the card I used last time without typing it again.
- As a **broker**, I see each payment and failed attempt on the policy's ledger with its Stripe reference. While a payment is in flight, the balance shows that it's waiting for the bank.
- As a **reviewer**, I can trace every payment line to its Stripe PaymentIntent and event id, and replaying a webhook changes nothing.

## Acceptance Criteria

1. AC1: When an amount is due, P1 Home shows "Pay $X", which opens `/me/pay`. When nothing is due there is no pay button, and `/me/pay` says "You're all paid up." with a link back to your cover.
2. AC2: `/me/pay` follows the P5 card-form state:
   - The heading reads "Pay $X".
   - The reason line reuses the `dueReason` copy, and its "See why" links to `/me`.
   - The Stripe Payment Element takes a new card.
   - The note reads "Card payments are handled by Stripe. We never see your full card number."
   - The amount is always the server's balance due. The browser never sends an amount.
3. AC3: After a successful card payment, the next visit to `/me/pay` offers "Card ending NNNN · Used last time", selected by default. "A different card" reveals the Payment Element. Paying with the saved card needs no card details.
4. AC4: After the card is submitted, P5 shows the "Waiting for your bank" state:
   - The text reads "Your $X payment is on its way. Your bank hasn't confirmed it yet. We'll show it as paid as soon as it does. You don't need to pay again."
   - It shows the card ending and the date.
   - It polls until the webhook settles the payment. The browser's confirm result never marks a payment paid or failed.
5. AC5: When `charge.succeeded` arrives:
   - The payment becomes SUCCEEDED.
   - One `PAYMENT` ledger line of −amount is posted, "Payment, card ending NNNN", linked to the payment and carrying the Stripe event id.
   - P5 shows "Paid · You're all set, {first name}. Your bank confirmed the payment. You owe nothing now." If something is still due, it shows that amount instead.
   - P1 shows "You're all paid up."
6. AC6: When `charge.failed` arrives:
   - The payment becomes FAILED, and the balance is unchanged.
   - A $0.00 `PAYMENT` line is posted, "Payment attempt, card ending NNNN", marked "No money moved".
   - P5 shows "Didn't go through" with "Try again" and "Use a different card".
   - P1 shows "Your $X payment didn't go through. No money moved." with "Try again".
7. AC7: While a payment is PROCESSING (from `payment_intent.processing`), P1 shows "Waiting for your bank". Starting another payment is refused with 409 "Your last payment is still on its way." in two cases:
   - a payment is PROCESSING;
   - Stripe already has the money for an earlier intent (processing or succeeded) and its webhook hasn't arrived yet. Cards usually skip `payment_intent.processing`.

   Opening a new payment cancels at Stripe every earlier intent nobody finished paying (open or declined), so a pay page left open in another tab can't charge the customer twice. Cancelling doesn't change a payment's status. Paying with the saved card is refused with 409 `payment_out_of_date` when the payment was replaced or the amount due changed. A saved-card confirm that fails never says "Nothing was charged".
8. AC8: `POST /api/stripe/webhook` needs no session. Signatures are verified against the raw request body with the configured signing secret. A missing or invalid `Stripe-Signature` gets 400 and changes nothing.
9. AC9: Each Stripe event id is processed at most once. Replaying an event, or delivering two copies at once, posts no second ledger line and changes no status. The replay still gets 200, so Stripe stops retrying.
10. AC10: These events are answered 200 without failing:
    - An unknown event type is recorded and ignored.
    - An event for an unknown PaymentIntent is ignored.
    - A `charge.succeeded` that arrives after FAILED still records the money, because the money did move.
    - A `payment_intent.payment_failed` or `payment_intent.canceled` moves a PROCESSING payment to FAILED, with no ledger line, and never undoes a payment that succeeded.

    The exception is a signed event we can't read. It gets 500 and isn't recorded, so Stripe sends it again.
11. AC11: The B3 ledger shows payment lines as "Paid" and failed attempts as "Didn't go through", each with its `pi_…` reference. While a payment is PROCESSING, B3 and the B1 list show "Waiting for the bank" next to the balance.
12. AC12: A policyholder can only create or read payments for their own policy. Someone else's payment id gets 404, and `/api/me/**` stays policyholder-only.
13. AC13: Configuration and docs:
    - Stripe keys come from env: `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY` and `STRIPE_WEBHOOK_SECRET`. They are listed in `.env.example`, are test keys only and are never committed.
    - Payments are on only when all three values are set and the keys are test-mode keys (`sk_test_…`, `pk_test_…`). Otherwise the app still starts, and `/me/pay` explains that payments aren't set up. Without the webhook secret no payment could be recorded, and this demo never takes real money.
    - The README covers `stripe listen --forward-to localhost:8080/api/stripe/webhook` and the test cards 4242 4242 4242 4242 (paid) and 4000 0000 0000 0002 (declined).
    - DECISIONS records the choices in the Technical Approach.

## Technical Approach

**Schema (`V6__payments.sql`)**

- `customers.stripe_customer_id text unique`.
- A `payments` table with:
  - `id`, a `display_id` of the form `PAY-0001` from a sequence, `policy_id`, and `created_by` → app_users;
  - `stripe_payment_intent_id text unique not null` and `amount_cents bigint check (> 0)`;
  - `status` in OPEN, PROCESSING, SUCCEEDED or FAILED;
  - `payment_method_id`, `card_brand`, `card_last4`, `failure_reason`, `created_at` and `settled_at`.
- `processed_stripe_events (event_id text primary key, type text, received_at timestamptz)`.
- `ledger_entries` gains `payment_id bigint references payments` and `stripe_event_id text`, with a check that a `PAYMENT` line has both.

A payment's status is the one mutable value in the money path, and only the webhook handler changes it. The ledger stays append-only.

**`billing` package**

- `StripeGateway` interface: `ensureCustomer`, `createPaymentIntent(customerId, amount, idempotencyKey, metadata)`, `confirmWithSavedCard(piId, pmId)` and `verify(payload, sigHeader) → event`. `StripeJavaGateway` implements it with `com.stripe:stripe-java`.
- `PaymentService.start(me)`:
  - Locks the policy row and computes the amount due from `LedgerQueryService.balance`.
  - Refuses if nothing is due, and returns 409 if a payment is PROCESSING.
  - Reuses the latest OPEN payment when its amount still matches.
  - Otherwise creates a PaymentIntent with `setup_future_usage=on_session` (so the card can be reused), metadata `payment_id` and `policy_id`, and the idempotency key `pay-<id>`.
  - Returns `{paymentId, clientSecret, publishableKey, amountCents, savedCard}`.
- The saved card is the card on that customer's latest SUCCEEDED payment.
- `POST /api/me/payments/{id}/saved-card` confirms with that card on the server. It returns `{status, clientSecret?}` so the browser can handle any 3-D Secure step.
- `GET /api/me/payments/{id}` is what the pay page polls.
- `StripeWebhookController` takes the raw `@RequestBody String` and passes it to `gateway.verify`, returning 400 on failure.
- `WebhookHandler.handle(event)` runs in one transaction:
  1. `insert into processed_stripe_events … on conflict do nothing`. Zero rows means a duplicate, so it returns.
  2. `payment_intent.processing` → PROCESSING.
  3. `charge.succeeded` → SUCCEEDED, saves the card details, and posts a PAYMENT −amount line through `NewEntry.payment(...)` and `LedgerPostingService`.
  4. `charge.failed` → FAILED, saves the failure reason, and posts a $0 PAYMENT line.
- `AuthFilter` exempts exactly `/api/stripe/webhook`.

**Read models**

- `MyCover` gains `payment` (the latest non-OPEN payment: status, amount, card last4 and date) and `paymentsEnabled`.
- `LedgerLine` gains `stripePaymentIntentId` and `stripeEventId` for the references column.
- `PolicyViews.Detail` and `ListRow` gain `paymentWaiting`.

**Web**

- Add `@stripe/stripe-js` and `@stripe/react-stripe-js`. The publishable key comes from the start-payment response, so there is no build-time env.
- `PayPage.tsx` (P5) has four states: card form, waiting, paid and didn't go through.
  - It polls with TanStack Query `refetchInterval` while the payment is OPEN or PROCESSING after submit.
  - A new card is confirmed with `stripe.confirmPayment({redirect: 'if_required'})`.
- The copy lives in `web/src/lib/paymentCopy.ts`, taken from P5 and P1.
- Add the `/me/pay` route.
- `HomePage` `AmountCard` gains the pay button and the waiting and failed states.
- `ledgerRows.ts` renders a $0 PAYMENT line as "Didn't go through", with the `pi_…` reference.
- The B3 balance note and the B1 pill show "Waiting for the bank".

**Config**

- `plainly.stripe.secret-key`, `publishable-key` and `webhook-secret` come from env with empty defaults. When they're empty, the gateway reports payments as disabled.
- Tests use a fake `StripeGateway` and a real webhook secret. `StripeTestSupport.signedEvent(json)` computes the `t=…,v1=` HMAC-SHA256 header, so signature checking runs for real.

## Out of Scope

- Refunds, a refunds table and `charge.refunded` (correct-effective-date, cancel-refund).
- Statement, history and the "How we got this" drawer (a separate epic).
- Removing saved cards, multiple saved cards and payment methods other than cards.
- Installments, partial payments and off-session or automatic charging.
- Emails and receipts (Resend).
- Railway deployment config. The spike runs locally through `stripe listen`. On Railway it only needs the three env vars and the webhook endpoint registered in the Stripe dashboard.

## Standards Applied

- [global/git-workflow](../../../../../../standards/global/git-workflow.md): branch naming, commits, and at most 10 files per PR.
- [global/tech-stack](../../../../../../standards/global/tech-stack.md): Spring Boot + JdbcClient + Flyway on the API, React + TanStack Query + Tailwind on the web. The standard still says Node/Express on the backend, which T19 corrects.

## Changelog

| Date | Author | Type | Change | Ref |
|---|---|---|---|---|
| 2026-11-22 | david-schwartzman | created | Initial shaping | — |
| 2026-11-22 | david-schwartzman | hotfix | Corrected AC7: also refuse a new payment when Stripe already has an earlier intent's money; cancel replaced intents; saved card can't pay a replaced payment; no false "Nothing was charged". Covers: +V7 migration, +web/src/lib/paymentCopy.ts | code and security reviews of #35–#41 |
| 2026-11-22 | david-schwartzman | hotfix | Corrected AC10 and AC13: payment_intent.payment_failed/canceled settle a stuck PROCESSING payment; an unreadable event gets 500 and is retried; payments need all three Stripe values and test-mode keys | code and security reviews of #35–#41 |
