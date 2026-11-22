# Decision log

Short, dated entries: what was decided, why, and any assumption made where the brief was silent.

## 2026-11-22: Chose Track 1 (Policy administration)

Of the three tracks, policy administration has the fewest external moving parts (Stripe test mode covers payments and refunds), its amounts are deterministic (dates × rates), and it is Corgi's own domain. Neobank was a close second; its main risk was card-issuing sandbox access. The investment app was ruled out because of its valuation and return-restatement complexity.

## 2026-11-22: Frontend is a first-class deliverable

The recruiter said the reviewers care strongly about a clean, easy-to-understand interface that shows creativity, especially in insurance. UI work gets real time in the plan, not just whatever is left at the end.

## 2026-11-22: Initial assumptions (confirmed 2026-11-22, see "Pricing rules confirmed" below)

- **Product:** one product, General Liability. Fictional pricing: base $1,200/year; one add-on at $730/year ($2.00/day).
- **Pro-rata:** annual premium × days covered ÷ days in term. Dates are end-exclusive, and each ledger line is rounded half-up to the cent once.
- **Cancellation:** pro-rata refund of the unearned premium with no penalty. Short-rate is a documented omission. Unpaid balances are netted off the refund.
- **Corrections:** reverse the original charge and rebill with the corrected effective date. The original stays visible.
- **Integrations:** Stripe (test mode) for payments, refunds and webhooks; Resend (sandbox) for policy and statement emails.
- **Stack:** React + TypeScript web, Spring Boot 3 / Java 21, PostgreSQL, deployed on Railway.

## 2026-11-22: Backend on Spring Boot, frontend on Corgi's stack

The brief doesn't require a stack. Corgi mainly uses Express and Next.js. I chose Spring Boot 3 / Java 21 for the backend anyway because it's my strongest stack. The heaviest-scored risk sits in the money logic: append-only ledger, reverse-and-rebill corrections and idempotent webhooks. Transactions, Flyway migrations and unique constraints are dependable tools for that, and a time-boxed trial is a poor place to learn a new stack. The frontend, which is graded heavily, uses React + TypeScript + Tailwind to match Corgi. With more time I'd port the API to TypeScript to share money and DTO types end to end.

## 2026-11-22: Policy dates use one timezone, America/New_York

The brief doesn't say which timezone a policy date belongs to, but it decides the day coverage starts and ends, so it changes every pro-rata amount. I assume every policy runs on America/New_York calendar days and store dates as plain dates (no time), end-exclusive. A real carrier would use the insured's location; per-policy timezones are a documented omission.

## 2026-11-22: Demo sign-in is simulated

Real accounts aren't part of the workflow being graded, so sign-in is a role switcher with seeded, fictional Broker and Policyholder users. This is not real security, and the README lists it under "simulated". Real authentication is in Phase 2.

## 2026-11-22: Only Stripe webhooks change payment status

The app never marks a payment as succeeded or failed because the browser said so. The status starts as pending and changes only when a signed Stripe webhook arrives, and each event id is processed once. As a result, the pending, successful and failed states the brief asks for always match Stripe, and each one traces to a Stripe event.

## 2026-11-22: Policyholder persona is a small-business owner

The brief says "customer" and "policyholder" but doesn't say who they are. Because this is commercial insurance, the insured party is a business, not a person. The customer record is the business (legal name, address), and its owner is the contact person who signs in, pays and gets emails. All policyholder-facing copy is written for an owner with no insurance background. Multiple locations, users or policies per business are left out.

## 2026-11-22: Pricing rules confirmed, and which are simplified

I kept the initial assumptions. The rules that follow real practice: charging by the day for mid-term changes; counting days the way a 12:01 a.m. start-to-12:01 a.m. end policy does (Sep 1 → Sep 1 = 365 days); taking any unpaid amount off a refund; and correcting by undoing the original line and recharging. The simplifications: prices are made up and flat (a real price depends on business type, revenue and location; $730 for the extra cover was picked because it is exactly $2.00 a day, so a reviewer can check it by hand); and every cancellation refunds unused days in full with no fee. In real commercial insurance, a cancellation the customer asks for often keeps a penalty (short-rate) or a minimum premium. Rounding each line once is a billing convention I chose so that lines always add up to the total shown.

## 2026-11-22: A correction that lowers the price is refunded automatically

When a correction reduces what the customer owes (for example, the extra cover's start moves later), the difference first pays off any open balance, and the rest is refunded to the customer's card through Stripe straight away. Refunds start with the most recent payment, and no single refund exceeds what that payment collected. The broker's confirm screen previews the refund, so confirming the correction counts as approving it; there's no separate approval step. I chose this over keeping a credit balance because it matches how insurers usually return money they owe, the customer isn't left with money sitting idle, and the refund traces to a Stripe event like every other amount. Refunds that fail are shown to the broker to retry.

## 2026-11-22: The app is called Plainly

The app has its own name and look so it doesn't copy Corgi's real branding. "Plainly" says what the product promises: insurance explained in plain language. A warm amber accent is the only nod to a corgi. The internal product slug stays `policy-admin`.
