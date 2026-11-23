# References for Statement clarity

## Related Specs

- **[Related]** [2026-11-22-statement](../2026-11-22-statement/spec.md) owns `/me/statement`, `StatementPage.tsx`, `statementCopy.ts` and `statement/**`. Those files are refined in place here rather than claimed again. This spec was kept separate because it is a focused follow-up from review feedback, and the original spec is done and merged.
- **[Related]** [2026-11-23-broker-history](../2026-11-23-broker-history/spec.md) shares `LedgerTable` and `ledgerRows`. B5 must keep reading correctly after the strikethrough is removed.

## Upstream Meeting

None. The source is a written review of the statement (Nov 23 → Nov 30 → Nov 15 scenario on GL-2026-0002), discussed in the shaping conversation.

## Similar Implementations

### Statement module
- **Location:** `api/src/main/java/app/plainly/statement/Statement.java`, `StatementService.java`
- **Relevance:** the new header and correction fields go here.
- **Key patterns:** a role-neutral record built from `PolicyService`; pending rows carry the previous balance.

### Ledger row model
- **Location:** `web/src/lib/ledgerRows.ts` (`struck`, `reversedByDisplayId`), `web/src/components/LedgerTable.tsx`
- **Relevance:** where the strikethrough comes from.

### Correction summary
- **Location:** `web/src/lib/statementCopy.ts` `correctionSummary`
- **Relevance:** already omits the per-day price when it isn't whole cents; AC7 pins that.

### Cancellation credits
- **Location:** DECISIONS.md 2026-11-22 "Cancelling posts one CREDIT line per cover"
- **Relevance:** the existing argument against striking charges that were really owed.
