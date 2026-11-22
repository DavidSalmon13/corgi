# Tasks — Policy history for the broker

> Epic: statement-history
> Spec: 2026-11-23-broker-history
> Branch: feat/statement-history/broker-history

## History API
- [x] T1 Failing HistoryServiceTest for AC2–AC6 and AC8: one action per change/payment/refund oldest first, actor names, correction card net (Oct 15 → Oct 8 = +$14.00), settled sum = ledger balance = statement last balance, pending outside the sum, failed refund + decline reason (testing skill, TDD)
- [x] T2 Add failureReason to PaymentService.Sent via PaymentRepository.sentForPolicy (repository + service)
- [x] T3 History records + HistoryService.forPolicy in app.plainly.statement, grouping StatementService rows by action; T1 passes (service)
- [ ] T4 BrokerHistoryController GET /api/broker/policies/{id}/history; BrokerHistoryEndpointTest incl. 403 for a policyholder and 404 for an unknown policy (controller)

## Web
- [ ] T5 api/types.ts History types + lib/historyCopy.ts with historyCopy.test.ts first: card titles with IDs, detail lines (sums / Stripe status), status words (Refund failed, decline reason, pending), net wording
- [ ] T6 drawerCopy(row, audience) broker voice + HowWeGotThis audience prop; statementCopy.test.ts keeps the policyholder output identical and adds broker cases
- [ ] T7 HistoryPage at /broker/policies/:id/history (App.tsx route, "History" link in PolicyWorkspace Cover timeline header, back link): timeline rail and dots, cards, ledger chips, struck undone chips, retry link on failed refund, light/dark, 390 px stacked
- [ ] T8 Every amount and ledger chip opens HowWeGotThis (audience="broker") with focus return

## Docs
- [ ] T9 DECISIONS.md: history groups the statement's rows by action on the server (one calculation); the broker sees the real refund/decline status while the policyholder's wording stays soft

## Verification
- [ ] T10 Run both suites with coverage on the changed files (testing skill)
- [ ] T11 Run QA against AC1–AC9, incl. the Oct 15 → Oct 8 scenario and a live re-correction, at 1280 px light and 390 px dark (qa skill)
