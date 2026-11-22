# Tasks — Issue a policy

> Epic: policy-lifecycle
> Spec: 2026-11-22-issue-policy
> Branch: feat/policy-lifecycle/issue-policy

## Scaffolding

- [x] T1 Scaffold `api/`: Spring Boot 4 / Java 21, Gradle Kotlin DSL, Flyway, JdbcClient, Testcontainers; `GET /api/health`
- [x] T2 Add `docker-compose.yml` (Postgres 16) and `.env.example`; README "Run locally" section
- [x] T3 Scaffold `web/`: Vite + React 18 + TS + Tailwind v4 + React Router + TanStack Query; dev proxy to the API
- [x] T3.1 Port Plainly design tokens to `web/src/theme.css` (`@theme`, `.dark` variant, Source Sans 3, tabular nums) and a dark-mode toggle

## Money core

- [x] T4 `pricing.ProRata`: end-exclusive days, one HALF_UP step, `long` cents; tests first (full year, leap term, 1 day, 321 and 328 days for $730)
- [x] T5 Flyway `V1__init.sql`: app_users, customers, policies, coverages, policy_transactions, ledger_entries, display-id sequences; seed broker Sam Reyes
- [x] T6 Flyway `V2__ledger_append_only.sql`: trigger rejecting UPDATE/DELETE on ledger_entries; Testcontainers test proving both are refused (AC7)
- [x] T7 `ledger.LedgerPoster` (insert only) and `LedgerQueries` (lines with running balance, balance per policy); tests first

## Issue a policy (API)

- [x] T8 `policy.IssuePolicyService`: shared `buildIssue`, `preview` (no writes) and `@Transactional issue`; tests: preview equals issued amounts, all rows written atomically, validation errors per field (AC2–AC6)
- [x] T8.1 `POST /api/broker/policies/preview` and `POST /api/broker/policies` with a field-error response shape
- [x] T9 `GET /api/broker/policies` (balance, status, last action, owes-money flag) and `GET /api/broker/policies/{id}` (header, coverages, ledger lines)
- [x] T10 Demo auth: `GET /api/demo/users`, `POST /api/demo/session`, cookie filter, BROKER/POLICYHOLDER guards, owner user created on issue; tests for 403s (AC1)
- [x] T11 `GET /api/me/cover`: policy, covers, amount due, plain-language reason, scoped to the session's customer
- [x] T12 Injectable America/New_York `Clock`, overridable by config

## Screens (rebuild from design/plainly/)

- [x] T13 Shared components: `Money`, `Term`, `StatusPill`, `CoverTimeline`, `LedgerTable`, app shell with broker nav
- [x] T14 SignIn (`SignIn.dc.html`): role switcher, demo notice, role-based redirect and route guards
- [x] T15 B2 New customer and policy (`B2-NewPolicy.dc.html`): form, error summary and per-field errors, debounced live price panel, "Customer will see" sentence, Create policy
- [x] T16 B1 Policies (`B1-Policies.dc.html`): table, summary line, All / Owes money chips, empty state
- [x] T17 B3 Policy workspace (`B3-Workspace.dc.html`): header, status and balance, cover timeline with Today marker, ledger table with references; no action buttons yet
- [x] T18 P1 Home (`P1-Home.dc.html`, 390 px): amount due and reason, protection sentence, cover period and timeline, broker contact; no Pay button yet
- [x] T19 Jargon and fidelity pass: wrap or remove terms (AC11); check dark mode, 390 px and money signs on all five screens (AC12)

## Verification

- [ ] T20 Run and complete the test suite for pricing, ledger, issue and auth (testing skill)
- [ ] T21 Run QA against acceptance criteria AC1–AC12 (qa skill)
