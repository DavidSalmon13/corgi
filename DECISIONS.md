# Decision log

## 2026-11-22: Track 1, Spring Boot + React

Policy administration has deterministic amounts and needs only Stripe for money. The API is Spring Boot (my strongest stack, for the money logic); the web app is React + TypeScript to match Corgi.

## 2026-11-22: One product, flat pro-rata pricing

General Liability at $1,200/year, with optional product liability at $730/year ($2.00/day, easy to check by hand). Mid-term amounts are annual premium × days ÷ days in the policy period, end date exclusive, rounded once per line.

## 2026-11-22: One timezone

Every policy runs on America/New_York calendar days. A carrier would use the insured's location.

## 2026-11-22: Append-only ledger

A database trigger blocks edits and deletes on ledger rows. Every line links to its policy transaction or payment, and every payment or refund to its Stripe event.

## 2026-11-22: Only Stripe webhooks change payment status

A payment is pending until a signed webhook marks it paid or failed; each event is processed once. A declined card posts a $0.00 line so the attempt stays visible.

## 2026-11-22: Corrections reverse and rebill

A corrected effective date reverses the original charge and rebills from the new date; the original stays visible. If the price goes down, the difference is refunded to the card automatically.

## 2026-11-22: Cancellation is pro-rata, no short-rate penalty

Unused days are credited in full, minus any unpaid premium. I left out the short-rate penalty to keep every amount one pro-rata calculation.

## 2026-11-22: Simplified sign-in

Real passwords and sessions, but no email confirmation or password reset, and a public demo broker login (fictional data only).

## 2026-11-23: Emails go to one test inbox

Resend's sandbox only delivers to the account owner, so every email goes there and names the owner it was for.

## 2026-11-23: Removing coverage is a credit; a wrong start date is a rewrite

Removing product liability credits the unused days and keeps the original charge. A policy's dates are never edited: it's cancelled flat and rewritten as a new, linked policy.

## 2026-11-24: Demo data without fake payments

Seeded customers are created through the real services, but no payments are seeded, since every payment must trace to a real Stripe event.
