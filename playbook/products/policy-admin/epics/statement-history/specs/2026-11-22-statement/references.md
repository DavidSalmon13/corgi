# References for Your statement and "How we got this"

## Related Specs

- **[Considered]** [2026-11-22-issue-policy](../../../policy-lifecycle/specs/2026-11-22-issue-policy/spec.md) — matched on `web/**` (glob), dismissed in Step 4d: that claim is a catch-all from scaffolding the SPA; the statement, its drawer and its copy are new and owned here.

## Upstream Meeting

None. Sources: `ASSIGNMENT.md` (core workflow step 5, the debrief), the statement-history product brief and tech plan.

## Designs

- `design/plainly/Main.dc.html` — P3 Statement: normal, refund issued; correction card opens and closes.
- `design/plainly/P4-HowWeGotThis.dc.html` — P4 drawer: charge, reversal, payment, refund. Its sample data fixes the copy for each kind (plain sentence, maths, note, cause, payment, references).
- `design/plainly/P1-Home.dc.html` — entry point into the statement.

## Similar Implementations

### Policyholder read model

- **Location:** `api/src/main/java/app/plainly/me/MyCoverService.java`, `MyCoverController.java`
- **Relevance:** the existing `/api/me` read built from `PolicyService.latestForCustomer` and its `PolicyViews.Detail` (ledger lines, transactions, refunds).
- **Key patterns:** reasons returned as codes (`DueReason`) that the web turns into sentences; the cancellation is read back from what was posted, never stored twice; `refundWaiting` sums unsettled refunds, failed ones included.

### Ledger rows

- **Location:** `web/src/lib/ledgerRows.ts`, `web/src/components/LedgerTable.tsx`
- **Relevance:** the broker's B3 ledger already formats every line's calculation (`$730.00 × 328 ÷ 365`), the "Undone by" note, the $0.00 declined payment and the reference chips.
- **Key patterns:** a pure row builder with tests; table on desktop, stacked list on phones; `Ref` chip for ids.

### Cover timeline

- **Location:** `web/src/components/CoverTimeline.tsx`, `web/src/lib/timeline.ts`
- **Relevance:** how dates and corrected starts ("from Oct 8 · was Oct 15") are worded today.

### Plain-language copy

- **Location:** `web/src/lib/*Copy.ts` with `*Copy.test.ts`, `web/src/components/Term.tsx`, `web/src/jargon.test.ts`
- **Relevance:** every screen's wording lives in a pure, tested copy module; `Term` explains unavoidable terms; the jargon test guards the words.

### Ledger data

- **Location:** `api/src/main/java/app/plainly/ledger/LedgerLine.java`, `LedgerQueryService.java`
- **Relevance:** each line already carries calc inputs, cover dates, reversal links, PTX id, Stripe payment/refund/event ids and the balance after it, so the statement needs no schema change.
