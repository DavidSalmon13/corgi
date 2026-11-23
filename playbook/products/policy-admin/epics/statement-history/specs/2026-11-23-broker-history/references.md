# References for Policy history for the broker

## Related Specs

- **[Related]** [2026-11-22-statement](../2026-11-22-statement/spec.md): history reuses its role-neutral `StatementService`, the `Statement` records (`Row`, `Trace`, `Ref`) and the `HowWeGotThis` drawer, which gains an `audience` prop. It stays separate because it is a different screen, role and grouping: B5 groups by action, while the statement lists ledger lines.
- **[Considered]** [policy-lifecycle / 2026-11-22-issue-policy](../../../policy-lifecycle/specs/2026-11-22-issue-policy/spec.md): matched on the parent route `/broker/policies/:id` and the blanket `web/**`. Dismissed in Step 4d because history only adds a "History" link to `PolicyWorkspace.tsx`; the B3 workspace itself is unchanged.

## Design

- `design/plainly/B5-History.dc.html`: a vertical timeline, one card per action. On the left are the date and the actor; the rail has a coloured dot; the card shows the title with its PTX id, the money, a detail line (the sums or the Stripe status), the reason for a correction, and the ledger-line chips. The design has no states or filters, and it is desktop only (1280 px). The 390 px layout is our own: the date and actor stack above the card.
- `design/plainly/B3-Workspace.dc.html:74`: the "History" link in the Cover timeline section header.

## Similar Implementations

### Statement module

- **Location:** `api/src/main/java/app/plainly/statement/` (`StatementService`, `Statement`, `MyStatementController`)
- **Relevance:** history is built on `StatementService.forPolicy(policyId)`, so both roles get one calculation.
- **Key patterns:**
  - The private `Builder` reads `PolicyService.detail`, `actorNames` and `PaymentService.sentForPolicy`.
  - Each `Row` carries a `Trace` with its `Ref`s; the CHANGE ref gives the PTX to group by.
  - `correctionId` groups the three lines of a correction.

### Statement page and drawer

- **Location:** `web/src/pages/me/StatementPage.tsx`, `web/src/components/HowWeGotThis.tsx`, `web/src/lib/statementCopy.ts`
- **Relevance:** the drawer wiring (`trace` state `{row, from}`, `returnTo` focus) and `drawerCopy(row)`, which is all in the policyholder's voice today.
- **Key patterns:**
  - The native `<dialog>` is a bottom sheet on phones.
  - `REF_LABEL` is role-neutral.
  - The note "Quote these if you ever need to ask us or your bank…" is the policyholder-only text to switch for the broker.

### Broker workspace

- **Location:** `web/src/pages/broker/PolicyWorkspace.tsx`, `web/src/components/LedgerTable.tsx` (`Ref`), `web/src/lib/ledgerRows.ts`
- **Relevance:** the broker's styling for IDs and refs, and the place the History link goes.

### Broker endpoint tests

- **Location:** `api/src/test/java/app/plainly/policy/BrokerPolicyReadTest.java`, `api/src/test/java/app/plainly/statement/StatementEndpointTest.java`, `CorrectionTestData`
- **Relevance:** the role checks (403), 404 handling and correction fixtures to copy.

### Data for the real status

- `billing/RefundListing`: `status` and `failureReason`, already returned.
- `payments.failure_reason`: the column exists (V6) but is not yet in `PaymentService.Sent`; T2 adds it.
