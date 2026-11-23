# QA Report — Cancel a policy and refund unused days

> Spec: spec.md
> Date: 2026-11-23 · QA by: david-schwartzman · Mode: setup
> Verdict: PASS WITH NOTES

Rerun for PR #98 (`fix/policy-lifecycle/final-cleanups`), which changes the customer side of a cancellation:
- a flat cancellation (no used days) reads as one on P1 and P6 even before its start date;
- P6 "How we worked it out" lists the unpaid premium first, then each return premium, then the bottom line;
- P6 shows the broker's email and phone.

The money, the ledger and the API are unchanged by this PR's cancellation work. Also rechecked: more-policies AC11 (flat
cancellation wording with its "?").

## Acceptance Criteria

| AC | Criterion | Result | Evidence |
|---|---|---|---|
| AC1 | B3 Cancel action opens B4 Cancel tab; cancelled policy blocks all tabs | pass | Unchanged by this PR. `PolicyWorkspace.tsx:224,288-289` (`canCancel` only when ACTIVE); `ChangePolicyPage` blocked-tab tests in the web suite |
| AC2 | Cover-ends date with hint; required reason shown word for word | pass | Unchanged. `CancelPolicyForm.tsx:194`; `cancelCopy.test.ts` "what to fix before cancelling" |
| AC3 | Preview = save path, writes nothing; credits, kept back, refund pieces, customer-will-see, labels | pass | `CancelPolicyPlanTest`, `CancellationEndpointsTest` (6 AC3 mappings) green; `cancelCopy.test.ts` B4 preview rows unchanged ("Unpaid balance kept back · Correction still owed · +$14.00") |
| AC4 | One credit per live cover; future-start cover and start-day cancel credited in full | pass | `CancelPolicyPlanTest` (3 AC4 mappings) green; observed: GL-2026-0018 cancelled from its Nov 28 start credits −$1,200.00 and −$730.00, the full charge |
| AC5 | Credit smaller than balance → nothing refunded, still-owed line | pass | `cancelCopy.test.ts` "shows what is still owed…" and B4 rows; API tests (5 AC5 mappings) green |
| AC6 | 422 validation for date, reason, already cancelled, payment waiting | pass | `CancellationEndpointsTest` (4 AC6 mappings) green |
| AC7 | Confirm dialog; one transaction (PTX, CREDIT lines, CANCELLED, PI cancelled, PENDING refunds); refunds after commit; banner | pass | `CancelPolicyServiceTest`, `CancellationRepositoryTest` (10 AC7 mappings) green |
| AC8 | Append-only ledger; concurrent cancel → one; REFUND only on signed webhook, replay-safe; retry on failure | pass | `LedgerTest`, `RefundWebhookTest`, `RefundServiceTest` (7 AC8 mappings) green |
| AC9 | B3 for a cancelled policy: pill, period, balance note, striped timeline, credit lines | pass | Unchanged by this PR; API + web tests (2 AC9 mappings) green |
| AC10 | P1 Cancelled card: pending / settled / owing sentences, Pay, link to P6; protection line and period end on the cancel day | pass (with note) | `cancelCopy.test.ts` "P1: says the refund is on its way…", "P1: shows what is still owed…" green, wording unchanged for non-flat cancellations. Observed P1 for a flat cancellation (GL-2026-0018): "Your policy was cancelled before it started (a flat cancellation)", "See how we worked it out" opens P6. For a flat cancellation the period shows the period as bought, struck through and marked cancelled, not "Nov 28 – Nov 28". See Notes |
| AC11 | P6: title, used/unused bar caption, credit lines with kept back and refund, refund pill; non-cancelled → P1 | pass (with note) | `cancelCopy.test.ts` "P6: titles the page…", "explains the striped days…", "shows each credit…", "says where the refund is…" and the new "flat cancellation" tests green; redirect at `CancellationPage.tsx:145`. Observed P6 (390 px light, 1280 px dark, no horizontal scroll): rows add up +$1,930.00 − $1,200.00 − $730.00 = $0.00. The unpaid premium is now listed first and labelled "Unpaid premium from your first year · Charged, not paid yet" instead of "kept back". See Notes |
| AC12 | Plain language; jargon.test passes | pass | `jargon.test.ts` green; new terms ("flat cancellation", "return premium", "short-rate penalty", "premium") render through `Glossed` with their "?" (observed on P1 and P6) |

### Rechecked in another spec

| Spec · AC | Criterion | Result | Evidence |
|---|---|---|---|
| more-policies · AC11 | Standard terms (flat cancellation…) with "?" | pass | `myCoverCopy.test.ts` "a policy cancelled flat…" (past and future start dates) and `cancelCopy.test.ts` glossParts check for `flatCancellation`; observed "?" on P1 and P6 |

## Checks Run

- build: `npx vite build` (web) — pass
- lint: no lint script configured (web or api) — not run
- typecheck: `npx tsc --noEmit` (web) — pass
- tests: `npx vitest run` (web) — pass (27 files, 400 tests); scoped `cancelCopy`, `myCoverCopy`, `jargon` — 143 pass; `./gradlew test` (api) — pass (full suite)

## Issues Found

(none)

## Notes

- **The spec's wording is behind the screens.** AC11 still describes "the amount kept back" after the credits and a
  "Money back to card" line. P6 now shows the unpaid premium first ("Unpaid premium from …"), then the return
  premiums, then "Refund to card …" or "Nothing to pay or refund". The same facts are shown, now in an order that adds up
  like the statement. AC10 says the cover period ends on the cancel day, which still holds except for a flat
  cancellation: there it would read "Nov 28 – Nov 28", so P1 shows the bought period struck through and marked cancelled.
  AC3 and AC9 also predate the standard-terms change ("Base cover, unused days" is now "General liability return
  premium"), and AC12 predates it too ("premium" is now allowed, with its "?"). These are recorded in DECISIONS.md
  (2026-11-23). Bring them into the spec with `/spec-changes`.
- **Hygiene.** All tasks in `tasks.md` are `[x]`; `> PR:` #50 recorded; `> Status: done`. This PR (#98) isn't recorded
  here because it spans several specs.
- **Test data.** Local database only: a fictional customer (Birchline Bike Repair LLC, owner Ada Birch,
  `qa.flat.<timestamp>@example.test`) with GL-2026-0018, issued from Nov 28 and cancelled from Nov 28.
