# References for Several policies per customer, and rewrite

## Related Specs

- **[Related]** [2026-11-22-issue-policy](../2026-11-22-issue-policy/spec.md): it owns `/me` (the policyholder home) and the issue flow, plus its foundation-wide globs. This spec turns `/me` into a picker when there are two or more policies, moves the home to `/me/policies/:id`, and adds an existing-customer path to `IssuePolicyService`. Kept separate because issue-policy is done, and this is a new capability on top of it.
- **[Related]** [2026-11-22-collect-premium](../2026-11-22-collect-premium/spec.md): it owns `/api/me/payments` and `/me/pay`. This spec makes the payment take a `policyId`, checked for ownership. The webhook and settlement are unchanged.
- **[Related]** [2026-11-22-cancel-refund](../2026-11-22-cancel-refund/spec.md): a flat cancellation (cancel on the start day) is the first half of a rewrite and needs no change. `/me/cancellation` moves under `/me/policies/:id/`.
- **[Related]** statement-history specs (statement, cover-timeline, emails): their `/api/me/statement` and `/api/me/timeline` routes move under `/api/me/policies/{id}/`, and email links become per-policy.

## Upstream Meeting

None. The sources are `ASSIGNMENT.md` ("broker … corrects mistakes") and the 2026-11-23 conversation. That conversation looked at how carriers do it: a bound policy's start date isn't edited, and the fix is flat cancel + rewrite (Guidewire policy-change reversal; the NFIP rule in 44 CFR 62.5; EZLynx/AmTrust "Cancel Flat / Rewrite Full Term").

## Visuals

No design exists. Extend these:
- `design/plainly/P1-Home.dc.html`: the card style for the policies overview.
- `B2-NewPolicy.dc.html`: customer mode.
- `B3-Workspace.dc.html`: the actions row and replaced links.
- `B1-Policies.dc.html`: the status pill.

## Similar Implementations

### Issue
- **Location:** `api/src/main/java/app/plainly/policy/IssuePolicyService.java` (~:80-101 customer insert, then the policy/transaction/ledger/event steps; :121-141 validate), `BrokerIssueController.java`, `IssuePlan.java`
- **Web:** `web/src/pages/broker/NewPolicyPage.tsx`

### Single-policy reads to change
- `me/MyCoverService.java:114`, `statement/StatementService.java:58` (`forPolicy` exists), `timeline/TimelineService.java:41` (`forPolicy` exists), `billing/PaymentService.java:75`, `policy/PolicyRepository.java:96-106`
- `billing/PaymentRepository.findOwned` and `latestCardOnFile` already work across policies.

### Policyholder web
- `web/src/App.tsx:49-88`; `pages/me/HomePage.tsx`, `PayPage.tsx`, `PaymentProgress.tsx`, `StatementPage.tsx`, `TimelinePage.tsx`, `CancellationPage.tsx`
- Query keys `['my-cover']`, `['my-statement']`, `['my-timeline']`.

### Emails
- `notify/EmailTemplates.java` (links to `/me/statement`, `/signup`), `notify/NotifyService.java`.

### Tests
- `api/src/test/java/app/plainly/me/MyCoverTest.java`, `PaymentTestData`, `IntegrationTest`; web `lib/*Copy.test.ts`.
