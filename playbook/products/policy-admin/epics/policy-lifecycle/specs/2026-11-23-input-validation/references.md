# References for Input validation at the API boundary

## Related Specs

- **[Related]** [2026-11-22-issue-policy](../2026-11-22-issue-policy/spec.md): overlaps on `api/**/policy/**` and `db/migration/**`. Kept separate because this spec owns only the validation layer across every body-taking endpoint, not issuing itself.
- **[Related]** [2026-11-23-remove-cover](../2026-11-23-remove-cover/spec.md): its removal request gets the same pattern once merged (T7).
- **[Related]** [2026-11-23-more-policies](../2026-11-23-more-policies/spec.md): its `/api/broker/customers/{id}/policies` (+preview) request gets the same pattern once merged (T7).

## Similar Implementations

### Today's 422 body

- **Location:** `api/src/main/java/app/plainly/common/ApiErrorHandler.java`, `common/ValidationException.java`
- **Relevance:** the exact body the boundary handler must reproduce: `{error, message, fields}`, with fields in form order.
- **Key patterns:** `LinkedHashMap` for field order; the "N things to fix before you can save." wording.

### Service-side checks and their message constants

- **Location:** `IssuePolicyService.validate()`, `AddCoverageService`, `CorrectCoverageService`, `CancelPolicyService`, `AccountService.signUp`
- **Relevance:** the annotations reuse these `static final` messages, so the edge and the service say the same thing. The services keep their checks because `DemoSeeder` calls `IssuePolicyService.issue` without HTTP.

### Controller date parsing to replace

- **Location:** the private `date()` helpers in `BrokerIssueController`, `AddCoverageController`, `CorrectionController`, `CancellationController`, and `AddCoverageController.coverKind()`
- **Relevance:** replaced by typed `LocalDate` / `CoverKind` fields, a lenient deserializer, and `@NotNull`.

### Endpoint test style

- **Location:** `api/src/test/java/app/plainly/policy/CorrectionEndpointsTest.java`
- **Key patterns:** `status().isUnprocessableContent()`, `jsonPath("$.fields.<name>").value(Service.CONSTANT)`, `jsonPath("$.message")`.
