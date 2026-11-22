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
