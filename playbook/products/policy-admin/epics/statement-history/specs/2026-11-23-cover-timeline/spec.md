# Cover timeline for the policyholder

> Epic: ../../epic.md
> Status: done
> Branch: feat/statement-history/cover-timeline
> Covers: /me/timeline, /api/me/timeline, api/src/main/java/**/timeline/**, web/src/pages/me/TimelinePage.tsx, web/src/lib/timelineCopy.ts

## Overview
The policyholder gets P2 Cover timeline (`/me/timeline`), reached from the timeline on P1 Home.

**What the page shows:**
- A one-line answer at the top, e.g. "You're covered all year, plus extra cover for products you sell from Oct 8."
- Each cover drawn as a band across the policy year, with month marks and a "Today" line.
- After a date correction, a dashed outline of where the cover used to start ("Before the fix: from Oct 15").
- After a cancellation, the days after the end date striped, with a note that the money for them came back.

**Tapping a band** opens a detail card for that cover. The card shows:
- what the cover pays for;
- when it starts and ends;
- its yearly price;
- what was charged by the day ("328 days · $656.00").

That amount opens the same "How we got this" drawer as the statement.

**After a correction**, a sentence says when and why the date moved. It links to that correction's card on the statement.

**Source of the numbers:** the timeline builds on `StatementService`, so its amounts are the statement's amounts to the cent.

**Epic goals it serves:** brief story 2 ("see a timeline of my cover"), and the success criteria for the Oct 15 → Oct 8 scenario, for one set of numbers for both roles, and for matching the design. Design: `design/plainly/P2-Timeline.dc.html`.

## Goals & User Stories
- As a policyholder, I see what I'm covered for and from when to when, at a glance, so I know I'm protected today.
- As a policyholder, I see what changed: a moved start date or a cancellation, drawn on the timeline and not just reported as a number.
- As a policyholder, I tap a cover to see what it pays for, its price, and what I was charged for the days I'm covered, with "how we got this" behind the amount.

## Acceptance Criteria
1. AC1: Getting there and the API.
   - A policyholder reaches `/me/timeline` by tapping the timeline on P1 Home, or a "See your cover timeline" link on P1.
   - A back link "‹ Your cover" returns to `/me`.
   - `GET /api/me/timeline` returns the signed-in policyholder's latest policy.
   - A broker gets 403 from it, and a policyholder with no policy gets 404 and a "no cover yet" page.
2. AC2: The top answer is one plain sentence for each state:
   - base only: "You're covered for a full year, Sep 1, 2026 to Sep 1, 2027."
   - with extra cover: "… plus extra cover for products you sell from Oct 15."
   - after a correction: the corrected date ("from Oct 8").
   - cancelled: "Your cover ended on Dec 1, 2026."
3. AC3: The timeline drawing.
   - One band per cover, base first, positioned by start and end over the policy term, with month marks and a "Today, <date>" line.
   - The term's start and end dates show above the bands.
   - Bands for covers starting in the future still show; today's line sits before them.
4. AC4: After a correction, a dashed outline shows the old start with "Before the fix: from <old date>". Below the card, a sentence explains it: "On <date> we moved your extra cover's start <n days/a week> earlier/later, <reason>."
   - The sentence has a "See what it changed" link to that correction's card on `/me/statement`.
   - The statement scrolls to the card and opens it.
   - With several corrections, the latest per cover is drawn and each gets its sentence.
5. AC5: After a cancellation, every band ends on the cancellation day and the rest of the term is striped. A note reads "Striped: days after your cover ended on <date>. You got those back."
6. AC6: Each band is a button. Tapping it, or pressing Enter or Space, selects it: a ring is drawn and `aria-pressed` is set. The detail card then shows:
   - the cover's name and what it pays for;
   - "Starts on", "Ends" ("Dec 1, 2026 (cancelled)" when cancelled), "Price of your cover" ("$730.00 a year ($2.00 a day)" when that is a whole number of cents);
   - "Charged by the day": days kept · amount.

   The extra cover is selected by default when there is one, and the base cover otherwise.
7. AC7: The money is the statement's money.
   - "Charged by the day" is the sum of that cover's settled lines still in force on the statement: the charge, less any cancellation credit, with undone lines excluded. "Days kept" is the days from start to end or to the cancellation day.
   - Trial scenario: extra cover 328 days · $656.00. Cancelled on Dec 1: base 91 days kept · $299.18, extra 54 days kept · $108.00.
   - The amount opens the "How we got this" drawer for that cover's line(s), with the same maths and references as the statement.
   - The sum of all covers' amounts equals the total of the statement's settled charges and credits for those covers.
8. AC8: Every insurance term on the page has a plain explanation (`Term`/abbr: "Starts on" = effective date, "Price of your cover" = premium, "Charged by the day" = pro-rata), and the wording passes `web/src/jargon.test.ts`. The page matches P2 in light and dark at 390 px with no horizontal scroll, and on desktop as a centred column like the other /me pages.
9. AC9: After the broker corrects or cancels the policy, reloading `/me/timeline` shows the new state. Nothing is cached across sessions.

## Technical Approach
- **API: new module `app.plainly.timeline`**, layered controller → service → repository:
  - **`MyTimelineController`** maps `GET /api/me/timeline` to `TimelineService.forOwner(SignedInUser)`.
    - Roles come from the `/api/me` prefix in `AuthFilter`.
    - A missing policy throws `NotFoundException`, which returns 404.
  - **`TimelineService`** is read-only and calls only other services (no repository of its own, so no SQL). It gets the policy with `PolicyService.latestForCustomer` and the statement with `StatementService.forPolicy`.
    - **Term:** start, end, `cancelledOn` and today.
    - **Covers:** `PolicyViews.Coverage` (kind, from, to, annual cents, `previousStart`).
    - **Correction per cover:** `Statement.corrections`, matched to the cover's kind and new start. It gives recordedOn, oldStart, dayDelta and reason, plus the PTX id for the link.
    - **Charged by the day:** the statement rows for the cover's lines still in force. These are the CHARGE/CREDIT rows that are not pending, have `undoneOn == null` and match the cover by `trace.maths.from/to` against the cover's dates. If matching by dates is ambiguous, add `coverageId` to `Statement.Row` (additive; the statement tests stay green).
    - **Response:** a `Timeline` record: `term`, `covers[] {id, kind, from, to, previousStart, annualCents, perDayCents, daysKept, chargedCents, rows[]}`, `corrections[]` and `cancellation`. `rows` reuses `Statement.Row`, so the web drawer works unchanged.
  - Nothing is written, there is no schema change and there is no Flyway migration.
- **Web:**
  - **Types:** `api/types.ts` gets the `Timeline` types.
  - **`lib/timelineCopy.ts`** (pure, TDD): the top answer for each state, cover names and "what it pays for", the correction sentence ("a week earlier" when 7 days), "Charged by the day" text, the cancelled-end wording and the striped note. It reuses `dates.ts`, `money.ts` and `myCoverCopy`.
  - **`components/CoverTimeline.tsx`** gets a new `variant: 'select'` (P1 `compact` and B3 `full` stay as they are). It adds:
    - band buttons with `selectedId`/`onSelect`;
    - a ghost row from `previousStart`;
    - an accessible label for each band.

    It reuses the `positionPct`/`spanPct`/`monthTicks` helpers in `lib/timeline.ts`.
  - **`pages/me/TimelinePage.tsx`**: the header with "‹ Your cover", the answer, the timeline card, the detail card with `HowWeGotThis` on the amount, and the correction sentence(s).
  - **Route:** `/me/timeline` in `App.tsx`, inside `RequireRole role="POLICYHOLDER"`.
  - **P1 Home:** the compact timeline becomes a link to `/me/timeline`, and a "Cover timeline" row is added in the More nav.
  - **`StatementPage`:** reads `location.hash` (`#correction-PTX-…`), scrolls to that correction card and opens it.

## Out of Scope
- A broker version of P2 (B3 keeps its full timeline; B5 History covers the broker).
- Editing cover from the timeline, or showing cover from previous policy terms.
- Emails (`emails` spec).
- Changing the statement's wording or the P1/B3 timeline visuals beyond the link.

## Standards Applied
- [backend/layers](../../../../../../standards/backend/layers.md): new controller and service in `timeline`, calling `PolicyService`/`StatementService` service-to-service. No new SQL.
- [global/tech-stack](../../../../../../standards/global/tech-stack.md): React 18 + Vite + Tailwind v4; Spring Boot 4 / Java 21.
- [global/git-workflow](../../../../../../standards/global/git-workflow.md): branch and commit conventions.

## Changelog
| Date | Author | Type | Change | Ref |
|---|---|---|---|---|
| 2026-11-23 | david-schwartzman | created | Initial shaping | — |
| 2026-11-23 | david-schwartzman | change | Status set to done: all tasks done and merged into dev (PR #57-61) | — |
