# References for Collect premium

## Related Specs

- **[Considered]** [2026-11-22-issue-policy](../2026-11-22-issue-policy/spec.md). It matched on `web/**` and `api/src/main/resources/db/migration/**`. Those are the first spec's blanket globs, so the overlap was dismissed in Step 4d: payments are new work. This spec still extends `/me` (P1 payment states) and `/broker/policies/:id` (B3 payment rows), which stay with issue-policy.

## Upstream Meeting

_None._ The sources are `ASSIGNMENT.md` (requirement 2), `DECISIONS.md` ("Only Stripe webhooks change payment status") and the epic's product brief and tech plan.

## Designs

- `design/plainly/P5-Pay.dc.html`: card form (saved card + new card), waiting for your bank, paid, didn't go through.
- `design/plainly/P1-Home.dc.html`: amount due, nothing due, payment waiting, payment failed.
- `design/plainly/B3-Workspace.dc.html`: payment rows ("Paid", "Didn't go through" at $0.00, "Waiting for the bank") with `pi_…` refs.
- `design/plainly/P4-HowWeGotThis.dc.html`: the payment section and references (Stripe payment and event). The drawer itself is out of scope.

## Similar Implementations

### Ledger posting

- **Location:** `api/src/main/java/app/plainly/ledger/` (`LedgerPostingService`, `NewEntry`, `LedgerQueryService`, `LedgerLine`).
- **Relevance:** PAYMENT lines go through the same poster.
- **Key patterns:**
  - `post(policyId, txId, entries)` joins the caller's transaction.
  - `check()` validates by entry type. It needs PAYMENT rules.
  - `balance()` is the sum of `amount_cents`.

### Policyholder cover

- **Location:** `api/src/main/java/app/plainly/me/MyCoverController.java`, `web/src/pages/me/HomePage.tsx`.
- **Relevance:** P1's amount-due card gains the pay button and the payment states.
- **Key patterns:**
  - `due = max(0, balance)`.
  - The `dueReason` copy.
  - `useQuery(['my-cover'])`.

### Broker workspace

- **Location:** `web/src/pages/broker/PolicyWorkspace.tsx`, `web/src/lib/ledgerRows.ts`, `web/src/lib/policyCopy.ts`.
- **Relevance:** the ledger rows and the balance note.
- **Key patterns:**
  - `ledgerRows` already maps PAYMENT to "Paid".
  - `policyStatus(status, balance)` sets the pill.

### Auth filter

- **Location:** `api/src/main/java/app/plainly/auth/AuthFilter.java`.
- **Relevance:** the webhook must be exempt and take no session.
- **Key patterns:**
  - The path is normalized with `UrlPathHelper`.
  - The exemption sits next to `/api/health` and `/api/auth/**`.

### Field errors and tests

- **Location:** `web/src/pages/broker/NewPolicyPage.tsx` (field errors from the API `fields` map), plus these in `api/src/test/java/app/plainly/`:
  - `IntegrationTest`;
  - `AuthTestSupport`;
  - `TestData`;
  - `me/MyCoverTest.ownerOf`.
- **Relevance:** how errors reach the form, and how tests set up their data.
- **Key patterns:**
  - Tests share one Testcontainers context and never clean up, so each test creates its own data.
  - Owner emails are unique (`TestData.uniqueEmail`).
