# Architecture

## Components

- **`web/`**: React SPA with role-based routes: `/broker/*` for brokers and `/me/*` for policyholders.
- **`api/`**: Spring Boot REST API, split into modules:
  - **policy**: customers, policies, coverages and policy transactions (issue, add coverage, correct, cancel).
  - **pricing**: a pure, side-effect-free pro-rata calculator (annual premium × days covered ÷ days in term).
  - **ledger**: append-only entries. Each one references the policy transaction or payment that caused it.
  - **billing**: Stripe PaymentIntents, refunds and the webhook endpoint, plus a processed-events table keyed on Stripe event id for idempotency.
  - **notify**: Resend emails (policy issued, statement or receipt).
- **PostgreSQL**: the system of record. The schema is managed by Flyway.

## Data Flow

- The SPA calls the API over JSON/REST.
- A broker action creates a **policy transaction**. Pricing computes the amount, and the ledger posts the entries in the same DB transaction.
- Payments: the API creates a PaymentIntent and the SPA confirms it with Stripe.js. **Payment status changes only through Stripe webhooks**, never from the client. Each webhook is checked against the processed-events table before any ledger posting.
- Refunds: cancellation posts a refund entry and calls Stripe Refunds. The `charge.refunded` / `refund.updated` webhooks settle the status.
- The statement and "how we got this" views read the ledger and join to policy transactions, payments and Stripe event ids.

## Environments

- **Local**: Postgres via docker-compose, API via `./gradlew bootRun`, web via `vite dev`. Use the Stripe CLI (`stripe listen`) to forward webhooks.
- **Prod**: Railway project with two services, `web` (static build) and `api`, plus Railway Postgres. Stripe and Resend run on test or sandbox keys only.

## Gotchas

1. **Never UPDATE or DELETE ledger rows.** Correct them by posting a reversal and a rebill.
2. **Money is `long` cents.** Never use `double`. `BigDecimal` is allowed only inside the pricing calculator's single half-up rounding step.
3. **Dates are end-exclusive** `LocalDate`s. Assumed policy timezone: America/New_York.
4. **The Stripe webhook needs the raw request body** for signature verification, so don't let a JSON message converter consume it first.
5. **Demo auth is a role switcher with seeded users.** It is not real security and is documented as simulated.
