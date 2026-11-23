# QA Report — Statement clarity: show reversals without striking them, plus a real statement header

> Spec: spec.md
> Date: 2026-11-23 · QA by: david-schwartzman · Mode: setup
> Verdict: PASS WITH NOTES

## Acceptance Criteria

| AC | Criterion | Result | Evidence |
|---|---|---|---|
| AC1 | No line struck through on P3 or B5; reversed line muted with a "Reversed by correction · LE-…" tag that scrolls to its card; footer stays true | pass | `statementCopy.test.ts` "tags a reversed line … never a strikethrough", "points a twice-corrected line at the later correction"; `ledgerRows.test.ts` "tags a line that was reversed". Live: 0 elements with `line-through` on P3 and B5 at both sizes; LE-0065 tagged "Reversed by correction · LE-0067" and LE-0068 "· LE-0070"; clicking the tag set `#correction-PTX-0040` and the card was in view; footer "We never edit a line: a mistake gets a reversal and a rebill" shown |
| AC2 | Repeated original in a card labelled as a reference, no balance, visibly not counted | pass | `statementCopy.test.ts` "labels the repeated original inside a card as a reference (AC2)". Live: "REFERENCE · Replaces: Product liability endorsement from Nov 23 · posted Nov 23 above", muted amount, "not counted again", no balance (`StatementPage.tsx` CorrectionCard) |
| AC3 | No per-line balance in cards; one "Balance after" per card, credits included; final balance = ledger balance | pass | `StatementServiceTest.eachCorrectionCardHasItsOwnBalanceAfter`, `StatementEndpointTest.theStatementCarriesItsTermItsDueDateAndEachCardsBalanceAfter`, `statementCopy.test.ts` "gives each card one balance after it, a credit included". Live: card PTX-0040 "Balance after $14.00 credit", PTX-0041 "Balance after $30.00 due"; final balance $30.00 = `sum(ledger_entries.amount_cents)` for the policy (3000) |
| AC4 | "effective <date>" on lines, "reversed, posted <date>" on reversals, never bare "reversed <date>" | pass | `statementCopy.ts` `rowNote`. Live: "$730.00 × 357 ÷ 365 · effective Nov 23 · reversed, posted Nov 23"; "Reversal in full of the line effective Nov 23"; regex for a bare "reversed <Mon> <d>" found no match |
| AC5 | Header: statement date, policy period with day count, "Due <date> · due on receipt"; due = oldest unpaid line's posting date; matches P1 | pass | `StatementServiceTest.theHeaderShowsTheStatementDateAndThePolicyTerm`, `aBalanceDueIsDueOnThePostingDateOfTheOldestUnpaidLine`, `afterACorrectionTheDueDateIsTheCorrectedLinesPostingDate`, `nothingIsDueWhenPaidUpOrInCredit`, `paymentsPayOffTheOldestChargesFirst`, `aLeftOverCreditPaysTheNextChargeFirst`. Live: "Statement date: Nov 23, 2026 / Policy period: Nov 15, 2026 (inception) → Nov 15, 2027 (expiry) · 365 days / Due Nov 23, 2026 · due on receipt"; P1 `/cover` returns `amountDueCents: 3000`, the same $30.00 |
| AC6 | Refund row reads "Refund to you"; amount and sign unchanged | pass | `statementCopy.ts` `ROW_STATUS`. Live (real Stripe test-mode refund RF-0004): "Refund to card ending 4242 · Refund to you · sent · banks take 5–10 days to show it", +$14.00 |
| AC7 | $1,000/yr cover corrected 7 days: no stray cent, reversal = −original, net = rebill − original, no "× $/day" when not whole cents | pass | `StatementServiceTest.aThousandDollarCoverCorrectedBySevenDaysLeavesNoStrayCent`; `statementCopy.test.ts` "leaves out the per-day price for a $1,000.00 cover" |
| AC8 | Jargon test passes, new terms wrapped; light + dark at 390 and 1280 | pass | `web/src/jargon.test.ts` passes; new glossary terms `inceptionDate`, `expiryDate`, `statementDate`, `postingDate`, `dueOnReceipt` (`glossary.ts`), each shown with its "?". Screenshots: P3 and B5 at 1280 light and 390 dark, no horizontal scroll, dark background rgb(21,24,31) |

## Checks Run

- build: `cd web && npm run build` (tsc --noEmit + vite build) — pass
- lint: no lint script in `web/` or `api/` — n/a
- typecheck: included in `npm run build` — pass
- tests: `cd api && ./gradlew test` — pass (536 passed, 0 failed); `cd web && npm test` — pass (358 passed, 23 files)
- runtime: the API built from this branch on :8081 (with Stripe test mode and `stripe listen`, emails off) against the local DB, the web dev server on :5173 with `/api` routed to :8081, headless Chromium (Playwright). The review's scenario was replayed on fictional policy GL-2026-0016 ("QA Clarity Ovens LLC"): inception Nov 15; product liability added from Nov 23; paid $1,914.00 with the 4242 test card; corrected Nov 23 → Nov 30 (−$14.00, $14.00 refunded through Stripe); corrected Nov 30 → Nov 15 (+$30.00 due).

## Issues Found

(none)

## Notes

- **"Balance after" sits at the top of each card (low, not a defect).** It is under the card's title, above the lines it follows. The value is right, but a reader who opens the details sees the result before the lines. Consider moving it under the net-change band.
- **Header wrap at 1280 (cosmetic).** "· 365 days" wraps onto a second line in the narrow receipt column.
- **P1 "Due now" has no date.** P1 shows "$30.00 due" without a date, so AC5's match was checked on the amount: the header's due date is today's posting date, which agrees with "due on receipt".
- **Scenario differs slightly from the review.** Local GL-2026-0002 holds a different history, so the scenario was rebuilt on GL-2026-0016. The mid-card dip there was −$714.00, not the review's −$678.00, with the same shape. A first attempt, GL-2026-0015 (inception Nov 23), couldn't take the Nov 15 correction because it falls before inception. Both QA policies stay in the local DB.
- **Hygiene.** T1–T7 are done; T8 is ticked with this report. No PR yet, so the next step is the pr skill. spec.md `> Status: shaped` should move on when the PR opens.
