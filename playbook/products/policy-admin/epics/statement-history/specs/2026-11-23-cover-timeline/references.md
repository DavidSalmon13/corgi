# References for Cover timeline for the policyholder

## Related Specs

- **[Considered]** [policy-lifecycle / 2026-11-22-issue-policy](../../../policy-lifecycle/specs/2026-11-22-issue-policy/spec.md): matched on `/me` (prefix of `/me/timeline`) and `web/**` (glob over `TimelinePage.tsx`, `timelineCopy.ts`). Dismissed in Step 4d because that is the bootstrap spec's broad claim, and P2 is a new page it never built.
- **[Related]** [2026-11-22-statement](../2026-11-22-statement/spec.md): the timeline takes its money from `StatementService` (one calculation for every screen). It reuses the `HowWeGotThis` drawer and deep-links to the statement's correction cards (`#correction-PTX-…`).

## Upstream Meeting

None. The sources are the epic's product brief and tech plan, and `design/plainly/README.md`.

## Similar Implementations

### CoverTimeline component

- **Location:** `web/src/components/CoverTimeline.tsx`, `web/src/lib/timeline.ts`
- **Relevance:** P1 Home already draws a `compact` timeline with it, and B3 a `full` one, with bars, the stripes after a cancellation and a today line.
- **Key patterns:**
  - Use the `positionPct`, `spanPct`, `monthTicks` and `pct` helpers.
  - The props already take `cancelledFrom`, which cuts every cover at the cancellation day.
  - The bars are `aria-hidden` and the component has no selection yet, so P2 needs a new `select` variant.

### My cover API

- **Location:** `api/src/main/java/app/plainly/me/MyCoverController.java`, `MyCoverService.java`
- **Relevance:** `/api/me/cover` already returns each cover (`PolicyViews.Coverage`: from, to, annual cents, `previousStart`) and the cancellation.
- **Key patterns:**
  - It finds the signed-in user's policy with `@RequestAttribute(AuthFilter.CURRENT_USER) SignedInUser` and `PolicyService.latestForCustomer`.
  - The service calls only other services.

### Statement API

- **Location:** `api/src/main/java/app/plainly/statement/` (`MyStatementController`, `StatementService`, `Statement`)
- **Relevance:** the source of the money.
  - `Statement.corrections[]` carries recordedOn, oldStart, newStart, dayDelta, perDayCents, reason and the PTX id.
  - `Row.trace.maths` carries annual × days ÷ term over from–to.
  - `Row.undoneOn` marks lines that a later change undid.
- **Key patterns:** a read-only service built on other services; `forOwner(me)` / `forPolicy(id)`; `NotFoundException` gives a 404. Tests: `StatementServiceTest`, `StatementEndpointTest` and `MyCoverTest` (MockMvc, `CorrectionTestData`).

### Drawer and copy modules

- **Location:** `web/src/components/HowWeGotThis.tsx`, `web/src/lib/statementCopy.ts`, `web/src/lib/myCoverCopy.ts`
- **Relevance:** the "Charged by the day" amount opens the same drawer as the statement.
- **Key patterns:** copy lives in pure functions, tested with exact `toBe` strings and tagged `// covers AC…, T…`. `coverDates`/`periodLine` and `formatCents`/`formatDate` are reused.

### Routes and deep links

- **Location:** `web/src/App.tsx` (flat `/me/*` routes in `RequireRole role="POLICYHOLDER"`), `web/src/pages/me/HomePage.tsx` (the More nav row), `web/src/pages/me/StatementPage.tsx` (correction cards with `id="correction-<PTX>"`; nothing reads the hash yet)
