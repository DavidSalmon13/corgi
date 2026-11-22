# References for Correct a start date

## Related Specs

- **[Related]** [2026-11-22-collect-premium](../2026-11-22-collect-premium/spec.md): it owns `api/src/main/java/**/billing/**`. This spec adds only `billing/Refund*.java` and the refund cases in `WebhookHandler`. Kept separate because collect-premium is done and is about collecting money, not returning it.
- **[Related]** [2026-11-22-issue-policy](../2026-11-22-issue-policy/spec.md): it owns `db/migration/**` and `web/**` as foundation-wide globs. This spec owns only `V8__corrections_and_refunds.sql`, `lib/correctCopy.ts` and `CorrectCoverForm.tsx` inside them.
- **[Related]** [2026-11-22-add-coverage](../2026-11-22-add-coverage/spec.md): its B4 `ChangePolicyPage.tsx` hosts the new Correct tab. Its service, controller and form are the pattern to mirror. Its DECISIONS entry left out a unique (policy, kind) index so corrections can replace a coverage row.

## Upstream Meeting

None. The sources are `ASSIGNMENT.md` (the trial scenario) and `DECISIONS.md` ("A correction that lowers the price is refunded automatically").

## Similar Implementations

### Add coverage service
- **Location:** `api/src/main/java/app/plainly/policy/AddCoverageService.java`, `AddCoveragePlan.java`, `AddCoverageController.java`
- **Relevance:** the same preview/save shape for a broker policy change.
- **Key patterns:** load → validate → pure `plan()` shared by preview and save; `select … for update` on the policy, then a separate read (READ COMMITTED); a `ValidationException` field map → 422; string request record → null → field message; 201 with an `Added` result.

### Ledger posting
- **Location:** `api/src/main/java/app/plainly/ledger/LedgerPostingService.java`, `NewEntry.java`, `LedgerQueryService.java`
- **Relevance:** reversal support already exists (`reverses_entry_id unique`, `NewEntry.reversal`, `check()` requires amount == −original).
- **Key patterns:** `NewEntry.charge` copies the `ProRata` calc; balance = sum of the lines. Gaps: no REFUND factory or check; `reversal` nulls `coverage_id`.

### Payments and webhooks
- **Location:** `api/src/main/java/app/plainly/billing/PaymentService.java`, `WebhookHandler.java`, `StripeGateway.java`; tests `StripeTestSupport`, `FakeStripeGateway`, `PaymentTestData`
- **Relevance:** cancelling OPEN intents, idempotency keys, and webhook dedupe through `processed_stripe_events` with a row lock per payment.
- **Key patterns:** insert-first event dedupe; settle money only on a signed event; no charge id is stored, so refunds go by `payment_intent`. PROCESSING payments are not in the balance, so corrections are blocked while one is waiting.

### B4 add-cover form
- **Location:** `web/src/pages/broker/ChangePolicyPage.tsx` (`AddCoverForm`), `PolicyWorkspace.tsx` (`useSavedBanner`)
- **Relevance:** the host page and the form pattern.
- **Key patterns:** 250 ms debounced preview `useQuery` with `keepPreviousData`; 422 mapping from preview and save; an error summary with focus; a once-only banner in router state. The whole-page "blocked" card must become per-tab.

### Ledger rows
- **Location:** `web/src/lib/ledgerRows.ts`
- **Relevance:** already renders "Undone by LE-x", struck lines, "Undo of LE-x" and correction highlighting; refund refs need adding.

### Designs
- `design/plainly/B4-ChangePreview.dc.html`: Correct tab (preview owes more, preview refund due, confirm, saved, refund failed)
- `design/plainly/B3-Workspace.dc.html`: ledger with the undo and corrected lines, and the "from Oct 8 · was Oct 15" timeline
- `design/plainly/P1-Home.dc.html`: "$14.00 due" and "Refund on its way" states
