# References for A public landing page

## Related Specs

- **[Related]** [2026-11-22-real-sign-in](../2026-11-22-real-sign-in/spec.md) owns `/signin`, `/signup`, `auth/**` and `client.ts`. This spec adds one open prefix to `AuthFilter`, a wordmark link on each page and one fetch. Ownership stays with `real-sign-in`, and its rules (the broker issues the policy, the owner signs up with that email) are what the page explains.
- **[Related]** [2026-11-22-issue-policy](../2026-11-22-issue-policy/spec.md) claims `web/**` broadly and built `HomeRedirect`. This spec changes only the signed-out branch of `HomeRedirect` and owns the new `/` page.

## Upstream Meeting

None. The source is the request in the shaping conversation: "a simple landing page where you don't need to be logged in to see what policies we offer", with sign in / sign up from there.

## Similar Implementations

### Cover catalog
- **Location:** `api/src/main/java/app/plainly/policy/CoverKind.java`
- **Relevance:** the single source of names and annual premiums ($1,200 / $730).

### Open routes
- **Location:** `api/src/main/java/app/plainly/auth/AuthFilter.java` (the `/api/health` and `/api/auth` exceptions)
- **Relevance:** where `/api/public` is added.

### Signed-out look
- **Location:** `web/src/pages/SignIn.tsx`, `design/plainly/SignIn.dc.html`
- **Relevance:** the wordmark, demo notice and tokens the landing page reuses.

### Terms and money
- **Location:** `web/src/lib/glossary.ts` (`Term`/`Glossed`), `formatCents`, `statementCopy.correctionSummary` (per-day only when exact)
