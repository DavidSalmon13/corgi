# Product Mission: Plainly

> Customer: [../../customer/overview.md](../../customer/overview.md) · Ecosystem: n/a (single product) — product: policy-admin

## Problem

Small-business owners get commercial insurance bills full of jargon: pro-rata, endorsement, unearned premium. They can't tell what they're covered for, what they owe, or why a number changed. When brokers fix a mistake, they edit records in place, so the history of what was charged, and why it changed, is lost.

## Target Users

- **Brokers**, who issue, change, cancel and correct General Liability policies.
- **Policyholders**, small-business owners with no insurance background, who check their coverage, pay what's due and read their statement.
- *Secondary:* **Corgi reviewers**, who must be able to trace any amount back to its source.

## Solution

- **Every amount can be explained.** A "how we got this" view walks from statement line → ledger entry → policy action or payment → Stripe event, with the arithmetic shown (rate × days ÷ term).
- **Nothing is overwritten.** The ledger is append-only, so a correction shows the original charge, its reversal and the rebill side by side.
- **Plain language comes first.** A coverage timeline and a "what you owe and why" view replace the usual admin grid, and every insurance term is explained where it appears.
