# References for Landing page for guests

## Related Specs

- **[Related]** [2026-11-22-real-sign-in](../2026-11-22-real-sign-in/spec.md): T5 adds a `?demo=` prefill to its `web/src/pages/SignIn.tsx`. Kept separate because the sign-in flow itself is unchanged; the landing page only links into it.
- **[Considered]** [2026-11-22-issue-policy](../2026-11-22-issue-policy/spec.md): matched only on its scaffold glob `web/**`, dismissed in Step 4d. That glob dates from building the web app; the spec never owned a public page or the `/` route.

- **[Related]** [2026-11-22-real-sign-in](../2026-11-22-real-sign-in/spec.md) also owns `auth/**`. T9 adds one open prefix (`/api/public`) to its `AuthFilter`, and the auth rules for every other route stay the same.

## Similar Implementations

### Cover catalog

- **Location:** `api/src/main/java/app/plainly/policy/CoverKind.java`
- **Relevance:** the single source of names and annual premiums ($1,200 / $730) the "What we offer" list shows.

### Auth shell

- **Location:** `web/src/components/AuthLayout.tsx`, `web/src/components/Mark.tsx`, `web/src/components/ThemeToggle.tsx`
- **Relevance:** the public look already shipped: the demo badge, mark, wordmark, tagline, test-mode footer and theme toggle.
- **Key patterns:** a `max-w-[430px]` column on `bg-bg`; Tailwind tokens from `src/theme.css`.

### Sign-in demo box

- **Location:** `web/src/pages/SignIn.tsx` (`DEMO_ACCOUNTS`, `DEMO_PASSWORD`, `fillDemo`)
- **Relevance:** the reviewer box reuses the same account list; `?demo=` reuses `fillDemo`'s behaviour.

### Glossary terms

- **Location:** `web/src/components/Term.tsx`, `web/src/components/Glossed.tsx`, `web/src/lib/glossary.ts`, `web/src/jargon.test.ts`
- **Relevance:** every insurance word on the page carries its "?" explanation; the jargon scan fails on home-made substitutes or unexplained terms.

### Copy modules

- **Location:** `web/src/lib/*Copy.ts` with matching `*.test.ts` (e.g. `policiesCopy.ts`)
- **Relevance:** page wording lives in a tested copy module, not inline in JSX.

### Design screens

- **Location:** `design/plainly/SignIn.dc.html`, `design/plainly/P1-Home.dc.html`, `design/plainly/README.md`
- **Relevance:** L1 copies their canvas format, tokens and dark toggle; P1 supplies the miniature "Your cover" snippet.
