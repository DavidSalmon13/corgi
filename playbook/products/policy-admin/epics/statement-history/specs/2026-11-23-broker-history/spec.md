# Policy history for the broker

> Epic: ../../epic.md
> Status: shaped
> Branch: feat/statement-history/broker-history
> Covers: /broker/policies/:id/history, /api/broker/policies/{id}/history, web/src/pages/broker/HistoryPage.tsx, web/src/lib/historyCopy.ts

## Overview
The broker gets B5 History: every action on a policy (issue, extra cover, date correction, cancellation, each payment attempt, each refund), oldest first, as a timeline. Each card shows who did it, what it did to the money, the sums or the Stripe status, the reason for a correction, and its IDs (PTX-…, LE-…, pi_…, re_…, evt_…). Every amount opens the same "How we got this" drawer as the policyholder's statement, in the broker's voice. History is built on the statement's role-neutral `StatementService`, so both roles see the same numbers to the cent. Unlike the policyholder, the broker sees the real state of money that went wrong: a refund Stripe failed, and why a card was declined. It serves the brief's criteria: every amount opens a trace with the ledger line, the change or payment, and the Stripe event; the Oct 15 → Oct 8 scenario reads as one correction card; both roles see the same numbers. Design: `design/plainly/B5-History.dc.html`, reached from B3 (`B3-Workspace.dc.html` Cover timeline header).

## Goals & User Stories
- As a broker, I see a policy's whole history in one place (every change, payment and refund, who did it and when) so I can answer any question about an amount.
- As a broker, I see every ID and Stripe reference next to the action it belongs to, so I can find it in Stripe or quote it to support.
- As a broker, I open "how we got this" on any amount and see the same sums the policyholder sees.
- As a broker, I see when money went wrong (a failed refund, a declined card and the bank's reason), so I can act on it.

## Acceptance Criteria
1. AC1: A broker reaches History at `/broker/policies/:id/history` from a "History" link on B3 (Cover timeline header), and back via "‹ <business> · <policy number>". A policyholder gets 403 from `/api/broker/policies/{id}/history`; an unknown policy gives 404 and a "not found" page.
2. AC2: One card per action, oldest first: each policy change (issue, extra cover, correction, cancellation), each payment attempt (paid, declined, waiting) and each refund (sent, on its way, failed). Each card has a date, the actor's name (broker or policyholder), a title with the change ID (e.g. "Start date corrected · PTX-0003"), and a rail dot coloured as in B5 (neutral change, green paid, red declined/failed, amber correction/waiting).
3. AC3: The money on each card is the sum of its ledger lines. A correction shows the net ("+$14.00 net"); pending money is marked pending ("−$14.00 pending") and, as on the statement, is not counted. The sum of all settled card amounts equals `LedgerQueryService.balance` to the cent, and equals the statement's last balance.
4. AC4: Each card shows its detail line: the sums for a charge or credit (`$730.00 × 328 ÷ 365`), or for a payment or refund the card, the Stripe status and refs (`pi_…`, `re_…`, `evt_…`). Corrections show the reason as written. Each card lists its ledger lines as chips (LE-…); a struck chip marks a line that was later undone.
5. AC5: A failed refund shows as "Refund failed" with Stripe's failure reason and a link to retry on B3. A declined payment shows the decline reason Stripe gave. The policyholder's statement wording is unchanged.
6. AC6: The trial scenario (Oct 15 → Oct 8, $730/yr extra cover) is one correction card: +$642.00 undone, +$656.00 charged, net +$14.00, the reason and the broker's name. The amounts are the same as on the policyholder's statement.
7. AC7: Every amount and ledger chip opens the "How we got this" drawer for that line, in the broker's voice (no "you"/"your"), with the same maths, cause, payment/refund and references as the policyholder's drawer. Keyboard, screen reader and focus return work as on the statement (statement AC8).
8. AC8: When the broker corrects a date again, reloading History shows a new correction card; earlier cards are unchanged.
9. AC9: Wording passes `web/src/jargon.test.ts`. Screens match B5 in light and dark at 1280 px. At 390 px, the date and actor sit above each card with no horizontal scroll.

## Technical Approach
- **API (in `app.plainly.statement`, controller → service → repository):** `HistoryService.forPolicy(policyId)` calls `StatementService.forPolicy(policyId)` for the rows and traces (one calculation for both roles). It groups the rows into `History.Action`s:
  - Policy-change actions: by the CHANGE ref (PTX), using `PolicyService.detail` transactions for type, effective date and reason, and `actorNames` for who.
  - Payment actions: one per payment row or pending payment.
  - Refund actions: one per refund, from `RefundListing`, including `status` and `failureReason`.
  - Each `Action` record: kind, date, actorName, title fields (type, displayId), net/pending cents, status (real: `REFUND_FAILED`, `DECLINED` + `failureReason`), reason, and the `Row`s (with traces).
- **Decline reason:** add `failureReason` to `PaymentService.Sent` through `PaymentRepository` (the `payments.failure_reason` column exists, from V6). No schema change.
- **Controller:** `BrokerHistoryController` maps `GET /api/broker/policies/{id}/history`. Role checking comes from `AuthFilter`'s `/api/broker` prefix; a missing policy gives `NotFoundException` → 404. Read-only, no `@Transactional` writes.
- **Web:**
  - Add `History`/`HistoryAction` types to `api/types.ts`.
  - `lib/historyCopy.ts` (pure, unit-tested) builds the card titles, detail lines and status words.
  - `pages/broker/HistoryPage.tsx` is the timeline: route `policies/:id/history` in `App.tsx` under `BrokerShell`, with a "History" link added to the `PolicyWorkspace` Cover timeline header.
  - `HowWeGotThis` gets an `audience: 'policyholder' | 'broker'` prop (default `policyholder`). `drawerCopy(row, audience)` switches the voice and the references note. Policyholder output is unchanged (existing tests stay green).
  - Ledger chips use the existing `Ref` style from `LedgerTable`.

## Out of Scope
- Filtering, search or export of the history.
- A per-broker access check (any broker can read any policy today; unchanged).
- The P2 cover timeline (`cover-timeline`) and emails (`emails`).
- Changing the policyholder's statement wording or the B3 ledger table.

## Standards Applied
- [backend/layers](../../../../../../standards/backend/layers.md): new controller and service in the statement module; the payment failure reason is read only via `PaymentRepository`.
- [global/tech-stack](../../../../../../standards/global/tech-stack.md): React 18 + Vite + Tailwind v4; Spring Boot 4 / Java 21 with JdbcClient.
- [global/git-workflow](../../../../../../standards/global/git-workflow.md): branch and commit conventions.

## Changelog
| Date | Author | Type | Change | Ref |
|---|---|---|---|---|
| 2026-11-23 | david-schwartzman | created | Initial shaping | — |
