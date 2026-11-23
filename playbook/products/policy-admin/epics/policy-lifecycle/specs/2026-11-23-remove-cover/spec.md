# Remove extra cover mid-term

> Epic: ../../epic.md
> Status: shaped
> Branch: feat/policy-lifecycle/remove-cover
> Covers: /api/broker/policies/{id}/cover-removal, /api/broker/policies/{id}/cover-removal/preview, api/src/main/java/**/policy/RemoveCover*.java, api/src/main/resources/db/migration/V12__cover_removal.sql, web/src/pages/broker/RemoveCoverForm.tsx, web/src/lib/removeCoverCopy.ts

## Overview

A broker takes the extra cover off a policy from a chosen day: a mid-term endorsement that removes cover. Nothing already on the ledger is edited. A REMOVE_COVERAGE policy transaction posts one CREDIT line for the extra cover's unused days. It is priced by the day, the same way a cancellation credits a cover (annual price × unused days ÷ days in term, one half-up rounding). The credit first pays off anything still owed. The rest is a return premium sent to the customer's card through Stripe: latest payment first, and settled only by a signed webhook. The broker's reason is shown to the customer word for word.

This serves the brief's "broker manages policies and corrects mistakes" and the epic's success criteria:
- every confirm screen previews the money effect;
- no ledger row is ever edited.

It reuses cancel-refund's per-cover credit and correct-effective-date's refund machinery. There are no designs for this flow. The UI extends B4 (a 4th tab, shaped like Cancel), B3, P1, P2, P3 and B5. New copy uses the standard insurance term, wrapped in the glossary/`Term` "?" explanation from `chore/standard-insurance-terms`.

## Goals & User Stories

- As a **broker**, I can remove a policy's extra cover from a chosen day and give the customer's reason. Before I confirm, I see the unused-days credit, anything kept back and the return premium.
- As a **broker**, once extra cover is removed I'm told plainly why it can't be added, corrected or removed again on this policy.
- As a **policyholder**, I see that my extra cover ends on that day, why, what's coming back to my card (or how much less I owe), and how it was worked out.

## Acceptance Criteria

Worked example: a GL policy runs Sep 1, 2026 → Sep 1, 2027 (365 days) at $1,200.00/yr, paid. Extra cover at $730.00/yr starts Oct 8 and is paid ($656.00). Today is Nov 22, 2026. The broker removes the extra cover from Dec 1, 2026, leaving 274 unused days.

1. AC1: On B3, an active policy with live extra cover shows a **Remove extra cover** action that opens B4 on a new **Remove** tab. B4's tabs read Add cover · Correct · Cancel · Remove. A blocked Remove tab names its reason:
   - "This policy is cancelled."
   - "This policy has no extra cover to remove."
   - "The extra cover was removed on Dec 1, 2026."
   - "A payment is waiting for the bank. Remove this once it settles."

2. AC2: The Remove form has a **Cover ends on** date, with the hint "Extra cover stops at the start of this day. No fee." It also has a required **Reason** (at most 500 characters) with a note that the customer sees it word for word. The date must be on or after the extra cover's start, and before the policy ends. Each error is a field message.

3. AC3: The preview runs the same code path as save and writes nothing. For Dec 1 it shows:
   - "Extra cover, unused days · $730.00 × 274 ÷ 365 · −$548.00"
   - "Unpaid balance kept back · … · +$x", only when something is owed
   - a "Return premium to card ending 4242 · Sent through Stripe when you confirm · $548.00" line with its pieces per payment, or the balance after when nothing is paid
   - the "Customer will see" sentence
   - the save label "Remove extra cover and refund $548.00" (or "Remove extra cover" when nothing goes back), plus a **Keep the extra cover** button

4. AC4: Saving is one transaction, under the policy row lock, with the cover read after the lock:
   - it inserts a REMOVE_COVERAGE transaction (effective date, broker, reason);
   - it posts one CREDIT line traced to that transaction and the cover (`ledger_credit_traced`);
   - it sets `coverages.ended_on`;
   - it cancels open payment intents first, and is refused while a payment is processing;
   - it records refunds PENDING and sends them after commit with key `refund-<id>-<attempt>`.

   The REFUND line is posted only by Stripe's webhook. Removing on the cover's start day credits the whole cover charge to the cent.

5. AC5: After a removal, Add cover says "Extra cover was removed from this policy on Dec 1, 2026, so it can't be added again." Correct and Remove are blocked with a plain message. The API enforces the same rules (422 under `policy`).

6. AC6: A later cancellation credits the removed extra cover only up to its `ended_on` (nothing when the cancel day is on or after it), so no day is credited twice.

7. AC7: What the policyholder and broker see:
   - P1 explains the change: a refund on its way, or the new amount due "because your extra cover was removed". It shows the broker's note.
   - P2 ends the extra cover's bar on Dec 1.
   - P3 and B5 show the CREDIT line titled as a removal (not "refunded"), and "How we got this" names the removal transaction.
   - The broker's policy list shows the last action as "Removed extra cover".
   - The refund receipt email gives the removal as the reason, not a moved start date.

8. AC8: All new copy uses the standard term (Endorsement, Return premium, Pro-rata, Effective date) with the "?" explanation, and `jargon.test.ts` passes.

## Technical Approach

- **Schema (`V12__cover_removal.sql`):**
  - `coverages.ended_on date null`, with a check `ended_on is null or (ended_on >= effective_from and ended_on < effective_to)`.
  - Drop and re-add the `policy_transactions` type check with `REMOVE_COVERAGE`. Confirm its generated name first, as V9 did for the entry types.
- **Repository:** `CoverageRepository.markEnded(id, day)` guarded by `ended_on is null`. Add `endedOn` to `Live` and to `PolicyViews.Coverage`.
- **Plan (pure):** `RemoveCoverPlan` uses `CancelPolicyPlan.credits(term, [extraCover], day)`. It reuses the kept-back, balance-after and still-owed logic and `soleCauseOfBalance`. Refunds come from `RefundService.plan(policyId, balanceAfter)`.
- **Service:** `RemoveCoverService.preview/remove`, mirroring `CancelPolicyService`:
  - `load(lock)` through `PolicyRepository.findTerm`, then `findLive(PRODUCT_LIABILITY)` after the lock;
  - validate: the policy problem alone under `policy`, field errors in form order;
  - `payments.cancelOpenIntents`, then insert the transaction, post the CREDIT, `markEnded`, and `refunds.recordAndSendAfterCommit`.
- **Controller:** `RemoveCoverController` handles `POST /api/broker/policies/{id}/cover-removal` and `/preview` with `{endDate, reason}`. It returns 422 field maps and 201; a policyholder gets 403.
- **Ripple:**
  - **Credits and blocks:** `CancelPolicyPlan.credits` ends each cover at `coalesce(endedOn, to)`. `AddCoverageService` and `CorrectCoverageService` get the removed-cover messages.
  - **Policyholder reads:** `TimelineService` sets `keptTo = min(cancelledOn, endedOn)` per cover. `MyCoverService.DueReason.COVER_REMOVED` shows the broker note.
  - **Refund email:** `EmailTemplates.RefundReason.COVER_REMOVED`, mapped in `NotifyService`.
- **Web:**
  - **Types and copy:** `types.ts` gets the new transaction type and `KeptBackFor` value, then `cancelCopy` maps, `statementCopy.changeWhat`, `historyCopy`, `policyCopy.ACTIONS`, `myCoverCopy.dueReasonText`, and the removal CREDIT title in `ledgerRows.ts`. New `lib/removeCoverCopy.ts`.
  - **Form:** `RemoveCoverForm.tsx` is modelled on `CancelPolicyForm.tsx`: debounced preview, confirm dialog, `validationOf`, saved banner.
  - **Pages:** `ChangePolicyPage` gets the `remove` tab and `blockedReason`. `PolicyWorkspace` gets the action and the ended-cover state. `CoverTimeline` shows the ended bar.

## Out of Scope

- Adding extra cover again after it was removed (once per policy).
- Undoing a removal, or changing its date after saving.
- Removing the base cover; that is a cancellation.
- Any fee for removing cover (no short-rate).
- Removing cover while a payment is with the bank; it is blocked until the payment settles.

## Standards Applied

- [global/tech-stack](../../../../../../standards/global/tech-stack.md): Spring Boot 4 / JdbcClient, Flyway, React + Vite + Tailwind v4, Vitest, Stripe test mode.
- [backend/layers](../../../../../../standards/backend/layers.md): controller → service → repository; only repositories run SQL; cross-module calls go service to service.
- [global/git-workflow](../../../../../../standards/global/git-workflow.md): branch naming, commits, PRs of at most 10 files.

## Changelog

| Date | Author | Type | Change | Ref |
|---|---|---|---|---|
| 2026-11-23 | david-schwartzman | created | Initial shaping | — |
