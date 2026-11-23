# QA Report — Emails: policy issued, payment and refund receipts

> Spec: spec.md
> Date: 2026-11-23 · QA by: david-schwartzman · Mode: setup
> Verdict: PASS WITH NOTES

## Acceptance Criteria

| AC | Criterion | Result | Evidence |
|---|---|---|---|
| AC1 | Spike recorded; `.env.example` lists the four variables | pass | curl spike on 2026-11-23: 200 with a message id to the account owner, the same `Idempotency-Key` returned the same id, another body under the same key was a 409, and another recipient was a 403. Recorded in DECISIONS.md ("Resend sandbox spike…", "Every demo email goes to one test inbox…"). `.env.example` lists `RESEND_API_KEY`, `RESEND_FROM`, `RESEND_TEST_TO`, `PLAINLY_WEB_URL`. |
| AC2 | One issue email after commit, with the subject, body and sign-up link; nothing on rollback | pass | `IssuePolicyServiceTest.issuingEmailsTheOwnerOnceAfterItCommits`, `anIssueThatIsRefusedSendsNoSecondEmail`; `NotifyServiceTest.aChangeThatRollsBackSendsNothing`; hook `IssuePolicyService.java:101`; link `EmailTemplates.java:62`. Live: "Your cover with Plainly starts Sep 1, 2026", SENT. |
| AC3 | One payment receipt after `charge.succeeded`, with amount, card, date, left to pay | pass | `StripeWebhookTest.aSettledPaymentSendsOneReceiptHoweverOftenStripeSaysSo`, `aDeclinedPaymentSendsNoReceipt`; `NotifyServiceTest.thePaymentReceiptSaysWhatIsLeftToPayAfterIt`; hook `StripeWebhookService.java:112`; wording `EmailTemplates.java:68-77`. Live: three receipts ($1,200.00, $642.00, $14.00), one per payment. |
| AC4 | One refund receipt when a refund settles, with the reason; none for a failed refund | pass | `RefundWebhookTest.aSettledRefundSendsOneReceiptHoweverOftenStripeSaysSo`, `aFailedRefundSendsNoReceipt`; `NotifyServiceTest.aRefundReceiptSaysWhyFromTheChangeThatCausedIt`; hook `RefundService.java:254`, only after `markSucceeded`. Live: two "$14.00 is on its way back to you", one per refund, though Stripe sent both `refund.created` and `refund.updated`. |
| AC5 | Once per event: unique (kind, source_id), and `Idempotency-Key: <kind>/<source_id>` | pass | `V10__sent_emails.sql:20` `unique (kind, source_id)`; `ResendEmailGateway.java:62`; `SentEmailRepositoryTest.aSecondClaimForTheSameKindAndSourceGetsNothing`, `NotifyServiceTest.theSameEmailIsSentOnlyOnce`, the replay tests in AC3/AC4. Live: 6 rows for 6 events, no duplicates. |
| AC6 | Money first: a Resend failure keeps the change and marks the row FAILED; a blank key logs that email is off and records SKIPPED | pass | `NotifyServiceTest.whenResendFailsTheChangeStandsAndTheEmailIsMarkedFailed`, `withEmailOffTheEmailIsRecordedAsSkipped`; `ResendEmailGatewayTest.withoutAKeyEmailIsOff`; `NotifyService.java:103-111`; startup warning `ResendEmailGateway.java:43`. |
| AC7 | HTML and text parts, no unexplained jargon, "Sent to <owner>" and the demo footer | pass | `EmailTemplatesTest.noEmailUsesInsuranceJargon`, `everyEmailNamesTheOwnersAddressAndSaysItIsADemo`, `namesTypedByABrokerAreEscapedInTheHtml`; `EmailTemplates.java:36,160`. |
| AC8 | Live run: issue, pay, correct Oct 15 → Oct 8, one email each; `sent_emails` holds the Resend id | pass | Live run on the real API with Stripe test mode and the Resend sandbox (policy 12): issued → pay $1,200.00 → add cover from Oct 15 → pay $642.00 → correct to Oct 8 → pay $14.00 → correct to Oct 22 (refund) → both refunds settled by webhook. `sent_emails`: 1 POLICY_ISSUED, 3 PAYMENT_RECEIPT, 2 REFUND_RECEIPT, all SENT with a `resend_id`, all `to_address` = the owner. See Notes on checking the dashboard. |

## Checks Run

- build + tests (api): `./gradlew build`: pass (397 tests, 0 failures)
- build + typecheck (web): `npm run build` (`tsc --noEmit && vite build`): pass
- tests (web): `npm test`: pass (20 files, 231 tests)
- lint: no lint script in `web/` or `api/`; not run

## Issues Found

| # | Severity | Issue | Route |
|---|---|---|---|
| 1 | low | The README's `stripe listen` command forwards payment events only. Locally, `refund.created` / `refund.updated` / `refund.failed` never reach the API, so refunds stay PENDING and no refund receipt is sent. The QA run added them by hand. The README also doesn't mention the Resend variables. | README fix (docs) |
| 2 | low | A refund split across two payments sends two receipts (here, two × $14.00 for one correction). This matches the spec ("one receipt per refund") but may read oddly to an owner. | Note only; consider one receipt per correction later |

## Notes

- **Checking Resend's side (AC8):** Resend returned a message id for each of the 6 emails, and each id is stored in `sent_emails.resend_id`. The API key has send access only, so QA couldn't read delivery status back from Resend's API (`restricted_api_key`). Open the Resend dashboard → Emails, or the test inbox, to see the six emails arrive once each.
- **AC8's own correction (Oct 15 → Oct 8) creates a charge, not a refund,** so its email is the $14.00 payment receipt once the owner pays. The extra Oct 8 → Oct 22 correction was added so a refund receipt was also checked live.
- **Different from the spec's Technical Approach:** `NotifyService` listens for `NotifyEvents` instead of being called directly, and it refuses to run outside a transaction (`NotifyService.java:82`) rather than using `propagation = MANDATORY`. Logged in DECISIONS.md (2026-11-23, "Emails hear about money through events…"); the behaviour the ACs ask for is unchanged.
- **Hygiene:** T1–T11 are done; T12 is this run. No PR yet. `playbook/config.yml` names `main` as the default branch, but this repo's PRs go to `dev`.
- **Left from the run:** policy 12 (`Juniper QA Bakery LLC`) and its payments and refunds are in the local database and Stripe test mode.
