# References for Add coverage

## Related Specs

- **[Considered]** [2026-11-22-issue-policy](../2026-11-22-issue-policy/spec.md): matched on its broad `api/src/main/java/**/policy/**`, `web/**` and `/broker/policies/:id` covers; dismissed in Step 4d. Issuing a policy and adding cover mid-term are different actions; this spec reuses issue-policy's pricing, ledger and B3 but owns the new endpoints and B4 page.
- **[Related]** [2026-11-22-collect-premium](../2026-11-22-collect-premium/spec.md): no overlap in covers. It is built in parallel and shares `MyCoverController`, `myCoverCopy.ts`, `types.ts`, `ledgerRows.ts` and `PolicyWorkspace.tsx` edits. This spec adds no migration, so there is no Flyway numbering clash with its V6.

## Upstream Meeting

None.

## Similar Implementations

### Issue a policy (`api/.../policy/IssuePolicyService.java`, `BrokerIssueController.java`)
- **Relevance:** the same preview-then-commit shape for a policy transaction.
- **Key patterns:**
  - `plan()` is shared by preview and issue.
  - `ValidationException` → 422 field map.
  - `NewEntry.charge` + `LedgerPostingService.post` in the caller's transaction.
  - String dates parsed to field errors.

### Pro-rata pricing (`api/.../pricing/ProRata.java`, `policy/CoverKind.java`)
- **Relevance:** `charge(annual, from, to, termStart, termEnd)`, end-exclusive, rounded half-up once; `PRODUCT_LIABILITY` = 73,000 cents.

### Live price preview (`web/src/pages/broker/NewPolicyPage.tsx`)
- **Relevance:** the B2 form, with the #17 fixes.
- **Key patterns:** debounce primitives; the `repricing` guard; the preview-failed retry; focus on the summary once per failed save; `validationOf()`.

### Cover timeline (`web/src/components/CoverTimeline.tsx`, `lib/timeline.ts`)
- **Relevance:** it already draws a mid-term extra-cover bar from `coverages`.

### Visuals
- `design/plainly/B4-ChangePreview.dc.html` (Add cover tab)
- `B3-Workspace.dc.html` (Add cover button)
- `P1-Home.dc.html` and `P2-Timeline.dc.html`
