# References for Real sign-in

## Related Specs

- **[Related]** [2026-11-22-issue-policy](../2026-11-22-issue-policy/spec.md): overlaps on `/signin`, `api/src/main/java/**/auth/**`, `api/src/main/resources/db/migration/**` and `web/**`. Kept separate because issue-policy built the simulated role switcher and is already in review (PRs #14–#22). This spec replaces that switcher with real sign-in and supersedes issue-policy's AC1.

## Similar Implementations

### Demo auth (being replaced)

- **Location:** `api/src/main/java/app/plainly/auth/` (`DemoAuthFilter`, `DemoSessionController`, `DemoUsers`, `DemoUser`, `Role`)
- **Relevance:** the filter, the cookie and the role rules this spec keeps.
- **Key patterns:** path-prefix role checks (`/api/broker/**`, `/api/me/**`); the signed-in user set as the `currentUser` request attribute; JSON 401/403 bodies `{error, message}`; the cookie built with `ResponseCookie` (httpOnly, SameSite=Lax).

### Issue policy validation

- **Location:** `api/src/main/java/app/plainly/policy/IssuePolicyService.java`
- **Relevance:** where the duplicate owner-email check goes, and where the owner's `app_users` row is created (line ~68).
- **Key patterns:** a `ValidationException(Map<field, message>)` with plain-language messages, which the B2 form shows per field.

### Sign-in screen

- **Location:** `web/src/pages/SignIn.tsx`, `design/plainly/SignIn.dc.html`
- **Relevance:** the layout, tokens and copy that the form keeps.
- **Key patterns:** the demo badge, the Mark and wordmark, the footer note and ThemeToggle; `queryClient.clear()` and `setQueryData(['session'])` on sign-in; `homeFor(role)`.

### Test conventions

- **Location:** `api/src/test/java/app/plainly/IntegrationTest.java`, `TestData.java`, `auth/DemoAuthTest.java`
- **Relevance:** a shared Testcontainers context with no cleanup (append-only), so each test makes its own data. Owner emails must now be unique per test.
