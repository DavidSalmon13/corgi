# Statement & History — Tech Plan

> Epic: epic.md · Product: ../../mission.md

## Approach

The money side is read-only: no new ledger rules. A statement endpoint for each role (`/me` and `/broker`) builds from `LedgerQueryService.lines()`, which already carries each line's calculation inputs (annual cents × days ÷ term days), cover dates, reversal links, policy transaction id, Stripe PaymentIntent, refund and event ids, and the running balance. A "how we got this" endpoint per entry returns the explanation chain: entry → policy transaction or payment/refund → Stripe event. A timeline endpoint derives cover periods from coverages and transactions. For emails, the `notify` module calls Resend's sandbox API from the services that issue policies and settle webhooks. Each send is recorded in a `sent_emails` table keyed by (kind, source id), so retries never send twice, and the send happens after commit, so a failed email never rolls back money. The web app rebuilds P3 Statement, P2 Timeline, the P4 drawer and B5 History from `design/plainly/`. No meeting write-up exists; the sources are `ASSIGNMENT.md`, `DECISIONS.md`, the `policy-lifecycle` tech plan (which assigns Resend emails to this epic) and `design/plainly/README.md`.

## Architecture Impact

- **ledger**: read-only query additions for the statement and trace views. No schema change to `ledger_entries`.
- **policy / me**: statement, history and timeline endpoints per role, layered controller → service → repository (`playbook/standards/backend/layers.md`).
- **notify**: built for the first time. A Resend client behind an interface (a fake in tests), email templates, and a `sent_emails` table (Flyway) for idempotency.
- **billing / policy services**: trigger an email after commit on issue, payment settled and refund settled.
- **web**: P2 Timeline, P3 Statement, P4 "How we got this" drawer, B5 History.
- **Infra**: `RESEND_API_KEY` and a sandbox sender on Railway. No new services.

## Risks & Unknowns

- **Spike: Resend sandbox delivery.** Without a verified domain, Resend only delivers to the account owner's address, so fictional customers can't receive mail. Confirm the sandbox sender and route all demo mail to one test inbox, documented as simulated. This is the first task of `emails`.
- **Plain-language explanations** for reversals, and for refunds split across payments, are the hardest copy and need a careful "why" per entry type.
- **Money in flight**: the statement must show pending payments and refunds clearly without counting them as settled.
- **One calculation for both roles**: the broker and policyholder views must share one service so they never disagree.
- **Email after commit can be lost** if the process dies between commit and send. Accepted for the demo; to be noted in `DECISIONS.md`.
- **Webhook retries must not resend receipts**: enforced by the unique key on `sent_emails`.

## Candidate Specs

- `statement` — policyholder P3 Statement with running balance and the P4 "how we got this" drawer; the shared statement and trace service both roles use.
- `broker-history` — B5 History: every transaction, entry, payment and refund with IDs and Stripe references, opening the same trace.
- `cover-timeline` — P2 Timeline: base cover, extra cover, corrections and cancellation shown as periods.
- `emails` — starts with the Resend sandbox spike; the notify module, the issue email and payment/refund receipts, sent idempotently.
