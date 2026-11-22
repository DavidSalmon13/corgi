# Tech Stack

> Differs from the Playbook template default (`playbook/standards/global/tech-stack.md`) on the backend. See DECISIONS.md, 2026-11-22.

## Frontend

- React 18 + TypeScript
- Tailwind CSS v4
- Vite

## Backend

- Spring Boot 3 on Java 21
- Gradle (Kotlin DSL)

## Database

- PostgreSQL
- Flyway migrations

## Other

- Railway for hosting (web, API, Postgres)
- Stripe test mode for payments, refunds and webhooks
- Resend sandbox for transactional email
