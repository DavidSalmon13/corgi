# Input validation at the API boundary

> Epic: ../../epic.md
> Status: shaped
> Branch: feat/policy-lifecycle/input-validation
> Covers: api/src/main/java/**/common/ApiErrorHandler.java, api/src/main/java/**/common/LenientDate.java, api/src/main/java/**/policy/BrokerIssueController.java, api/src/main/java/**/policy/AddCoverageController.java, api/src/main/java/**/policy/CorrectionController.java, api/src/main/java/**/policy/CancellationController.java, api/src/main/java/**/auth/AuthController.java#SignUpRequest

## Overview
The API rejects badly formed requests at the controller boundary using Bean Validation, before any service runs.
Validation becomes three layers: the shape at the edge (annotations on request records), business rules in services
(unchanged), and invariants in Postgres (already in place; audited, no change). The 422 body and every field message
stay exactly as they are, so the web app doesn't change. No tech plan covers this cross-cutting work; it serves the
epic by making every policy action safe against garbage input.

## Goals & User Stories
- As a broker, when I submit a form with a missing or malformed field, I see the same plain-language message next to
  that field as today, never a generic "bad request".
- As a maintainer, I can read a request record and see what the endpoint accepts, without opening the service.
- As a reviewer, I can see three distinct layers: the shape at the edge, business rules in services, invariants in the database.

## Acceptance Criteria
1. AC1: Issue (+preview), add coverage (+preview), correction (+preview), cancellation (+preview) and sign-up declare
   their rules on the request record (`@NotNull`, `@NotBlank`, `@Email`, `@Size`) and are bound with `@Valid`.
2. AC2: A missing, blank, too-long or wrongly formatted field returns 422
   `{"error":"validation","message":"N things to fix before you can save.","fields":{...}}`, and the service is never called.
3. AC3: Each field message is the same constant the service uses today (e.g. `CorrectCoverageService.NO_START`).
4. AC4: An unreadable date (`"2026-13-45"`, `""`) or an unknown cover kind is a field error on that field, not a 400.
5. AC5: `fields` lists the fields in the order the request record declares them (the form order).
6. AC6: Malformed JSON still returns 400 `bad_request`. Sign-in (which must not reveal which field was wrong) and the
   Stripe webhook (signature + event-id idempotency) are unchanged.
7. AC7: Business rules (email taken, policy cancelled, payment waiting, same start date, refund only after failure) are
   still enforced by the services, with their current messages.
8. AC8: The four private `date()` helpers and the `coverKind()` helper are gone from the controllers.
9. AC9: DECISIONS.md records the three-layer model, the intentional overlap between edge and service checks, and the database audit result.

## Technical Approach
- **Request records** (nested in the controllers) switch to typed fields with annotations whose `message` is the service's
  existing `static final` constant:
  - `IssueRequest`: `@NotBlank @Size(max=MAX_TEXT)` on legalName, businessDescription and ownerName;
    `@NotBlank @Email @Size` on ownerEmail; `@NotNull LocalDate startDate`; `Boolean productLiability` stays optional.
  - `PreviewRequest`: `@NotNull LocalDate startDate`.
  - `AddCoverageRequest`: `@NotNull CoverKind kind`, with
    `@JsonFormat(with = READ_UNKNOWN_ENUM_VALUES_AS_NULL)` so an unknown kind becomes null and then a field error;
    `@NotNull LocalDate startDate`.
  - `CorrectionRequest` / `CancellationRequest`: `@NotNull LocalDate` date; `@NotBlank @Size(max=500)` reason.
  - `SignUpRequest`: `@NotBlank` email and password. Length and "passwords match" stay in `AccountService`.
- **`common/LenientDate`**: a Jackson `ValueDeserializer` that turns blank or unreadable `yyyy-MM-dd` into `null`. It is
  applied per field with `@JsonDeserialize(using = LenientDate.class)`, not globally. Use whichever databind package is on
  the classpath (Spring Boot 4 → Jackson 3, `tools.jackson.databind`).
- **`ApiErrorHandler`**: a new `@ExceptionHandler(MethodArgumentNotValidException.class)` builds the same body through
  `ValidationException`'s message rule, keeping one field per error in the order of `getTarget().getClass().getRecordComponents()`.
  To share the "N things to fix" wording, add a static `ValidationException.messageFor(int)` (or build a
  `ValidationException` and reuse the existing handler).
- **Layers:** controllers only gain annotations and `@Valid`. Services and repositories are untouched. No SQL changes.
- **Trade-off:** format errors now come back before business errors. Example: a blank name plus a taken email returns
  the name error first and the email error on the next submit. This is accepted and recorded in DECISIONS.md.

## Out of Scope
- No new database migration. The audit found the important invariants already enforced (recorded in DECISIONS.md).
- Endpoints still on unmerged branches (remove-cover's removal, more-policies' issue-for-existing-customer) get the same
  pattern once they merge (task T7 runs only for the ones on dev by then).
- No web changes. Sign-in, the Stripe webhook and the refund-retry endpoint (no body) are unchanged.
- No removal of service checks (`DemoSeeder` and future internal callers rely on them).

## Standards Applied
- [backend/layers](../../../../../../standards/backend/layers.md): the edge checks the shape and services own the rules; no SQL outside repositories.
- [global/tech-stack](../../../../../../standards/global/tech-stack.md): Spring Boot 4 / Jackson 3, `spring-boot-starter-validation`.

## Changelog
| Date | Author | Type | Change | Ref |
|---|---|---|---|---|
| 2026-11-23 | david-schwartzman | created | Initial shaping | — |
