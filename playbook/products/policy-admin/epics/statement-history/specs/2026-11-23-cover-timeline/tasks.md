# Tasks — Cover timeline for the policyholder

> Epic: statement-history
> Spec: 2026-11-23-cover-timeline
> Branch: feat/statement-history/cover-timeline

## Timeline API
- [x] T1 Failing TimelineServiceTest for AC2–AC5, AC7 and AC9 (testing skill, TDD). Cases:
  - base only, with extra cover, after a correction (Oct 15 → Oct 8: previousStart, dayDelta 7, reason, PTX id) and cancelled on Dec 1;
  - charged by the day: 328 d · $656.00; and 91 d · $299.18 / 54 d · $108.00 when cancelled;
  - the covers' sum equals the statement's settled lines for those covers;
  - a re-correction shows the new state.
- [x] T2 Timeline records + TimelineService.forOwner in app.plainly.timeline, built from PolicyService + StatementService (no repository). Add coverageId to Statement.Row only if matching by dates is ambiguous. T1 passes (service).
- [x] T3 MyTimelineController GET /api/me/timeline; TimelineEndpointTest with 200 for a policyholder, 403 for a broker and 404 with no policy (controller).

## Web
- [x] T4 Timeline types in api/types.ts + lib/timelineCopy.ts, with timelineCopy.test.ts written first. It covers:
  - the answer for each state;
  - cover names and what each pays for;
  - the correction sentence ("a week earlier");
  - "Charged by the day";
  - the cancelled end;
  - the striped note.
- [x] T5 CoverTimeline variant 'select':
  - band buttons with selectedId/onSelect and aria-pressed;
  - a ghost row for previousStart;
  - the hatch after cancellation;
  - today line and month ticks.

  P1 compact and B3 full are unchanged; add timeline.test.ts cases for any new helper.
- [ ] T6 TimelinePage at /me/timeline:
  - App.tsx route under RequireRole POLICYHOLDER, with "‹ Your cover" as the back link;
  - the answer, the timeline card and the detail card, with HowWeGotThis on "Charged by the day";
  - the correction sentence(s) and the no-policy page;
  - light/dark, 390 px.
- [ ] T7 Entry and exit links:
  - P1 Home's compact timeline links to /me/timeline, plus a "Cover timeline" row in More;
  - StatementPage scrolls to #correction-PTX-… and opens that card.

## Docs
- [ ] T8 DECISIONS.md: the timeline reuses StatementService for its money (one calculation), and "charged by the day" counts only in-force settled lines.

## Verification
- [ ] T9 Run both suites with coverage on the changed files (testing skill)
- [ ] T10 Run QA against AC1–AC9, incl. the Oct 15 → Oct 8 scenario, a live re-correction and a cancellation, at 390 px light and dark and on desktop (qa skill)
