# References for Remove extra cover mid-term

## Related Specs

- **[Related]** [2026-11-22-cancel-refund](../2026-11-22-cancel-refund/spec.md): it owns `policy/Cancel*.java` and the CREDIT line type. This spec reuses `CancelPolicyPlan.credits` for one cover, and changes it to stop at a removed cover's `ended_on` so that a cancellation after a removal credits nothing twice. Kept separate because a cancellation ends the whole policy, while this removes one cover and the policy stays active.
- **[Related]** [2026-11-22-correct-effective-date](../2026-11-22-correct-effective-date/spec.md): it owns `billing/Refund*.java`, refund sending, webhook settlement and retry, all reused unchanged. Correct gains a "removed" block.
- **[Related]** [2026-11-22-add-coverage](../2026-11-22-add-coverage/spec.md): its B4 `ChangePolicyPage.tsx` hosts the new Remove tab. Add cover gains a "removed" block.
- **[Considered]** [2026-11-22-issue-policy](../2026-11-22-issue-policy/spec.md): matched only on its foundation-wide globs (`**/policy/**`, `db/migration/**`, `web/**`). This is a different concern.

## Upstream Meeting

None. The sources are `ASSIGNMENT.md` ("brokers … change coverage during its term") and the 2026-11-23 conversation that chose this feature.

## Visuals

No design exists. Extend these:
- `design/plainly/B4-ChangePreview.dc.html`: the Cancel tab is the template for Remove.
- `B3-Workspace.dc.html`: actions.
- `P1-Home.dc.html`: the "why you owe" and "refund on its way" states.
- `P2-Timeline.dc.html`: the cancelled state's striped unused days show how to draw the ended bar.

## Similar Implementations

### Cancel a policy
- **Location:** `api/src/main/java/app/plainly/policy/CancelPolicyService.java`, `CancelPolicyPlan.java`, `CancellationController.java`
- **Key patterns:**
  - load through `findTerm(id, true)`, then read covers after the lock;
  - validate, then `payments.cancelOpenIntents`, then insert the transaction and `NewEntry.credit`;
  - `refunds.recordAndSendAfterCommit`; `credits(term, covers, day)`; `soleCauseOfBalance`.

### Correct a start date
- **Location:** `policy/CorrectCoverageService.java`
- **Key patterns:** `findLive(PRODUCT_LIABILITY)`; extra-cover-specific policy messages.

### Refunds
- **Location:** `billing/RefundService.java` (`plan`, `recordAndSendAfterCommit`)

### Web
- `web/src/pages/broker/CancelPolicyForm.tsx` (template), `ChangePolicyPage.tsx`, `changeForm.tsx`, `PolicyWorkspace.tsx`, `components/CoverTimeline.tsx`
- `web/src/lib/cancelCopy.ts`, `ledgerRows.ts`, and the glossary/`Glossed` from `chore/standard-insurance-terms`

### Tests
- `api/src/test/java/app/plainly/policy/CancelPolicyServiceTest.java`, `CancellationEndpointsTest.java`; `CorrectionTestData`, `PaymentTestData`, `StripeTestSupport`, `FakeStripeGateway`
- `IntegrationTest` fixes today at 2026-11-22.
