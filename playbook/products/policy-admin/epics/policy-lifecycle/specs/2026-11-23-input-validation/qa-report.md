# QA Report — Input validation at the API boundary

> Spec: spec.md
> Date: 2026-11-23 · QA by: david-schwartzman · Mode: setup
> Verdict: PASS WITH NOTES

## Acceptance Criteria

| AC | Criterion | Result | Evidence |
|---|---|---|---|
| AC1 | Issue (+preview), add coverage (+preview), correction (+preview), cancellation (+preview) and sign-up declare their rules on the request record and bind with `@Valid` | pass | Annotated records in `BrokerIssueController.java` (Preview/Issue/ForCustomer), `AddCoverageController.java`, `CorrectionController.java`, `CancellationController.java`, `AuthController.java` (SignUpRequest). Bound at `BrokerIssueController.java:73,79,92,98`, `AddCoverageController.java:48,53`, `CorrectionController.java:52,58`, `CancellationController.java:52,58`, `AuthController.java:59`. The save endpoints use `@Validated({Default, OnSave})`, Spring's `@Valid` with groups (see Notes). Remove-cover (`RemoveCoverController.java:52,58`) is covered too (T7) |
| AC2 | A missing, blank, too-long or wrongly formatted field returns the 422 body and the service is never called | pass | `ApiErrorHandler.java:37-39` maps `MethodArgumentNotValidException` to the `ValidationException` body. Tests: `BrokerIssueEndpointsTest.aFormatErrorComesBackWithoutCallingTheService` (a taken email isn't reported), `aBlankOrTooLongFieldGetsTheServicesMessage`, and `customerModeChecksTheDateBeforeTheServiceRuns`; `*EndpointsTest.aBadBodyIsAFieldErrorBeforeTheServiceRuns` for correction, cancellation and remove-cover, and `AddCoverageEndpointsTest.anUnknownKindOrUnreadableDateIsAFieldErrorBeforeTheServiceRuns`, where an unknown policy id gets 422, not the service's 404; `AuthTest.aBlankEmailOrPasswordIsAFieldErrorBeforeTheServiceRuns` (no confirmPassword error from the service). `ApiErrorHandlerTest` |
| AC3 | Each field message is the same constant the service uses | pass | The annotations reference `IssuePolicyService.NO_LEGAL_NAME/NO_DESCRIPTION/NO_OWNER_NAME/BAD_EMAIL/TOO_LONG/NO_START`, `AddCoverageService.NO_KIND/NO_START`, `CorrectCoverageService.NO_START/NO_REASON/REASON_TOO_LONG`, `CancelPolicyService.NO_DATE/…`, `RemoveCoverService.NO_DATE/…` and `AccountService.NO_EMAIL/SHORT_PASSWORD`. The services use the same constants; the endpoint tests assert `Service.CONSTANT` values |
| AC4 | An unreadable date (`"2026-13-45"`, `""`) or an unknown cover kind is a field error, not a 400 | pass | `LenientDate.java` plus `LenientDateTest` (11 cases, including numbers, objects and arrays); `BrokerIssueEndpointsTest.anUnreadableOrBlankStartDateIsAFieldErrorOnTheDate`; `AddCoverageEndpointsTest.anUnknownKindOrUnreadableDateIsAFieldErrorBeforeTheServiceRuns` (`SPACE_TRAVEL` → `kind`, through `READ_UNKNOWN_ENUM_VALUES_AS_NULL`); the existing `aMissingOrUnreadableDateIsAFieldErrorNotACrash` tests are unchanged and green |
| AC5 | `fields` lists the fields in record (form) order | pass | `ApiErrorHandler.java:45-60` orders by `getRecordComponents()`; `ApiErrorHandlerTest.answersTheSame422BodyWithFieldsInRecordOrder`; `theFieldsComeBackInFormOrder` in the BrokerIssue, AddCoverage, Correction and Cancellation endpoint tests; order assertions in RemoveCover and AuthTest |
| AC6 | Malformed JSON is still 400 `bad_request`; sign-in and the Stripe webhook are unchanged | pass | `ApiErrorHandler.java:73-74` is unchanged; `BrokerIssueEndpointsTest.aBodyThatIsNotJsonIsABadRequest` passes. `SignInRequest` (`AuthController.java:39`) has no annotations or `@Valid`; `AuthTest.aWrongPasswordAndAnUnknownEmailGetTheSame401` passes. `git diff dev...HEAD -- billing/` is empty, and the Stripe webhook and refund suites pass |
| AC7 | Business rules are still enforced by the services, with their messages | pass | Unchanged tests still pass: `anOwnerEmailAnotherCustomerUsesGetsAFieldError` (email taken), `cancellingTwiceIsAPolicyError` / `removingTwiceIsAPolicyError` (cancelled), `CancelPolicyServiceTest:306` (payment waiting), `CorrectionEndpointsTest.theSameStartIsAFieldErrorOnTheDate` (same start), `addingItTwiceIsAFieldErrorOnTheCover`, and `RefundServiceTest` (refund only after a failure) |
| AC8 | The private `date()` helpers and `coverKind()` are gone from the controllers | pass | `grep "DateTimeParseException\|static .* date(\|coverKind(" */*Controller.java` finds 0 matches. All five `date()` helpers are gone, including remove-cover's |
| AC9 | DECISIONS.md records the three-layer model, the intentional overlap and the database audit | pass | DECISIONS.md, 2026-11-23 "Validation has three layers: shape at the edge, rules in services, invariants in Postgres" (with the DemoSeeder overlap and the audit with no migration). Two related entries: the owner email `@Pattern` and the `OnSave` validation group |

## Checks Run

- build + tests (api): `./gradlew build` — pass (522 tests, 0 failures, 0 skipped)
- build + typecheck (web): `npm run build` (`tsc --noEmit && vite build`) — pass (web unchanged on this branch)
- tests (web): `vitest run` — pass (348 tests, 23 files)
- lint: none configured (no `lint` script in `web/package.json`, no lint task in Gradle)
- coverage (JaCoCo through a temporary init script, not committed): 100% of lines in `ApiErrorHandler`, `LenientDate`, `ValidationException` and all six controllers; `AccountService` 97.4%, `IssuePolicyService` 95.0%

## Issues Found

(none)

## Notes

- **AC1 lists `@Email`; the owner email uses `@Pattern` instead.** Hibernate's `@Email` accepts `maya@juniperandrye`, which the service rejects, and rejects an email with surrounding spaces, which the service trims and accepts. The pattern is built from the service's own `EMAIL_SHAPE`, so the edge and the service agree (DECISIONS.md). Sign-up has `@NotBlank` only, as the Technical Approach says.
- **The save endpoints bind with `@Validated({Default.class, OnSave.class})`**, so the reason is required to save but not for the live preview (the existing `previewDoesNotNeedAReasonYet` tests still pass).
- **Scope beyond the AC1 list:** remove-cover and issue-for-existing-customer got the same pattern (T7, both now on dev).
- **Some literal service messages became named constants** (`IssuePolicyService`, `AddCoverageService`, `AccountService`) so the annotations could reuse them. Behaviour is unchanged and the service suites pass. This is the only touch to services, beyond the spec's "services untouched".
- **Hygiene:** T10 is ticked with this report. No PR yet; open one with the pr skill. The spec's `> Status:` still says `shaped`.
- **QA ran in an isolated worktree on `feat/policy-lifecycle/input-validation`**, because the main checkout was in use on another branch.
