# Statement clarity: show reversals without striking them, plus a real statement header

> Epic: ../../epic.md
> Status: shaped
> Branch: feat/statement-history/statement-clarity
> Covers: web/src/components/LedgerTable.tsx, web/src/lib/ledgerRows.ts

## Overview

A review of `/me/statement` found places where the screen contradicts its own rule that "we never edit a line":
- A reversed line is struck through, which reads as deleted, yet it still counts in the running balance.
- Inside a correction card, the per-line balance dips to −$678, which reads as "we owe you $678".
- The −$14 correction card never says the customer was in credit.
- "reversed Nov 23" doesn't say whether Nov 23 is the effective date or the posted date.

The statement also lacks what a real one carries: a statement date, the policy term, and a due date for what is owed.

This spec fixes those points on the statement (P3), and on B5 History where it shares the components. It makes no ledger or schema changes; the statement API only gains a few read fields. It refines `2026-11-22-statement`, which still owns `/me/statement`, `StatementPage`, `statementCopy` and the `statement/**` module; this spec changes them in place.

It serves the epic's criteria that every amount is traceable and that the correction scenario shows the original, the reversal and the rebill. It also supports the brief's rule that a policyholder sees what they owe and why, in plain language.

## Goals & User Stories

- As a policyholder, a line that was later reversed still reads as a real charge that counted, with a pointer to the correction that undid it, so the statement agrees with "we never edit a line".
- As a policyholder, I see my balance after each correction, including when I was in credit, and never a misleading balance in the middle of a correction.
- As a policyholder, I can tell when a change took effect and when it was posted.
- As a policyholder, I see when this statement was made, the term my policy covers, and when what I owe is due.

## Acceptance Criteria

1. AC1: No ledger line is struck through, on the statement or on B5 History. A reversed line shows at normal weight, muted, with a `Reversed` tag ("Reversed by correction · LE-…") that scrolls to its correction card. The footer copy stays true.
2. AC2: Each line in a correction card that repeats an earlier line is labelled as a reference ("Replaces: … · posted <date> above"). It has no balance and is visibly not counted.
3. AC3: Correction cards show no per-line running balance. Each card shows one "Balance after" value, which also covers credits: the −$14 card reads "Balance after: $14.00 credit". The statement's final balance still equals `LedgerQueryService.balance` to the cent.
4. AC4: Dates are labelled. Line notes say "effective <date>", and reversal notes say "reversed, posted <date>", never a bare "reversed <date>".
5. AC5: A header shows the statement date (today), the policy term (inception → expiry, plus number of days), and, when a balance is due, "Due <date> · due on receipt". The due date is the posting date of the oldest unpaid line, and it matches P1's "Due now".
6. AC6: A refund row's note reads "Refund to you". Its amount and sign are unchanged.
7. AC7: A $1,000/yr cover corrected by 7 days shows no stray cent. Reversal = −original exactly. Net = rebill − original. The card subtitle omits "× $/day" when the daily rate isn't whole cents. This is a regression test; per the existing rule in `correctionSummary` the behaviour should already hold.
8. AC8: The jargon test (`web/src/jargon.test.ts`) passes, and every new term is wrapped in `Term`. The page renders in light and dark mode at 390 px and 1280 px.

## Technical Approach

- **API (`app.plainly.statement`, read-only, controller → service → repository unchanged):**
  - `Statement.Header` gains `inceptionDate`, `expiryDate` and `statementDate`, plus `dueDate`, which is null when nothing is owed.
  - `StatementService` fills them from the policy dates `PolicyService` already exposes. For `dueDate`, it walks the settled rows to find the posting date of the oldest line not covered by later payments or credits ("due on receipt").
  - Each `Statement.Correction` gains `balanceAfterCents`, the running balance after the card's last line.
  - No schema change and no new repository.
- **Web:**
  - `ledgerRows.ts` replaces `struck: boolean` with `reversedBy` (display id plus a correction anchor). `LedgerTable` drops `line-through` and renders the muted row with a `Reversed` tag linking to the card.
  - `statementCopy.ts` gains the effective/posted labels, the "Replaces" reference label, "Balance after" (due/credit/zero), the header sentences and "Refund to you". Its tests are written first.
  - `StatementPage` renders the header and the card-level balance, and drops the per-line balance inside cards.
  - Types are in `web/src/api/types.ts`.
  - B5 History (`historyCopy.ts`) picks up the new `ledgerRows` shape; it shows no other change.
- **Docs:** `DECISIONS.md` gets a dated entry covering four points:
  - why reversed lines aren't struck through;
  - the due-on-receipt rule;
  - refunds stay immediate despite refund-then-charge churn, with a link to the 2026-11-22 decision;
  - taxes and fees are out of scope.

## Out of Scope

- A "no known loss" confirmation when an effective date is backdated (a follow-up on `correct-effective-date` / `add-coverage`).
- Reason codes on corrections (broker error / insured request / insurer error).
- Holding small credits on account instead of refunding them.
- Tax and fee lines.
- Separate posted-date and effective-date columns. The date groups are by posted date, and each line names its effective date.
- Changing the refund sign convention.

## Standards Applied

- [backend/layers](../../../../../../standards/backend/layers.md): the statement module stays controller → service → repository, and the new fields come through `PolicyService`.
- [global/tech-stack](../../../../../../standards/global/tech-stack.md): React 18 + Vite + Tailwind v4; Spring Boot 4 / Java 21 with JdbcClient.
- [global/git-workflow](../../../../../../standards/global/git-workflow.md): branch and commit conventions.

## Changelog

| Date | Author | Type | Change | Ref |
|---|---|---|---|---|
| 2026-11-23 | david-schwartzman | created | Initial shaping from statement review feedback | — |
