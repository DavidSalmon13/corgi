# Policy Lifecycle — Tech Plan

> Epic: epic.md · Product: ../../mission.md

## Approach

Every broker action is a **policy transaction** (`ISSUE`, `ADD_COVERAGE`, `CORRECT`, `CANCEL`), handled in a single DB transaction. The pure `pricing` module computes the pro-rata amounts (annual premium × days covered ÷ days in term, rounded half-up once per line), and the `ledger` posts append-only entries that reference the transaction. `CORRECT` posts a reversal of the original entry plus a rebill and never edits the original. Each action has a **preview** endpoint that runs the same code path without committing, and that preview drives the broker's confirm screen. `billing` creates Stripe PaymentIntents for the open balance and issues Stripe refunds for credits: a lowered correction pays off any open balance first, then refunds the rest, latest payment first and capped at what each payment collected. Payment and refund status change only on signed webhooks, deduplicated through the processed-events table. The SPA runs the broker workflow under `/broker/*` and the policyholder's amount due and pay flow under `/me/*`. No meeting write-up exists. The sources are `ASSIGNMENT.md` and `DECISIONS.md`.

## Architecture Impact

- **New Flyway schema (first migrations):** `customers`, `policies`, `coverages`, `policy_transactions`, `ledger_entries`, `payments`, `refunds`, `processed_stripe_events` (unique on Stripe event id).
- **Append-only enforcement:** a Postgres trigger on `ledger_entries` raises an error on any `UPDATE` or `DELETE`. It works even when the app connects as the schema owner, which is the simple setup on Railway.
- **api modules built out:** `policy` (customers, policies, coverages, transactions and their previews), `pricing` (the pro-rata calculator), `ledger` (posting, reversal and rebill, balances), `billing` (PaymentIntents, refunds, the webhook endpoint with raw-body signature verification).
- **Not touched:** `notify` (Resend emails belong to the statements epic).
- **web:** broker screens for customer, issue, add-on, correction and cancellation, each with a money-preview confirm step; policyholder amount-due and Stripe.js payment screens.
- **Infra:** Stripe test keys and a webhook signing secret on Railway; `stripe listen` locally.

## Risks & Unknowns

- **Webhook plumbing.** Spring must keep the raw request body for signature verification, and Stripe CLI forwarding has to work locally and on Railway. **Spike:** a PaymentIntent plus signed webhook round trip on Railway, done as the first task of `collect-premium`.
- **Pending payments during a change.** A correction or cancellation can post while a payment is still pending, so balance and refund math must handle money that is in flight.
- **Refund allocation.** A refund may span several payments (latest first, capped per payment), and each piece needs its own Stripe refund and webhook settlement.
- **Rounding drift.** The reversal must exactly mirror the original line, and the rebill is rounded independently, so the net change may differ by a cent from a direct calculation. Tests must pin this.
- **Invalid corrections.** An add-on start moved before the policy start, after cancellation, or past the end date must be rejected with a clear message.

## Candidate Specs

- `issue-policy`: Flyway schema, pricing calculator, append-only ledger with trigger; the broker creates a customer and issues a GL policy; the policyholder sees the amount due.
- `collect-premium`: starts with the webhook spike, then the PaymentIntent and Stripe.js pay flow, the signed idempotent webhook, and pending/paid/failed states.
- `add-coverage`: mid-term $730/yr add-on with a pro-rata preview and the new amount due.
- `correct-effective-date`: reverse and rebill with a preview; either the net extra is due, or a refund is paid automatically, latest payment first.
- `cancel-refund`: pro-rata refund of unearned premium net of any unpaid balance, paid as a Stripe refund and settled by webhook.
