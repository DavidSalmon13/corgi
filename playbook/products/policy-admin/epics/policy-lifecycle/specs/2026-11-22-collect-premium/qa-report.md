# QA Report — Collect premium

> Spec: spec.md
> Date: 2026-11-22 · QA by: david-schwartzman · Mode: setup
> Verdict: PASS WITH NOTES
> Stale: spec changed 2026-11-22 (hotfix payment-review-fixes corrected AC7, AC10, AC13); rerun the qa skill

## Acceptance Criteria

| AC | Criterion | Result | Evidence |
|---|---|---|---|
| AC1 | P1 "Pay $X" → `/me/pay`; nothing due → "You're all paid up." with a link back | pass | `PaymentViewsTest.beforePayingTheOwnerSeesWhatsDueAndThatPaymentsAreOn`, `paymentCopy.test.ts` (P1 card choice); `web/src/pages/me/PayPage.tsx:112` with the "Back to your cover" link in `Notice` (`PayPage.tsx:66`); live: 409 `nothing_due` "You're all paid up." once Maya had paid |
| AC2 | P5 card form: heading, reason + "See why", Payment Element, Stripe note, server-set amount | pass | `MyPaymentsTest.startingAPaymentOpensAStripeIntentForTheBalanceDue`, `.anAmountSentByTheBrowserIsIgnored`; `PayPage.tsx:124` (heading), `:129` (See why), `:234` (PaymentElement); `lib/paymentCopy.ts:6` (note) |
| AC3 | "Card ending NNNN · Used last time" chosen by default; "A different card"; saved card pays with no card details | pass | `MyPaymentsTest.aCardThatPaidBeforeIsOfferedAndCanPayWithoutCardDetails`, `.withNoCardOnFileTheSavedCardOptionIsRefused`; `paymentCopy.test.ts` "offers the card used last time"; `PayPage.tsx:228` |
| AC4 | "Waiting for your bank" copy, card and date, polls; the browser's confirm never settles a payment | pass | `MyPaymentsTest.theBrowsersConfirmNeverSettlesAPayment`; `paymentCopy.test.ts` "waits for the bank once submitted, whatever the browser was told", "waiting for your bank"; polling every 2s until SUCCEEDED/FAILED at `web/src/pages/me/PaymentProgress.tsx:47` |
| AC5 | `charge.succeeded` → SUCCEEDED, one −amount PAYMENT line with its event id, P5 "Paid", P1 all paid up | pass | `StripeWebhookTest.aSucceededChargeMarksThePaymentPaidAndPostsOnePaymentLine`, `LedgerTest.aPaymentLineIsACreditThatTracesToItsPaymentAndStripeEvent`, `PaymentViewsTest.afterPayingTheOwnerIsAllPaidUp`; live: card 4242 → LE-0007 −$1,200.00 with `evt_…`, $0 due |
| AC6 | `charge.failed` → FAILED, balance unchanged, $0.00 attempt line, P5/P1 "didn't go through" | pass | `StripeWebhookTest.aFailedChargeMarksThePaymentFailedAndPostsANoMoneyMovedLine`, `LedgerTest.aDeclinedAttemptIsAZeroLine`, `PaymentViewsTest.afterADeclineTheOwnerSeesItDidntGoThrough`, `paymentCopy.test.ts` "didn't go through"; live: card 0002 → LE-0006 $0.00, $1,200.00 still due |
| AC7 | PROCESSING → P1 waiting; a second payment gets 409 "still on its way" | pass | `StripeWebhookTest.aProcessingIntentMarksThePaymentProcessingWithoutTouchingTheLedger`, `MyPaymentsTest.aSecondPaymentIsRefusedWhileTheFirstIsOnItsWay`, `PaymentViewsTest.whileTheBankDecidesTheOwnerSeesItWaiting` |
| AC8 | Webhook needs no session; raw-body signature check; missing/invalid → 400, nothing changes | pass | `StripeWebhookTest` (no signature, another secret, edited body, old signature); live: no signature 400, bad signature 400, real `stripe listen` deliveries 200 |
| AC9 | Each event id processed once; replay and two copies at once post nothing new, replay gets 200 | pass | `StripeWebhookTest.replayingAnEventIsAnsweredOkButPostsNothingNew`, `.twoCopiesOfAnEventArrivingTogetherPostOneLine` |
| AC10 | Unknown type, unknown PaymentIntent, success after failure → 200 without failing | pass | `StripeWebhookTest.anEventTypeWeDontUseIsRecordedAndIgnored`, `.aChargeForAPaymentWeNeverStartedIsIgnored`, `.moneyThatMovesAfterAFailureIsStillRecorded`, `.aLateProcessingEventNeverUndoesAPayment` |
| AC11 | B3 shows "Paid" / "Didn't go through" with the `pi_…` reference; B3 and B1 show waiting while PROCESSING | pass | `PaymentViewsTest.theBrokerLedgerTracesEachPaymentLineToStripe`, `.theBrokerSeesWhenAPaymentIsWaitingForTheBank`; `ledgerRows.test.ts` (AC11 cases); `policyCopy.test.ts:40`; `PolicyWorkspace.tsx:86`, `PoliciesPage.tsx:40` |
| AC12 | Own payments only; someone else's id → 404; `/api/me/**` is policyholder-only | pass | `MyPaymentsTest.anOwnerCantSeeOrPayWithSomeoneElsesPayment`, `.paymentsAreForPolicyholdersOnly`; live: unknown id 404, broker 403, signed out 401 |
| AC13 | Keys from env in `.env.example`, never committed; no keys → app starts and explains; README; DECISIONS | pass | `.env.example:18-20`; `.env` is git-ignored (`.gitignore:2`), and `git log -S` finds no key in history; `MyPaymentsTest.withoutStripeKeysPaymentsAreSwitchedOffPlainly`, `lib/paymentCopy.ts:122`; README "Paying with Stripe (test mode)"; DECISIONS 2026-11-22 entries (T1, T17, T18) |

## Checks Run

- build: `cd web && npm run build` (tsc + vite): pass
- lint: no lint script configured in `web/` or `api/`: not run
- typecheck: included in `npm run build` (`tsc --noEmit`): pass
- tests: `cd api && ./gradlew test`: pass (126 passed, 0 failed); `cd web && npm test`: pass (89 passed, 0 failed)
- live: `stripe listen` in Stripe test mode with cards 0002 and 4242, see DECISIONS 2026-11-22 "The live payment check was run against the API, not by eye"

## Issues Found

| # | Severity | Issue | Route |
|---|---|---|---|
| 1 | low | Nobody has looked at the P5/P1/B3 payment states in a browser. Their copy and state choice are covered by tests and the live API pass. | Owner to look before the demo |
| 2 | med | Earlier PaymentIntents (left open, or declined and then retried) are never cancelled at Stripe. A second tab can still pay one after "Try again", which charges the customer twice (`PaymentService.start`). | /hotfix |
| 3 | med | Card payments switch on without `STRIPE_WEBHOOK_SECRET`. The card is charged, but every webhook gets 400, so the ledger never shows the money (`StripeJavaGateway` constructor). | /hotfix |
| 4 | med | Cards go straight to succeeded with no `payment_intent.processing`. Until the webhook lands, the payment still looks OPEN: P1 offers Pay again, and the AC7 409 guard doesn't fire (`PaymentService.start`, `paymentCopy.ts`). | /hotfix |
| 5 | low | A saved-card confirm that fails says "Nothing was charged" even when the intent had already succeeded (`StripeJavaGateway`, `PaymentProblem`). | /hotfix |
| 6 | low | A live (`sk_live_`) key only logs a warning instead of turning payments off. | /hotfix |
| 7 | low | A PROCESSING payment that ends with `payment_intent.canceled`/`payment_failed` and no `charge.failed` stays PROCESSING and blocks paying. An event that can't be read is still marked as processed (`WebhookHandler`). | /hotfix |
| 8 | low | Stripe network calls run while the policy row is locked (`PaymentService.start`). | note |

## Notes

- Findings 2–8 come from the code and security reviews of 2026-11-22. Neither review found anything critical or high. The user chose to open the PRs and fix these afterwards.

- Setup mode, so the browser-check gap stays a note.
- B1 labels the waiting state "Active · waiting for the bank" (`lib/policyCopy.ts`), which carries the AC11 wording inside the status chip.
- No PR yet: `gh` couldn't reach api.github.com during this run. Open one with the pr skill.
- The live pass added real rows to the local dev database (payments 2–3, LE-0006 and LE-0007 on Maya's policy). That's expected, since the ledger is append-only.
