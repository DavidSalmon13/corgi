# Corgi — Tech Stack

> The brief doesn't require a stack. Corgi mainly uses Express and Next.js, and the frontend matches that. The backend is Spring Boot by choice, which also departs from the Playbook template default (`playbook/standards/global/tech-stack.md`). See DECISIONS.md, 2026-11-22.

## Frontend

- React 18 + TypeScript
- Tailwind CSS v4
- Vite

## Backend

- Spring Boot 3 on Java 21

## Data

- PostgreSQL. Money is stored as integer cents (USD), and the ledger is append-only.

## Infra & Services

- Railway for hosting (web, API, Postgres)
- Stripe test mode for payments, refunds and webhooks (live sandbox)
- Resend sandbox for policy and statement emails (live sandbox)

## Conventions

- Money is integer cents in USD. Each ledger line is rounded half-up to the cent once.
- The ledger is append-only: a correction reverses the original entry and rebills it, and never edits it.
- Webhooks are idempotent by Stripe event id.
- Every amount traces from ledger entry → policy transaction or payment → Stripe event, and the UI shows this ("how we got this").
- Fictional customer data and test credentials only.
