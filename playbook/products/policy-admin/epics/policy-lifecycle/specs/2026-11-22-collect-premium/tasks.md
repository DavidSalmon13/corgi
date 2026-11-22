# Tasks — Collect premium

> Epic: policy-lifecycle
> Spec: 2026-11-22-collect-premium
> Branch: feat/policy-lifecycle/collect-premium

PR slices (at most 10 files each): T1 · T2–T4 · T5–T7 · T8–T10 · T11–T13 · T14–T15 · T16–T20.

## Spike

- [ ] T1 Webhook spike: add `com.stripe:stripe-java` to api/build.gradle.kts and a throwaway endpoint that verifies the signature on the raw body. Run one test-mode PaymentIntent through `stripe listen` and record the result in DECISIONS. The spike code is replaced in T7 and T8.

## Tests first (TDD)

- [ ] T2 Add `StripeTestSupport` (a signed-event builder using the `t=…,v1=` HMAC-SHA256 header, and a fake `StripeGateway`) and a `PaymentTestData` helper that issues a policy and signs in the owner.
- [ ] T3 Write failing API tests:
  - webhook signature handling (AC8);
  - idempotency, including a replay and two copies at once (AC9);
  - status transitions and ledger lines (AC5, AC6, AC10);
  - start-payment rules: amount due, 409 while processing, OPEN reuse and ownership (AC2, AC7, AC12);
  - the saved card (AC3).
- [ ] T4 Write failing web unit tests for `paymentCopy.ts` and the `ledgerRows` payment states (AC4–AC6, AC11).

## Backend

- [ ] T5 Add migration `V6__payments.sql`: the payments and processed_stripe_events tables, `customers.stripe_customer_id`, and `ledger_entries.payment_id` and `stripe_event_id` with the PAYMENT check.
- [ ] T6 Add a `NewEntry.payment(...)` factory, and make `LedgerPoster.check` require `payment_id` and `stripe_event_id` on PAYMENT lines.
- [ ] T7 Add `StripeGateway`, `StripeJavaGateway`, the `plainly.stripe.*` config and the disabled mode, and add the Stripe keys to `.env.example`.
- [ ] T8 Add `WebhookHandler` and `StripeWebhookController`, and exempt `/api/stripe/webhook` in `AuthFilter`.
- [ ] T9 Add `PaymentService` and `MyPaymentsController`: start a payment, confirm with the saved card, and GET a payment for polling.
- [ ] T10 Extend the read models: `MyCover.payment` and `paymentsEnabled`, the Stripe refs on `LedgerLine`, and `paymentWaiting` on `Detail` and `ListRow`.

## Web

- [ ] T11 Add `@stripe/stripe-js` and `@stripe/react-stripe-js`, and write `web/src/lib/paymentCopy.ts` with the P5 and P1 copy.
- [ ] T12 Add `web/src/pages/me/PayPage.tsx` (P5) with the card form, the saved card and the Payment Element, plus the `/me/pay` route.
- [ ] T13 Add the waiting, paid and didn't-go-through states to PayPage, with polling.
- [ ] T14 Add the pay button and the waiting and failed states (P1) to the HomePage `AmountCard`.
- [ ] T15 Broker side: show payment and failed-attempt lines in `ledgerRows.ts` with their `pi_` refs, and show "Waiting for the bank" on B3 and B1.

## Docs

- [ ] T16 README: Stripe setup, `stripe listen` and the test cards.
- [ ] T17 Add DECISIONS entries:
  - failed attempts are $0 ledger lines;
  - only webhooks change a payment's status;
  - card details come from charge events;
  - the API serves the publishable key;
  - abandoned OPEN intents are reused or left alone.

## Verification

- [ ] T18 Get both test suites green (testing skill). Then do a manual `stripe listen` pass with the 4242 and 0002 cards, checking the P5, P1 and B3 states in the browser.
- [ ] T19 Update `playbook/standards/global/tech-stack.md` to the real stack (Spring Boot / Java 21, JdbcClient, Flyway).
- [ ] T20 Run QA against AC1–AC13 (qa skill).
