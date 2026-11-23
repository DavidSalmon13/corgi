# Tasks — Input validation at the API boundary

> Epic: policy-lifecycle
> Spec: 2026-11-23-input-validation
> Branch: feat/policy-lifecycle/input-validation

## Failing tests first (TDD)
- [x] T1 Add failing endpoint tests for AC2–AC5: garbage date, unknown kind, blank + too-long fields, field order, and the service not being called — in BrokerIssueEndpointsTest, AddCoverageEndpointsTest, CorrectionEndpointsTest, CancellationEndpointsTest, AuthTest

## Boundary plumbing (common/)
- [x] T2 Add `common/LenientDate` deserializer (blank/unreadable → null)
- [x] T3 Handle `MethodArgumentNotValidException` in `ApiErrorHandler` → same 422 body, fields in record order; share the "N things to fix" wording with `ValidationException`

## Request records (controllers only)
- [x] T4 `BrokerIssueController`: annotate `IssueRequest` / `PreviewRequest`, typed `LocalDate`, `@Valid`; delete `date()`
- [x] T5 `AddCoverageController`: typed `CoverKind` (unknown → null) + `LocalDate`, `@Valid`; delete `date()` / `coverKind()`
- [x] T6 `CorrectionController` and `CancellationController`: typed `LocalDate`, `@NotBlank @Size` reason, `@Valid`; delete `date()`
- [x] T6.1 `AuthController.SignUpRequest`: `@NotBlank` email/password, `@Valid` (sign-in untouched)
- [x] T7 For each unmerged endpoint that is on dev by then (remove-cover's removal request, more-policies' `/api/broker/customers/{id}/policies`): apply the same pattern to its request record; otherwise cancel this task with a note

## Docs
- [x] T8 DECISIONS.md: the three-layer model, the intentional edge/service overlap (DemoSeeder), and the database audit with no migration

## Verification
- [ ] T9 Run the full API test suite; make every pre-existing endpoint test pass unchanged, or adjust only those that expected format and business errors in one response (testing skill)
- [ ] T10 Run QA against acceptance criteria (qa skill)
