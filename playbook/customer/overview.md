# Corgi — Overview

## Business

Corgi is a commercial insurance company. This engagement is a work trial: building a policy administration platform (Track 1). Full brief: [`ASSIGNMENT.md`](../../ASSIGNMENT.md).

## Goals & Engagement

Deliver a deployed platform for managing commercial insurance policies (one product: General Liability) with two roles:

- **Broker:** issues a policy, changes coverage mid-term, cancels it, and corrects mistakes.
- **Policyholder:** views coverage, pays amounts due, and reads a statement that explains every charge, payment and refund in plain language.

Core workflow: issue a policy → collect premium via a sandbox (pending / succeeded / failed) → add coverage mid-term with pro-rata premium → cancel with a documented refund rule → policy history and financial statement for both roles.

Test scenario: coverage paid, then three weeks later the broker finds the add-on started a week earlier than recorded. Correct the effective date and show the revised amount due, with both the original charge and the correction visible.

Constraints: at least two live sandbox integrations (Stripe test mode, Resend sandbox), USD, fictional data, test credentials, no spend. Every amount must trace from ledger entry → policy transaction or payment → Stripe event. Document what is live and what is simulated.

Out of scope unless time allows: claims, renewals, broker commissions.

## Success Measures

Reviewer scoring:

| Area | Points |
|---|---|
| Understanding of the financial problem | 30 |
| Working app | 25 |
| Sandbox integrations | 20 |
| Live debrief | 15 |
| Engineering judgment | 10 |

Reviewers have said the frontend matters a lot: it should be clean, explain itself and show product thinking.

Submission (all five items required):

1. A link to the deployed app.
2. Demo credentials or instructions for both roles.
3. The repo link, with the chosen track and decision log.
4. A video of up to 5 minutes showing the transaction.
5. Evidence from the sandbox integrations (event logs or screenshots).

## Key Dates

- 2026-11-22: Track chosen, project started.
- Submission deadline: TBD (shown on the trial platform).
- Live debrief: TBD.
