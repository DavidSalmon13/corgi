# QA Report — Cover timeline for the policyholder

> Spec: spec.md
> Date: 2026-11-23 · QA by: david-schwartzman · Mode: setup
> Verdict: PASS WITH NOTES

## Acceptance Criteria

| AC | Criterion | Result | Evidence |
|---|---|---|---|
| AC1 | Getting there, back link, API roles | pass | `TimelineEndpointTest` (200 policyholder, 403 broker, 404 no policy). Live: broker `GET /api/me/timeline` → 403. P1 "See your cover timeline" → `/me/timeline`, "‹ Your cover" → `/me` (Playwright). No-policy page seen at 390 px. Compact bars link to P2 (`HomePage.tsx`, `Link tabIndex={-1}`). |
| AC2 | One plain answer per state | pass | `timelineCopy.test.ts`. Live, in order: "You’re covered for a full year, Sep 1, 2026 to Sep 1, 2027." → "…from Oct 15." → "…from Oct 8." → "Your cover ended on Dec 1, 2026." |
| AC3 | Bands, month marks, term dates, today line | pass | `CoverTimeline` select variant (`timeline.test.ts` for the helpers). Screenshots show base first, S–A ticks, "Sep 1, 2026 / Sep 1, 2027" and "Today, Nov 23". |
| AC4 | Old-start outline, correction sentence, link to the statement card | pass | Live PTX-0023: "Before the fix: from Oct 15" and "On Nov 23 we moved your extra cover’s start a week earlier, from Oct 15 to Oct 8. The reason: …". "See what it changed" → `/me/statement#correction-PTX-0023`: card found, scrolled to (top 238 px), focused, details open. After three corrections, the latest per cover is drawn with its sentence (the "each cover" reading, confirmed by the user). |
| AC5 | Cancellation: bands end, rest striped, note | pass | Live cancel Dec 1 (PTX-0026): both bands end Dec 1, the rest is hatched, and the note reads "Striped: days after your cover ended on Dec 1. You got those back." |
| AC6 | Bands are buttons; detail card | pass | Live: tap Base → card "Base cover"; focus Extra + Enter → card "Extra cover: products you sell"; `aria-pressed` follows. Extra selected by default; base when alone. "$730.00 a year ($2.00 a day)", "$1,200.00 a year", "Dec 1, 2026 (cancelled)". |
| AC7 | Money is the statement's money | pass | `TimelineServiceTest`. Live: extra 328 days · $656.00; cancelled base 91 days kept · $299.18, extra 54 days kept · $108.00. Sum of covers = statement's settled, not-undone charges and credits: 185,600 = 185,600 (corrected) and 40,718 = 40,718 (cancelled). The drawer opens with the same maths and references ("$730.00 a year × 328 days ÷ 365 = $656.00", PTX/LE refs). |
| AC8 | Terms explained; jargon test; 390 px light/dark; desktop | pass | `Term` on "Starts on", "Price of your cover", "Charged by the day" (`TimelinePage.tsx`). `jargon.test.ts` passes. scrollWidth 390 at 390 px in light and dark; centred 480 px column at 1280 px. |
| AC9 | Reload shows the new state; no cross-session cache | pass | Live re-correction Oct 8 → Oct 10 (PTX-0024) showed "from Oct 10", 326 days · $652.00 and "2 days later" on the next load. React Query cache only, no persistence. `TimelineServiceTest` re-correction case. |

## Checks Run

- build + typecheck: `npm run build` (`tsc --noEmit && vite build`): pass
- lint: no lint script in `web/` or `api/`: n/a
- tests (web): `npx vitest run`: pass (231 passed)
- tests (api): `./gradlew test`: pass (360 passed, run in T9 with JaCoCo)

## Issues Found

(none)

## Notes

- **AC4 reading:** "each gets its sentence" means each corrected cover gets one sentence, for its latest correction (user's call, 2026-11-23). After Oct 15 → 8 → 10 → 8, the outline reads "Before the fix: from Oct 10".
- **Narrow bands:** on a cancelled policy the extra band is about 15% wide, so its label clips to "Extra c…". This matches P2's design. The detail card and the band's accessible name give the full name.
- **QA data:** the live run created a fictional policy (GL-2026-0011, "Timeline QA Bakery … LLC", owner `qa-…@timeline-qa.test`) in the local dev database, with three corrections and a cancellation.
- **Runtime setup:** the dev API on :8080 predates `/api/me/timeline`. QA ran the branch's API on :8081 and sent the web app's `/api` calls there from Playwright.
- **Hygiene:** T10 is the only open task (ticked with this report). No PR yet.
