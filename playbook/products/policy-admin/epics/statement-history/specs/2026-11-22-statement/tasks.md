# Tasks — Your statement and "How we got this"

> Epic: statement-history
> Spec: 2026-11-22-statement
> Branch: feat/statement-history/statement

## Statement API

- [x] T1 Failing StatementServiceTest for AC2–AC7: running balance = ledger balance, pending payment/refund rows outside the balance, declined $0 line, correction grouping (Oct 15 → Oct 8 and → Oct 22), trace per kind with refs (testing skill, TDD)
- [x] T2 PolicyTransactionRepository read of actor names per transaction (created_by → app_users), exposed through PolicyService (repository + service)
- [x] T3 Statement records + StatementService.forPolicy in app.plainly.statement: rows, pending rows, corrections, traces, banner state; shared due-reason helper used by MyCoverService too; T1 passes (service)
- [x] T4 MyStatementController GET /api/me/statement; StatementEndpointTest incl. 403 for a broker and own-policy-only (controller)

## Web

- [ ] T5 api/types.ts Statement types + lib/statementCopy.ts with statementCopy.test.ts first: banner sentences, row titles, correction summary (day delta, net, per-day only when exact), P4 plain sentence/maths/cause/payment/refs per kind
- [ ] T6 StatementPage at /me/statement (route in App.tsx, link from P1 HomePage and back): banner, rows with running balance, pending rows, correction card open/close, P3 states in light/dark, 390 px stacked
- [ ] T7 HowWeGotThis drawer: opens from every amount, dialog on desktop / bottom sheet on phones, focus trap, Esc and close return focus (AC8)

## Docs

- [ ] T8 DECISIONS.md: traces embedded in the statement response instead of a per-entry endpoint; pending money shown but outside the balance

## Verification

- [ ] T9 Run both suites with coverage on the changed files (testing skill)
- [ ] T10 Run QA against AC1–AC10, incl. the Oct 15 → Oct 8 scenario and a live re-correction, at 1280 px light and 390 px dark (qa skill)
