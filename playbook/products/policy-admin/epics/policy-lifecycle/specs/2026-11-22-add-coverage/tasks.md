# Tasks — Add coverage

> Epic: policy-lifecycle
> Spec: 2026-11-22-add-coverage
> Branch: feat/policy-lifecycle/add-coverage

## Tests first (TDD)

- [x] T1 Write failing `AddCoverageServiceTest` (api/src/test/java/app/plainly/policy/): Oct 15 → 321 days $642.00, last day $2.00, first day $730.00, 366-day term; every AC4 validation message; add posts one ADD_COVERAGE transaction, one coverage and one CHARGE with calc, linked; balance rises by the preview amount; preview writes nothing; two concurrent adds → one coverage (AC3–AC5, AC7)
- [ ] T2 Write failing `AddCoverageEndpointsTest`: 201 + Location, 422 field body, 403 for a policyholder, 404 for an unknown id; extend `MyCoverTest` for `EXTRA_COVER` and `latestChangeFrom`, and `BrokerPolicyReadTest` for the last action (AC4, AC9–AC11)
- [ ] T3 Write failing `web/src/lib/addCoverCopy.test.ts` (calc line, customer sentence with and without the per-day clause, save label, banner) and the `EXTRA_COVER` case in `myCoverCopy.test.ts` (AC3, AC6, AC9)

## Backend

- [ ] T4 `AddCoveragePlan` + `AddCoverageService.preview` with validation and a shared `plan()` (api/.../policy/)
- [ ] T5 `AddCoverageService.add`: policy row lock, ADD_COVERAGE transaction, coverage row, `LedgerPostingService.post` of the CHARGE, `Added` result
- [ ] T6 `AddCoverageController` (`/api/broker/policies/{id}/coverages`, `/preview`) with `AddCoverageRequest` parsing
- [ ] T7 `MyCoverController`: `DueReason.EXTRA_COVER` + `latestChangeFrom`; API suite green

## Web

- [ ] T8 `types.ts` additions + `lib/addCoverCopy.ts`; `myCoverCopy.ts` EXTRA_COVER text
- [ ] T9 `ChangePolicyPage.tsx` (B4 Add cover tab) + `policies/:id/change` route in App.tsx (AC2, AC3, AC4, AC8)
- [ ] T10 `PolicyWorkspace.tsx`: Add cover button and saved banner (AC1, AC6)
- [ ] T11 Widen `jargon.test.ts` to the lib copy helpers (AC12)

## Docs

- [ ] T12 DECISIONS.md: an add-on may start any day in the cover period, including the past; one extra cover per policy, enforced under a row lock (no unique index, for corrections); per-day shown only when exact

## Verification

- [ ] T13 Run the test suites and coverage on changed files (testing skill)
- [ ] T14 Run QA against acceptance criteria (qa skill)
