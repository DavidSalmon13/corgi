# Add coverage

> Epic: ../../epic.md
> Status: shaped
> Branch: feat/policy-lifecycle/add-coverage
> Covers: /broker/policies/:id/change, /api/broker/policies/{id}/coverages, /api/broker/policies/{id}/coverages/preview, api/src/main/java/**/policy/AddCoverage*.java, web/src/pages/broker/ChangePolicyPage.tsx, web/src/lib/addCoverCopy.ts

## Overview

A broker can add the $730.00-a-year product liability extra cover to an active policy part-way through its year. Before anything is saved, the B4 "Change this policy" page shows the charge worked out by the day: yearly price × days from the chosen start to the policy's end ÷ days in the policy year, rounded half-up once. Confirming records an `ADD_COVERAGE` policy transaction, a coverage row and one append-only CHARGE ledger line that carries its calculation, all in one database transaction. The policyholder's amount due rises by exactly the previewed amount, and P1 says why in plain words. Paying it is collect-premium's job; this spec only raises the balance through the ledger.

Serves the brief's goal 1, the story "as a broker I can add the $730/yr add-on mid-term and see the pro-rata charge before I confirm", and the success criteria "every confirm screen previews the money effect" and "pro-rata amounts match the hand calculation". It creates the add-on that correct-effective-date later moves in the trial scenario. B4 ships with only the Add cover tab; correct-effective-date and cancel-refund add their tabs.

UI reference: `design/plainly/B4-ChangePreview.dc.html` (Add cover tab), `B3-Workspace.dc.html` (Add cover button), `P1-Home.dc.html` and `P2-Timeline.dc.html` (extra cover shown to the policyholder).

## Goals & User Stories

- As a **broker**, I add extra cover from the policy page, pick its start date, and see exactly what it adds to the customer's bill before I save.
- As a **broker**, I can't add the same cover twice, start it outside the cover period, or change a cancelled policy, and I'm told why in plain words.
- As a **policyholder**, after extra cover is added I see the new amount due, why it's due, and the extra cover on my cover timeline.
- As a **reviewer**, I can trace the new charge from its ledger line to its policy transaction and see the arithmetic on the line.

## Acceptance Criteria

1. AC1: B3 shows an "Add cover" button for an ACTIVE policy without product liability cover; it opens `/broker/policies/:id/change`. The button is not shown when the policy is cancelled or already has the extra cover.
2. AC2: `/broker/policies/:id/change` follows B4's Add cover tab:
   - Back link "‹ {business} · {policy number}", heading "Change this policy", subtitle "Nothing is saved until you've seen what it costs or refunds the customer."
   - Section "Add extra cover": a Cover select with the one option "Product liability ($730.00 a year, $2.00 a day)" and the hint "Pays if something the business sells hurts someone or damages their property."; a "Starts on" date (default: today if inside the cover period, else the policy start) with the hint "Runs to the end of the cover period, {end}."
   - No Correct or Cancel tabs yet.
3. AC3: The money preview ("What this does to {first name}'s bill") updates as the date changes and saves nothing:
   - A line "Extra cover from {Mon d}" with "{yearly} × {days} ÷ {term days} · charged by the day" and the amount as `+$`.
   - "Customer pays" with the same amount.
   - "Customer will see": "You owe {amount} for your new extra cover: {days} days from {Mon d} at {per day} a day." The "at {per day} a day" clause appears only when the yearly price divides evenly by the term days.
   - Example: policy Sep 1, 2026 → Sep 1, 2027, start Oct 15 → 321 days, $730.00 × 321 ÷ 365 = $642.00. Start on the last day → 1 day, $2.00. Start on the policy start → $730.00. A 366-day term uses 366.
4. AC4: Invalid input gets 422 with field errors, shown next to the field and in the summary:
   - No date: "Pick the day the extra cover should start."
   - Before the policy start: "Extra cover can't start before the policy does ({start})."
   - On or after the policy end: "Extra cover has to start before the policy ends ({end})."
   - Already has it: "This policy already has product liability cover."
   - Cover kind other than PRODUCT_LIABILITY: "Pick a cover to add."
   - Cancelled policy: "This policy is cancelled, so cover can't be added."
5. AC5: "Add cover and ask {first name} to pay" saves, in one database transaction:
   - a `policy_transactions` row of type `ADD_COVERAGE` with `effective_date` = the start and `recorded_on` = today;
   - a `coverages` row (PRODUCT_LIABILITY, $730.00, start → policy end);
   - one CHARGE ledger line, "Extra cover from {Mon d}", with its calc columns and cover dates, linked to the coverage and the transaction.
   The balance rises by exactly the previewed amount. If any step fails, nothing is saved.
6. AC6: After saving, the broker lands on B3 with "Extra cover added. {first name} now owes {balance}." B3's timeline shows the extra cover bar from its start, and the ledger shows the new line with its LE and PTX references and its calculation.
7. AC7: The server locks the policy row while adding, so two simultaneous adds produce one coverage and one charge; the other gets the "already has" 422.
8. AC8: The save button is disabled and the preview dimmed while the price is catching up with the inputs. If the preview fails for a non-validation reason, it says "We couldn't work out the price just now." with "Try again". After a failed save, focus moves to the error summary once.
9. AC9: P1 for the policyholder:
   - The amount due includes the new charge, with the reason "Your new extra cover, charged by the day from {Mon d}." when the latest money change is the extra cover.
   - The extra cover is listed ("Extra cover: products you sell", "from {Mon d}") and drawn on the timeline.
   - The protection sentence mentions things the business sells.
10. AC10: B1 shows the policy's last action as "Added extra cover · {date}" and counts it as owing money.
11. AC11: Only brokers can preview or add cover (policyholders get 403). An unknown policy id gets 404.
12. AC12: No unexplained jargon: "charged by the day" explains pro-rata ("yearly price × days ÷ days in the policy year"). The automated jargon check also scans the `lib/*Copy.ts` text helpers.

## Technical Approach

**No migration.** V1 already allows `ADD_COVERAGE` transactions, per-coverage `effective_from/to`, and CHARGE lines linked to a coverage and transaction. One extra cover per policy is enforced in the service under a row lock rather than a unique index, because correct-effective-date may later need a second coverage row of the same kind.

**`policy` package**
- `AddCoveragePlan(long policyId, CoverKind kind, String label, LocalDate from, LocalDate to, ProRata.Charge calc, long totalCents)`.
- `AddCoverageService`:
  - `preview(policyId, kind, startDate)`: loads the policy (404 if missing), validates (AC4), returns the plan. Saves nothing.
  - `@Transactional add(policyId, cmd, brokerUserId)`: `select … from policies where id = ? for update`, re-validates against the locked state, plans, inserts the `ADD_COVERAGE` transaction (`effective_date` = start, `recorded_on` = `Today.date()`, `created_by`), inserts the coverage, then `ledger.post(policyId, txId, [NewEntry.charge(today, "Extra cover from " + Mon d, coverageId, calc, from, policyEnd)])`. Returns `Added(policyId, transactionDisplayId, entries, totalCents, balanceCents)`.
  - Pricing is `ProRata.charge(kind.annualPremiumCents(), start, policyEnd, policyStart, policyEnd)`. Preview and add share one `plan()`.
- `AddCoverageController` at `/api/broker/policies/{id}/coverages`: `POST /preview` and `POST ""` (201, `Location: /api/broker/policies/{id}`). The request is `AddCoverageRequest(String kind, String startDate)`; dates are parsed like `BrokerIssueController.date()`, so a bad date becomes a field error. Errors go through the existing `ValidationException` → 422 shape.
- `/api/broker/**` is already broker-only in `AuthFilter`.

**Read models**
- `MyCoverController.DueReason` gains `EXTRA_COVER` when something is due and the newest transaction is `ADD_COVERAGE`. `MyCover` gains `latestChangeFrom` (that transaction's effective date) for the reason line.
- `PolicyViews.Detail` is unchanged; B3 works out whether to show Add cover from `status` and `coverages`.

**Web**
- `types.ts`: `AddCoverRequest`, `AddCoverPlan`, `CoverAdded`; `DueReason` gains `'EXTRA_COVER'`; `MyCover.latestChangeFrom`.
- `lib/addCoverCopy.ts`: the cover option label, calc line, "Customer will see" sentence (per-day clause only when exact), save label, and saved banner.
- `pages/broker/ChangePolicyPage.tsx` (B4, Add tab): fetches `['policy', id]`. Debounced preview `useQuery(['add-cover-preview', id, kind, start])` with `keepPreviousData`. `useMutation` invalidates `['policy', id]` and `['policies']`, then navigates to B3 with the banner in router state. Reuses #17's patterns: debounce primitives, a `repricing` guard on save, the preview-failed message, and focus on the summary once per failed save. If the policy can't take extra cover, it shows that message with a link back.
- `App.tsx`: route `policies/:id/change` under the broker shell.
- `PolicyWorkspace.tsx`: an "Add cover" button (AC1) and the saved banner (AC6).
- `myCoverCopy.ts`: the `EXTRA_COVER` reason text.
- `jargon.test.ts`: widen the glob to `./lib/*Copy.ts` and `./lib/ledgerRows.ts` (the #22 review's MEDIUM).

**Interaction with collect-premium.** This spec only adds a CHARGE line, so the open balance rises. collect-premium's `PaymentService` locks the same policy row and only reuses an OPEN PaymentIntent whose amount still matches, so an add-on during checkout simply leads to a new payment for the new balance. No payment code is touched here.

## Out of Scope

- Correct and Cancel tabs, and changing or removing an add-on (correct-effective-date, cancel-refund).
- Collecting the new amount (collect-premium) and emails (statements epic).
- Other add-on products, a rating engine, or more than one extra cover per policy.
- The P4 "how we got this" drawer for policyholders (statements epic).

## Standards Applied

- [global/git-workflow](../../../../../../standards/global/git-workflow.md): branch naming, small PRs (≤ 10 files), conventional commits.
- [global/tech-stack](../../../../../../standards/global/tech-stack.md): Spring Boot + JdbcTemplate + Flyway, React + TanStack Query + Tailwind, Vitest.

## Changelog

| Date | Author | Type | Change | Ref |
|---|---|---|---|---|
| 2026-11-22 | david-schwartzman | created | Initial shaping | — |
