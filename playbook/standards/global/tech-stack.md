# Tech Stack

The stack as built. Change this file when a dependency is added or swapped, and say why in `DECISIONS.md`.

## Frontend (`web/`)

- React 18 with TypeScript, built with Vite
- Tailwind CSS v4 (through `@tailwindcss/vite`), with design tokens in `src/theme.css`
- React Router 7 for pages, TanStack Query 5 for server data
- Stripe Elements (`@stripe/react-stripe-js`) for the card form; card numbers never touch our servers
- Vitest for tests (`npm test`); `npm run build` type-checks and builds
- In development `/api` is proxied to the API on `:8080`

## Backend (`api/`)

- Java 21 with Spring Boot 4 (Spring MVC), built with Gradle (Kotlin DSL)
- SQL through Spring's `JdbcClient`: plain SQL, no JPA or ORM
- One package per area: `auth`, `policy`, `pricing`, `ledger`, `billing`, `me`, `common`
- Sign-in: server-side sessions with BCrypt hashes (`spring-security-crypto` only, not full Spring Security)
- Payments: `stripe-java`, test mode only; webhooks verified by signature and processed once per event id
- Tests: JUnit 5 with Testcontainers Postgres (`./gradlew test`, Docker must be running)

## Database

- PostgreSQL 16 (`docker compose up -d` locally)
- Flyway migrations in `api/src/main/resources/db/migration` (`V<n>__<what>.sql`), run on API start
- The ledger is append-only, enforced in the database; corrections are reversing lines

## Money

- Integer cents in USD everywhere (Java `long`, TypeScript `number`); format only at the edge of the UI

## Hosting

- Railway; deployment settings are in the README
