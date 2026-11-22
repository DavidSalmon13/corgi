# Your statement and "How we got this"

> Epic: ../../epic.md
> Status: done
> Branch: feat/statement-history/statement
> Covers: /me/statement, /api/me/statement, api/src/main/java/**/statement/**, web/src/pages/me/StatementPage.tsx, web/src/components/HowWeGotThis.tsx, web/src/lib/statementCopy.ts

## Overview

The policyholder gets a statement of every charge, credit, payment and refund on their policy, oldest first with a running balance, and can tap any amount to see "how we got this": the plain reason, the maths, the change or payment that caused it, and the references (ledger line, policy change, Stripe payment, refund and event). A correction shows as one card holding the original line, its full undo, the corrected line and the broker's reason, so the Oct 15 → Oct 8 (+$14.00) scenario reads at a glance. It is the first spec of statement-history and serves the brief's criteria: the running balance equals the ledger balance to the cent; every amount opens a trace; the correction scenario shows original, reversal and rebill; the screens match design/plainly in light and dark at 390 px and desktop with no unexplained jargon. The statement is built by a role-neutral service so `broker-history` reuses it. Designs: `design/plainly/Main.dc.html` (P3), `P4-HowWeGotThis.dc.html` (P4), entry from `P1-Home.dc.html`.

## Goals & User Stories

- As a policyholder, I see every charge, payment and refund with a running balance, so I know what I owe and why.
- As a policyholder, I tap any amount and see how we got it in plain words: days × daily price, and which change or payment caused it.
- As a policyholder, when a date was corrected I see the original charge, its undo and the new charge, with my broker's reason, not a silently changed number.
- As a policyholder, I see money still on its way (a payment waiting for my bank, a refund being sent) without it being counted as settled.

## Acceptance Criteria

1. AC1: A signed-in policyholder reaches the statement at `/me/statement` from P1 Home and back. A broker gets 403 from `/api/me/statement`; a policyholder only ever sees their own policy.
2. AC2: Every ledger line is listed oldest first with date, plain title, amount (signed) and running balance. The last balance equals the ledger balance (`LedgerQueryService.balance`) to the cent, on every policy.
3. AC3: The banner states the one thing that matters now, in the P3 states: what you owe and why (reusing the P1 due reasons), "Waiting for your bank" when a payment is pending, "We owe you $X, on its way to card ending NNNN" when a refund is unsettled, or all paid up.
4. AC4: A pending payment and an unsettled refund appear as rows marked pending ("Waiting for your bank", "Refund on its way") that do not change the running balance. A declined attempt shows as a $0.00 line "Didn't go through · no money moved".
5. AC5: Each CORRECT change shows as one correction card: the change's old → new date, the day difference, the net change (and the per-day price when it is a whole number of cents), the broker's reason as written with their name; expanding it shows the original line (struck, "undone <date>"), the reversal and the corrected line with their maths.
6. AC6: The trial scenario (Oct 15 → Oct 8 on a $730/yr extra cover) shows +$642.00, −$642.00, +$656.00 and a net +$14.00 ("7 more days × $2.00"); the mirror case (Oct 15 → Oct 22) shows a net −$14.00 and the refund row.
7. AC7: Every amount is tappable and opens the P4 drawer for its kind (charge, credit, reversal, payment, refund) with: line and amount; a plain sentence; the maths (`$730.00 a year × 328 days ÷ 365 = $656.00` with the day range, end not counted; a reversal shows the undone line's numbers with the sign flipped; a payment or refund shows balance before → after); what caused it (change type, effective dates, who, when, and the broker's reason for a correction); the payment or refund (card, date, status); references (Change PTX-…, Line LE-…, Undoes LE-…, Stripe payment pi_…, Refund RF-… / re_…, Stripe event evt_…).
8. AC8: The drawer works by keyboard and screen reader (focus moves in, Esc and a close button return focus to the amount), and on phones it is a bottom sheet with no horizontal scroll.
9. AC9: When the broker changes a date again (the debrief), reloading the statement shows the new reversal and corrected line and the drawer explains the new amount; nothing earlier changes.
10. AC10: Wording passes the jargon test (`web/src/jargon.test.ts`): no unexplained "ledger", "pro-rata", "premium", "endorsement"; insurance or accounting terms used are wrapped in `Term` with a plain explanation. Screens match P3/P4 in light and dark at 390 px and 1280 px.

## Technical Approach

- **API (new `app.plainly.statement` module, controller → service → repository):** `StatementService.forPolicy(policyId)` builds a role-neutral `Statement` from `PolicyService` detail (ledger lines with calc inputs, cover dates, reversal links, PTX ids, Stripe ids, balances; transactions; refunds) and `PaymentService` (pending payment). It returns: header (business, policy number, balance, banner state + due reason), `rows` (ledger lines plus pending, non-balance rows), `corrections` (per CORRECT transaction: original, reversal, rebill line ids, old/new date, day delta, net, reason, actor) and a `trace` per row (kind, maths inputs, cause: transaction type/effective date/recorded on/actor name/reason; payment or refund: card, date, status; refs). `MyStatementController` maps `GET /api/me/statement` for the signed-in policyholder via `PolicyService.latestForCustomer`. The actor name per transaction comes from `policy_transactions.created_by` via a new `PolicyTransactionRepository` read exposed through `PolicyService`. No schema change, no new ledger rules; read-only, no `@Transactional` writes.
- **Traces are embedded in the statement response** rather than a separate per-entry endpoint (a deviation from the tech plan): one request, the drawer opens instantly, and the numbers can't disagree between the list and the drawer. `broker-history` adds a broker controller over the same service.
- **Due reason:** move the `DueReason` decision out of `MyCoverService` into the statement service (or a shared helper) so P1 and P3 say the same thing; `MyCoverService` keeps its response shape.
- **Web:** `StatementPage` at `/me/statement` (RequireRole POLICYHOLDER), linked from P1 Home and back; `HowWeGotThis` drawer component (dialog on desktop, bottom sheet on phones); `lib/statementCopy.ts` turns codes and numbers into sentences (pure, unit-tested), reusing `formatCents`/`formatDate` and the calc text conventions in `ledgerRows.ts`. Types in `web/src/api/types.ts`.

## Out of Scope

- The broker's view of the drawer and B5 History (`broker-history`).
- The P2 cover timeline (`cover-timeline`).
- Emails and PDF statements.
- Filtering, search or export of statement lines.

## Standards Applied

- [backend/layers](../../../../../../standards/backend/layers.md) — the new statement module is controller → service → repository; the service calls other modules' services only.
- [global/tech-stack](../../../../../../standards/global/tech-stack.md) — React 18 + Vite + Tailwind v4; Spring Boot 4 / Java 21 with JdbcClient.
- [global/git-workflow](../../../../../../standards/global/git-workflow.md) — branch and commit conventions.

## Changelog

| Date | Author | Type | Change | Ref |
|---|---|---|---|---|
| 2026-11-22 | david-schwartzman | created | Initial shaping | — |
| 2026-11-23 | david-schwartzman | change | Status → done: PRs #51–#55 merged into dev | #55 |
