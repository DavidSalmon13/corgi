# Issue a policy

> Epic: ../../epic.md
> Status: shaped
> Branch: feat/policy-lifecycle/issue-policy
> Covers: /signin, /broker/policies, /broker/policies/new, /broker/policies/:id, /me, api/src/main/java/**/policy/**, api/src/main/java/**/pricing/**, api/src/main/java/**/ledger/**, api/src/main/java/**/auth/**, api/src/main/resources/db/migration/**, web/**, docker-compose.yml

## Overview

The first slice of the policy lifecycle, and the one that stands up the stack. A broker creates a customer (the business and its owner) and issues a one-year General Liability policy, optionally with the extra product-liability cover. The price is previewed live before anything is saved. Issuing posts one policy transaction (ISSUE) and one append-only ledger line per cover, and each line shows its arithmetic (annual × days ÷ days in term). The broker sees the policy in the list (B1) and in its workspace (B3: cover timeline and ledger). The owner signs in through the demo role switcher and sees what they're covered for and what they owe (P1). Paying is the next spec (`collect-premium`). This spec builds the schema, the pricing calculator and the ledger that every later spec posts to.

Serves the epic's success criteria: pro-rata amounts match hand arithmetic and have unit tests; no ledger row is ever updated or deleted, enforced by the DB; the price is previewed before the broker commits. Serves the mission: plain language first; every amount can be explained.

UI reference: `design/plainly/` (SignIn, B1-Policies, B2-NewPolicy, B3-Workspace, P1-Home). The files are design-canvas components: rebuild them in React, don't reuse their markup.

## Goals & User Stories

- As a **broker**, I can create a customer (legal business name, what the business does, owner's name, owner's email) and issue a GL policy from a start date, so the business is covered for a year with a clear first bill.
- As a **broker**, I see the price, with its calculation, update as I fill the form, before anything is saved, so I never issue a surprise.
- As a **broker**, I can choose the extra product-liability cover when issuing, so a business that sells goods is covered from day one.
- As a **broker**, I can see all my policies with their status and balance, and filter to the ones that owe money.
- As a **broker**, I can open a policy and see its cover timeline and every ledger line, with the calculation and the record each one came from.
- As a **policyholder**, I can sign in and see, in plain words, what I'm covered for, for how long, and how much I owe.

## Acceptance Criteria

1. AC1: Sign-in (`/signin`) offers "Continue as Sam, the broker" and one entry per customer owner, and says this is a demo with fictional people and Stripe test mode. A broker lands on `/broker/policies`, a policyholder on `/me`. Broker routes refuse a policyholder session, and the reverse (403 from the API, redirect in the SPA).
2. AC2: B2 (`/broker/policies/new`) collects legal business name, what the business does, owner's name and owner's email. All are required, and the email must be valid. On submit with errors, a summary ("N things to fix before you can save") and a message under each field appear, matching B2's "field errors" state. Nothing is saved.
3. AC3: The cover period is fixed at one year from the chosen start date, shown as "Sep 1, 2026 → Sep 1, 2027" with the hint "One year. The end date isn't counted." Days are end-exclusive, America/New_York calendar days (Sep 1 2026 → Sep 1 2027 = 365 days; a term spanning Feb 29 has 366).
4. AC4: Base cover ($1,200.00 a year) is always included. Extra cover: product liability ($730.00 a year) is an optional checkbox. Each cover has a one-line plain-language description, as in B2.
5. AC5: The price panel updates as the broker types, from a server preview that saves nothing. It shows one line per cover with its calculation (e.g. "Base cover, full year · $1,200.00 × 365 ÷ 365 · +$1,200.00"), the total "Customer pays", and the sentence "Customer will see: You're covered from … to …. The price is … for the year." With no start date it shows "Add a start date and we'll show the price here, before anything is saved."
6. AC6: "Create policy" creates the customer, the policy (number `GL-<start year>-<4-digit seq>`, status Active), its covers, one ISSUE policy transaction (`PTX-0001`…) and one ledger charge per cover (`LE-0001`…), all in one DB transaction. Each charge stores the annual premium, days covered and days in term, and its amount is the half-up-rounded result, in integer cents. The issued amounts equal the preview exactly.
7. AC7: The database rejects any UPDATE or DELETE on ledger entries (a trigger raises an error). An integration test proves it.
8. AC8: B1 (`/broker/policies`) lists policies with customer, owner, policy number, status pill, balance (owed amounts in the accent colour, "$1,200.00 due") and last action ("Issued · Sep 1"), plus the summary line ("N policies · N owe money"). Filter chips: All and Owes money. With no policies it shows the empty state ("No policies yet … Add your first customer").
9. AC9: B3 (`/broker/policies/:id`) shows the business, policy number, owner, cover period, status ("Active · balance due") and balance. It also shows the cover timeline (a month axis, one bar per cover, a "Today" marker) and the ledger table (Date, Line, Calculation, Amount, running Balance, References: LE id and PTX id), oldest first, under "Lines are never edited; corrections add an undo and a new line." The Add cover / Correct / Cancel actions aren't shown until their specs ship.
10. AC10: P1 (`/me`, mobile-first at 390 px) shows the business name, "$X due" and why ("Your first year of cover"), what they're protected for (built from what the business does and whether extra cover is included), the cover period with a timeline bar per cover, and the broker contact. The Pay button is wired in `collect-premium`; until then, P1 shows the amount due without a pay action.
11. AC11: Insurance terms on these screens (e.g. "pro-rata", "premium", "term") are wrapped in a dotted-underline term with a plain-language explanation, or avoided. No unexplained jargon is left.
12. AC12: All screens use the Plainly tokens from the designs (cream/amber palette, Source Sans 3, tabular numbers), support dark mode, and lay out without horizontal scroll at 390 px. Money shows as `$1,200.00`, with `+$` for charges and `−$` (a true minus sign) for credits.

## Technical Approach

**Stack (per product tech-stack.md):** `api/`: Spring Boot 3, Java 21, Gradle Kotlin DSL, Flyway, JdbcClient (no JPA: an append-only ledger fits explicit SQL). `web/`: Vite, React 18, TypeScript, Tailwind v4, React Router, TanStack Query. Local Postgres via `docker-compose.yml`. Integration tests use Testcontainers Postgres.

**Schema (Flyway `V1__init.sql`):** `app_users` (id, role BROKER|POLICYHOLDER, display_name, customer_id null), `customers` (legal_name, business_description, owner_name, owner_email), `policies` (policy_number unique, customer_id, start_date, end_date, status, created_at), `coverages` (policy_id, kind BASE|PRODUCT_LIABILITY, annual_premium_cents, effective_from, effective_to), `policy_transactions` (display_id `PTX-%04d` from a sequence, policy_id, type ISSUE|…, effective_date, created_at, created_by), `ledger_entries` (display_id `LE-%04d`, policy_id, policy_transaction_id null, entry_type CHARGE|…, coverage_id null, amount_cents bigint signed, description, calc_annual_cents, calc_days, calc_term_days, reverses_entry_id null, posted_at). `V2__ledger_append_only.sql` adds a `BEFORE UPDATE OR DELETE` trigger that raises. Payment, refund and processed-event tables are left to `collect-premium` (a later migration), so this spec owns only what it uses.

**Modules:**
- `pricing`: a pure `ProRata.charge(annualCents, from, to, termStart, termEnd)` that returns `{days, termDays, amountCents}`. It uses `BigDecimal` with a single `HALF_UP` step and returns `long` cents. It is exhaustively unit-tested, including the leap term and 1-day and full-term spans.
- `policy`: `IssuePolicyService.preview(cmd)` and `issue(cmd)` share one `buildIssue(cmd)` that returns the planned transaction and entries. `issue` persists them in one `@Transactional` block. Endpoints: `POST /api/broker/policies/preview`, `POST /api/broker/policies`, `GET /api/broker/policies` (list with balance and last action), `GET /api/broker/policies/{id}` (header + coverages + ledger with running balance).
- `ledger`: `LedgerPoster.post(entries)` (insert only) and `LedgerQueries.balance(policyId)` / `.lines(policyId)`. There is no update or delete method.
- `auth` (demo, simulated): `GET /api/demo/users`, `POST /api/demo/session {userId}` sets an HTTP-only cookie with the user id, and a filter resolves the role. `/api/broker/**` needs BROKER; `/api/me/**` needs POLICYHOLDER and is scoped to that user's customer. A Flyway seed adds Sam Reyes (broker). Issuing a policy creates the owner's POLICYHOLDER user.
- `me`: `GET /api/me/cover` returns policy, coverages, amount due and a plain-language reason.
- Clock: an injectable `Clock` fixed to America/New_York drives "today" (the timeline marker, validation). It can be overridden by config for demos and tests.

**Web:** `web/src/theme.css` ports the design tokens into Tailwind v4 `@theme`, with a `.dark` variant and a toggle. Shared components: `Money` (formats cents and signs), `Term` (abbr with explanation), `StatusPill`, `CoverTimeline` (used by B3 and P1), `LedgerTable`. Pages: `SignIn`, `broker/PoliciesPage` (B1), `broker/NewPolicyPage` (B2, debounced preview query), `broker/PolicyWorkspace` (B3), `me/HomePage` (P1). Copy is taken from the design files.

## Out of Scope

- Paying (P5, PaymentIntents, webhooks, pending/paid/failed states, the B1 "Payment failed" filter): `collect-premium`.
- Mid-term add-on, corrections, cancellation, the B3 action buttons and B4: later specs in this epic.
- Statement (P3), "How we got this" drawer (P4), B5 History, the P2 cover timeline page: statements epic.
- Resend emails, including the "invite" on B2.
- Editing or deleting customers or policies; multiple policies per customer; covers other than base and product liability; real authentication.

## Standards Applied

- [global/git-workflow](../../../../../../standards/global/git-workflow.md): branch `feat/policy-lifecycle/issue-policy`, `feat(policy-lifecycle): …` commits, PR via the pr skill.
- [global/tech-stack](../../../../../../standards/global/tech-stack.md): frontend applies as written. The backend is overridden by the product `tech-stack.md` (Spring Boot 3 / Java 21; see DECISIONS.md 2026-11-22).

## Changelog

| Date | Author | Type | Change | Ref |
|---|---|---|---|---|
| 2026-11-22 | david-schwartzman | created | Initial shaping | — |
