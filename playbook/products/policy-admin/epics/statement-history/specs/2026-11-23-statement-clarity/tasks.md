# Tasks — Statement clarity

> Epic: statement-history
> Spec: 2026-11-23-statement-clarity
> Branch: feat/statement-history/statement-clarity

## Statement API

- [x] T1 Failing StatementServiceTest cases for AC3 (balanceAfterCents per correction, incl. −$14 credit), AC5 (inception/expiry/statement date, dueDate = oldest unpaid line's posting date, null when paid up) and AC7 ($1,000/yr cover corrected 7 days: reversal = −original, net = rebill − original, no stray cent) (testing skill, TDD)
- [x] T2 Statement.Header (inceptionDate, expiryDate, statementDate, dueDate) and Statement.Correction.balanceAfterCents filled in StatementService from PolicyService; T1 passes (service)
- [x] T3 StatementEndpointTest asserts the new header and correction fields on GET /api/me/statement (controller)

## Web

- [x] T4 statementCopy.test.ts and ledgerRows tests first, then the copy: no struck flag but reversedBy + "Reversed by correction · LE-…" (AC1), "Replaces: … · posted <date> above" (AC2), "Balance after" due/credit/zero (AC3), "effective" / "reversed, posted" labels (AC4), header sentences (AC5), "Refund to you" (AC6); types in api/types.ts
- [x] T5 LedgerTable drops line-through and renders the muted reversed row with its tag linking to the card; StatementPage renders the header and card-level balance and drops per-line balances inside cards; check B5 History still reads right (AC1–AC6, AC8)

## Docs

- [x] T6 DECISIONS.md entry: no strikethrough on reversed lines; due on receipt; immediate refunds kept despite refund-then-charge churn; taxes and fees out of scope

## Verification

- [ ] T7 Run both suites with coverage on the changed files (testing skill)
- [ ] T8 Run QA against AC1–AC8 with the Nov 23 → Nov 30 → Nov 15 scenario at 1280 px light and 390 px dark (qa skill)
