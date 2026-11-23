# Tasks — Emails: policy issued, payment and refund receipts

> Epic: statement-history
> Spec: 2026-11-23-emails
> Branch: feat/statement-history/emails
> PR: https://github.com/DavidSalmon13/corgi/pull/67
> Reviewed: 2026-11-23 APPROVE
> SecurityReviewed: 2026-11-23 APPROVE

## Spike
- [x] T1 Resend sandbox spike: send one email from `onboarding@resend.dev` to the account inbox with curl, confirm the `Idempotency-Key` behaviour, record the findings and the test-inbox rule in DECISIONS.md, and add the env vars to `.env.example` (AC1)

## Notify module (api)
- [x] T2 Flyway `V10__sent_emails.sql` (unique kind + source_id)
- [x] T3 `SentEmailRepository` (repository): claim / markSent / markFailed / markSkipped
- [x] T4 `EmailGateway` + `ResendEmailGateway` (RestClient, switches off when the key is blank) + `plainly.email.*` / `plainly.web-url` config; `FakeEmailGateway` + `EmailTestSupport` wired into `@IntegrationTest`
- [x] T5 `EmailTemplates` (pure): issued, payment and refund messages, HTML + text, Plainly styling and plain words (AC2–AC4, AC7)
- [x] T6 `NotifyService` (service): claim in the caller's transaction, send after commit, mark SENT/FAILED/SKIPPED (AC5, AC6)
- [x] T6.1 Read-only service methods it needs: `PolicyService.contact(policyId)`, plus a payment and refund lookup on the billing services (service + repository read)

## Hooks (service layer)
- [x] T7 Call `NotifyService.policyIssued` from `IssuePolicyService.issue` (AC2)
- [x] T8 Call `NotifyService.paymentSettled` from `StripeWebhookService.succeeded` (AC3)
- [x] T9 Call `NotifyService.refundSettled` from `RefundService.settle` only on SUCCEEDED (AC4)

## Docs
- [x] T10 DECISIONS.md: email after commit can be lost (accepted); simulated delivery to a test inbox; Railway env vars

## Verification
- [x] T11 TDD tests (testing skill):
  - `EmailTemplatesTest`: the copy for each kind and the jargon check.
  - `NotifyServiceIT`:
    - a rollback sends nothing;
    - a Resend failure keeps the money and records FAILED;
    - with the gateway off, rows are SKIPPED;
    - a duplicate claim sends nothing.
  - `StripeWebhookServiceIT`: a retried `charge.succeeded` sends one receipt.
  - `RefundServiceIT`: a refund that settles sends one receipt; a failed refund sends none.
  - `IssuePolicyServiceIT`: issuing sends one email.
- [x] T12 Run QA against acceptance criteria (qa skill), including a live sandbox run for AC8
