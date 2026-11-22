# Decision log

Short, dated entries: what was decided, why, and any assumption made where the brief was silent.

## 2026-11-22: Chose Track 1 (Policy administration)

Of the three tracks, policy administration has the fewest external moving parts (Stripe test mode covers payments and refunds), its amounts are deterministic (dates × rates), and it is Corgi's own domain. Neobank was a close second; its main risk was card-issuing sandbox access. The investment app was ruled out because of its valuation and return-restatement complexity.

## 2026-11-22: Frontend is a first-class deliverable

The recruiter said the reviewers care strongly about a clean, easy-to-understand interface that shows creativity, especially in insurance. UI work gets real time in the plan, not just whatever is left at the end.

## 2026-11-22: Initial assumptions (to confirm during planning)

- **Product:** one product, General Liability. Fictional pricing: base $1,200/year; one add-on at $730/year ($2.00/day).
- **Pro-rata:** annual premium × days covered ÷ days in term. Dates are end-exclusive, and each ledger line is rounded half-up to the cent once.
- **Cancellation:** pro-rata refund of the unearned premium with no penalty. Short-rate is a documented omission. Unpaid balances are netted off the refund.
- **Corrections:** reverse the original charge and rebill with the corrected effective date. The original stays visible.
- **Integrations:** Stripe (test mode) for payments, refunds and webhooks; Resend (sandbox) for policy and statement emails.
- **Stack:** React + TypeScript web, Spring Boot 3 / Java 21, PostgreSQL, deployed on Railway.
