# References for Emails

## Related Specs

_None._ The cross-epic pre-flight check found no spec claiming `notify/**`, `V10__sent_emails.sql` or email templates. The hook lines go into code owned by other specs:
- `IssuePolicyService` belongs to `policy-lifecycle/2026-11-22-issue-policy`.
- `StripeWebhookService` belongs to `policy-lifecycle/2026-11-22-collect-premium`.
- `RefundService` belongs to `policy-lifecycle/2026-11-22-correct-effective-date`.

Those specs keep ownership; this spec adds one call in each.

## Upstream Meeting

None. The sources are `ASSIGNMENT.md`, `DECISIONS.md`, this epic's tech plan and `design/plainly/README.md`.

## Similar Implementations

### Stripe gateway
- **Location:** `api/src/main/java/app/plainly/billing/StripeGateway.java`, `StripeJavaGateway.java`, and `api/src/test/java/app/plainly/StripeTestSupport.java`
- **Relevance:** the outside service sits behind an interface.
- **Key patterns:**
  - The real `@Component` reads `plainly.stripe.*` and turns itself off (`enabled() == false`) when a key is blank.
  - Tests provide a `@Primary` fake through a test config imported by `@IntegrationTest`.

### Webhook idempotency
- **Location:** `billing/ProcessedEventRepository.recordIfNew`, `V6__payments.sql`
- **Relevance:** the "once per event" rule.
- **Key patterns:** `insert … on conflict do nothing`, which returns whether a row was inserted.

### After-commit side effect
- **Location:** `billing/RefundService.recordAndSendAfterCommit` (around lines 115–133)
- **Relevance:** sending to an outside service only after money commits.
- **Key patterns:** `TransactionSynchronizationManager.registerSynchronization(... afterCommit ...)`.

### Hook points
- `policy/IssuePolicyService.issue`: after the policy is written.
- `billing/StripeWebhookService.succeeded`: after `payments.markSucceeded`.
- `billing/RefundService.settle`: when `refunds.markSucceeded` returns true.

### Tests
- `@IntegrationTest` runs Testcontainers Postgres 16 with one shared context, with `plainly.today=2026-11-22`.
- Test data comes from `TestData` and `PaymentTestData`.
- Webhook signatures are checked for real with a test secret.
