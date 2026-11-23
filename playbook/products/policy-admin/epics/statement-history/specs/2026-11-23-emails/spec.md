# Emails: policy issued, payment and refund receipts

> Epic: ../../epic.md
> Status: shaped
> Branch: feat/statement-history/emails
> Covers: api/src/main/java/**/notify/**, api/src/main/resources/db/migration/V10__sent_emails.sql, api/src/main/resources/templates/email/**

## Overview
The policyholder (the business owner) gets three emails through Resend's sandbox:
- one when their broker issues the policy;
- a receipt when a payment settles;
- a receipt when a refund settles.

Each email says, in plain words, what happened, what it cost or returned, why, and what they owe now. It links to their statement.

Resend is the trial's second live integration after Stripe. Each email is sent **once** per event, even when Stripe retries a webhook. It is sent **after** the money change commits, so a failed email never undoes a payment.

Without a verified domain, Resend's sandbox delivers only to the account owner's address. So every demo email goes to one test inbox (`RESEND_TEST_TO`), and the owner's real (fictional) address is recorded and shown in the email. This is documented in DECISIONS.md as simulated delivery.

Serves: brief goal 5, story 6, and the Resend success criterion. Design: no mockup exists, so the emails reuse Plainly's colour tokens, type and tone from `design/plainly/README.md` (calm, plain words, one clear link).

## Goals & User Stories
- As a policyholder, when my broker sets up my cover, I get an email saying what I'm covered for, from when to when, what it costs for the year, and how to set up my account.
- As a policyholder, when my payment goes through, I get a receipt with the amount, the card, the date and what I still owe, so I don't need to log in to check.
- As a policyholder, when money comes back to me, I get a receipt saying how much, to which card, why (a corrected date or a cancellation), and when to expect it.
- As a broker or reviewer, I can see in the Resend dashboard that each email was sent exactly once.

## Acceptance Criteria
1. AC1: Spike outcome recorded. The Resend sandbox sender (`onboarding@resend.dev`) delivers to the account owner's inbox with `RESEND_API_KEY` set. The finding, and the "one test inbox" rule, are written in DECISIONS.md (dated). `.env.example` lists `RESEND_API_KEY`, `RESEND_FROM`, `RESEND_TEST_TO` and `PLAINLY_WEB_URL`.
2. AC2: Policy issued email. When a broker issues a policy, one email is sent after the transaction commits.
   - Subject: "Your cover with Plainly starts <Sep 1, 2026>".
   - Body: the owner's name, the business's legal name, the policy number, the cover dates, the price for the year, and the broker's name.
   - Link: a "Set up your account" button to `<web-url>/signup`.
   - If issuing fails and rolls back, nothing is sent.
3. AC3: Payment receipt. When `charge.succeeded` settles a payment, one receipt is sent after commit.
   - Subject: "We got your payment of $X".
   - Body: the amount, "card ending 4242", the date it settled, and "Left to pay: $Y" (or "You're all paid up"), from the ledger balance at the moment it settled.
   - Link: "See your statement" to `<web-url>/me/statement`.
4. AC4: Refund receipt. When a refund settles (`RefundService.settle` marks it SUCCEEDED), one receipt is sent after commit.
   - Subject: "$X is on its way back to you".
   - Body: the amount, "card ending 4242", why in one plain sentence ("because we moved your cover's start date" or "because your cover ended early"), and "Banks usually take 5–10 working days to show it".
   - Link: to the statement.
   - Failed refunds send nothing.
5. AC5: Once per event. `sent_emails` has a unique key (kind, source_id).
   - A retried webhook (same event id), a second event for an already-settled payment or refund, or a second call for the same policy sends nothing more.
   - Resend also receives `Idempotency-Key: <kind>/<source_id>`.
6. AC6: Money first.
   - If Resend fails or times out, the payment, refund or issued policy stays committed. The row is marked FAILED with the error, and a warning is logged.
   - With `RESEND_API_KEY` blank, the app starts, logs that email is off, and records rows as SKIPPED.
7. AC7: Plain words. Every email has an HTML part and a plain-text part, and neither uses unexplained insurance jargon (checked by a template test reusing the `jargon.test.ts` word list idea). Each email shows "Sent to <owner email>" and a footer: "Demo with fictional data, delivered to a test inbox."
8. AC8: Evidence. In the trial run, issuing a policy, paying, and correcting Oct 15 → Oct 8 (which produces a refund or charge) show the matching emails, one each, in the Resend dashboard. `sent_emails` holds the Resend message id for each.

## Technical Approach
- **New module `app.plainly.notify`**, layered as service → repository with no controller (no HTTP surface):
  - **`EmailGateway`** (interface) with `enabled()` and `send(EmailMessage, idempotencyKey) → messageId`.
    - **`ResendEmailGateway`** `@Component` uses Spring `RestClient` to call `POST https://api.resend.com/emails` with a bearer key and the `Idempotency-Key` header. It reads `plainly.email.{api-key,from,test-to}` and turns itself off when the key is blank, as `StripeJavaGateway` does.
    - **`FakeEmailGateway`** (test) records the messages it receives and can be told to throw. It is provided `@Primary` through `EmailTestSupport`, imported by `@IntegrationTest`.
  - **`SentEmailRepository`** (JdbcClient):
    - `claim(kind, sourceId, toAddress, subject)` runs `insert … on conflict do nothing` and returns `Optional<id>`;
    - `markSent(id, messageId)`;
    - `markFailed(id, error)`;
    - `markSkipped(id)`.
  - **`EmailTemplates`** (pure) builds `EmailMessage(subject, html, text)` for each kind from small records (`PolicyIssued`, `PaymentReceipt`, `RefundReceipt`), using Plainly's inline-styled HTML. Money is formatted from cents and dates in the Plainly style.
  - **`NotifyService`** has the public methods `policyIssued(policyId)`, `paymentSettled(paymentId)` and `refundSettled(refundId)`. Each one:
    - must run inside the caller's transaction (`@Transactional(propagation = MANDATORY)`);
    - reads what it needs through other modules' **services**: `PolicyService` (a new read-only `contact(policyId)` returning owner name, email, legal name, policy number, dates and broker), `LedgerQueryService.balance`, and payment and refund lookups on `PaymentService`/`RefundService`, adding small read methods where they are missing;
    - renders the message, then calls `claim`;
    - registers a `TransactionSynchronization.afterCommit` that sends and marks SENT or FAILED, but only if the claim inserted a row. This follows `RefundService.recordAndSendAfterCommit`.

    Rendering inside the transaction captures the balance exactly as it was committed.
- **Flyway `V10__sent_emails.sql`:** `sent_emails(id bigserial, kind text, source_id bigint, to_address text, subject text, status text check in (PENDING,SENT,FAILED,SKIPPED), resend_id text, error text, created_at, sent_at, unique(kind, source_id))`.
- **Hook points**, one line each, in other specs' code:
  - `IssuePolicyService.issue`, after the policy and its transaction are written;
  - `StripeWebhookService.succeeded`, after `payments.markSucceeded`;
  - `RefundService.settle`, only when `markSucceeded` returns true.
- **Config:** `plainly.email.*` and `plainly.web-url: ${PLAINLY_WEB_URL:http://localhost:5173}` in `application.yml`; `plainly.email.api-key` stays blank in tests. Railway gets `RESEND_API_KEY`, `RESEND_FROM`, `RESEND_TEST_TO` and `PLAINLY_WEB_URL`.
- **Lost-send risk:** a crash between commit and send leaves the row PENDING. This is accepted for the demo and noted in DECISIONS.md. There is no retry sweep.

## Out of Scope
- An in-app "we emailed you" marker on the statement or broker history.
- Retrying failed or pending sends (no sweep), and bounce or webhook handling from Resend.
- Emails for corrections or cancellations that settle no money, password reset, sign-up confirmation, and broker notifications.
- A verified sending domain and delivery to real owner addresses.

## Standards Applied
- [backend/layers](../../../../../../standards/backend/layers.md): `NotifyService` holds the rules and the external call; only `SentEmailRepository` runs SQL; cross-module data comes service to service.
- [global/tech-stack](../../../../../../standards/global/tech-stack.md): Spring Boot 4 / Java 21, JdbcClient, Flyway, Postgres 16.
- [global/git-workflow](../../../../../../standards/global/git-workflow.md): branch and commit conventions.

## Changelog
| Date | Author | Type | Change | Ref |
|---|---|---|---|---|
| 2026-11-23 | david-schwartzman | created | Initial shaping | — |
