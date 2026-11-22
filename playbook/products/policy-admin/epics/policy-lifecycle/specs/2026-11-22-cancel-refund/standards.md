# Standards for Cancel a policy and refund unused days

The following standards apply to this work.

---

## global/tech-stack

# Tech Stack

The stack as built. Change this file when a dependency is added or swapped, and say why in `DECISIONS.md`.

## Frontend (`web/`)

- React 18 with TypeScript, built with Vite
- Tailwind CSS v4 (through `@tailwindcss/vite`), with design tokens in `src/theme.css`
- React Router 7 for pages, TanStack Query 5 for server data
- Stripe Elements (`@stripe/react-stripe-js`) for the card form; card numbers never touch our servers
- Vitest for tests (`npm test`); `npm run build` type-checks and builds
- In development `/api` is proxied to the API on `:8080`

## Backend (`api/`)

- Java 21 with Spring Boot 4 (Spring MVC), built with Gradle (Kotlin DSL)
- Layered as **controller → service → repository**; only `@Repository` classes run SQL. Read and follow `playbook/standards/backend/layers.md` for any API work
- SQL through Spring's `JdbcClient` inside repositories: plain SQL, no JPA or ORM
- One package per area: `auth`, `policy`, `pricing`, `ledger`, `billing`, `me`, `common`
- Sign-in: server-side sessions with BCrypt hashes (`spring-security-crypto` only, not full Spring Security)
- Payments: `stripe-java`, test mode only; webhooks verified by signature and processed once per event id
- Tests: JUnit 5 with Testcontainers Postgres (`./gradlew test`, Docker must be running)

## Database

- PostgreSQL 16 (`docker compose up -d` locally)
- Flyway migrations in `api/src/main/resources/db/migration` (`V<n>__<what>.sql`), run on API start
- The ledger is append-only, enforced in the database; corrections are reversing lines

## Money

- Integer cents in USD everywhere (Java `long`, TypeScript `number`); format only at the edge of the UI

## Hosting

- Railway; deployment settings are in the README

---

## backend/layers

# Backend layers: controller → service → repository

Every API feature is built in three layers. Each layer has one job, and calls only go downwards.

```
HTTP ──▶ Controller ──▶ Service ──▶ Repository ──▶ Postgres
         (web)          (rules)      (SQL)
```

| Layer | Annotation | Its job | Never |
|---|---|---|---|
| **Controller** (`*Controller`) | `@RestController` | Map HTTP to a service call: parse the path, body and signed-in user; pick the status code; return the service's result. | Run SQL, hold business rules, call a repository, open a transaction. |
| **Service** (`*Service`) | `@Service` | Business rules and validation, pricing, ledger posting, calls to Stripe, and the transaction boundary (`@Transactional`). | Run SQL or touch `JdbcClient`; know about HTTP (`HttpServletRequest`, `ResponseEntity`). |
| **Repository** (`*Repository`) | `@Repository` | The only code that runs SQL. One repository per table it writes (`PaymentRepository` writes `payments`), with methods named after what they do (`lockLatestForCustomer`, `markSucceeded`). Maps rows to records. | Make business decisions, call services or Stripe, decide what an error means to the user, start transactions. |

## Rules

1. **Only repositories touch `JdbcClient`.** A `JdbcClient` field in a controller or service is a review finding. Test fixtures (`TestData`, `PaymentTestData`) may seed with SQL directly.
2. **Controllers call services only.** A controller with a repository field is a review finding, even for a simple read.
3. **Transactions belong to services.** Put `@Transactional` on the service method that has to be all or nothing (issue a policy, add cover, handle a webhook). Repositories join the caller's transaction, so row locks (`... for update`) taken through a repository last until that service method commits.
4. **Across modules, call the other module's service, not its repository.** Billing needs to lock a policy, so it calls `PolicyService.lockLatestForCustomer`, not `PolicyRepository`. Inside one module, a service uses that module's repositories directly.
5. **A repository writes only its own table.** Reads may join other tables when a screen needs a joined view (the ledger lines join payments to show the Stripe intent), but every `insert`/`update` goes through the repository that owns the table.
6. **Plain SQL through `JdbcClient`, no JPA or ORM.** Every write is a visible `insert` or `update`, which keeps the append-only ledger honest (see DECISIONS.md, 2026-11-22 "Plain SQL in repositories, no JPA").
7. **Repositories return records or primitives,** never `ResultSet`s or `Map`s. Small row records live inside the repository (`PaymentRepository.Unfinished`) unless a service exposes them.
8. **Names say the layer.** `XController`, `XService`, `XRepository`. The `ledger` module keeps two services (`LedgerPostingService` for writes, `LedgerQueryService` for reads) over one `LedgerRepository`.

## Package layout

One package per module (`auth`, `policy`, `pricing`, `ledger`, `billing`, `me`, `common`). Each holds its own controllers, services, repositories and records:

```
policy/
  BrokerPolicyController.java   ← HTTP
  IssuePolicyService.java       ← rules + @Transactional
  PolicyRepository.java         ← SQL on policies
  CoverageRepository.java       ← SQL on coverages
  IssuePlan.java, PolicyViews.java  ← records
```

`pricing` is pure arithmetic with no database, so it has no repository.

## Checklist for a new spec

- Each new endpoint has a thin controller method that calls one service method.
- Each new rule or calculation lives in a service (or in `pricing` if it is pure arithmetic) and is tested there.
- Each new SQL statement lives in a repository method, and each new table gets its repository.
- `tasks.md` names the controller, service and repository each task touches.

---

## global/git-workflow

# Git Workflow

## Branch naming

One branch per spec, named from the Playbook slugs (no date prefixes):

```
feat/<epic-slug>/<spec-slug>          # spec work — the canonical branch
feat/<epic-slug>/<spec-slug>--t<id>   # parallel work on one spec, split by task
hotfix/<epic-slug>/<bug-slug>         # bug fixes
chore/<slug>                          # everything else
```

- The canonical branch is recorded in the spec's `tasks.md` `> Branch:` line. To map a branch to a spec: match that line first; fall back to suffix-matching `<spec-slug>` against `feat/*/`.
- Slugs are load-bearing — never rename an epic or spec after branches exist.
- Never commit on the default branch. Branch first.

## Commits

Conventional-commit-ish titles, scoped to the epic:

```
feat(<epic-slug>): add password reset endpoint
fix(<epic-slug>): handle expired reset tokens
chore: bump CI node version
```

- Small, focused commits. Reference task IDs in the body when useful (`T3`, `T4.1`).
- **Never `git commit --no-verify`.** Failing hooks mean fix the cause, not skip the check.
- No force pushes to the default branch.

## Pull requests

- **At most 10 changed files per PR.** Ship a spec in several small PRs (one or a few tasks each) rather than one large one. If a slice would go over 10 files, split it before opening the PR.
- **Start the PR body with one paragraph explaining what the PR does**, in plain words: what changes for the user or the system, and why. Put it above the spec, tasks and acceptance-criteria sections.
- Open PRs with the **pr** skill — it runs verification, builds the title (`feat(<epic-slug>): <spec title>` / `fix(<epic-slug>): <bug title>`) and body from the spec, and writes the PR URL back to `tasks.md`.
- Tasks incomplete → open as draft.

## Production mode

When `playbook/config.yml` has `mode: production`, the workflow tightens:

- **Git itself is never gated.** Commit and push as usual — pushing is how CI runs and how a branch gets reviewed, so blocking it would obstruct the verification the mode is asking for. The gates live in the tools that can actually check something.
- **A PR needs a passing `qa-report.md` and a recorded `> Reviewed:` line** (written by `/code-review`) before the pr skill will open it. A draft PR is for incomplete work, not for skipping verification.
- **Unfixed HIGH or CRITICAL review findings block the merge**, rather than being advice.

`/go-production` makes this transition and records any waivers; `/go-production --revert` goes back. Neither is a quiet config edit — both leave an entry in `playbook/production-readiness.md`.
