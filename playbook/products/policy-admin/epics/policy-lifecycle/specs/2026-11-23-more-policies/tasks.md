# Tasks — Several policies per customer, and rewrite

> Epic: policy-lifecycle
> Spec: 2026-11-23-more-policies
> Branch: feat/policy-lifecycle/more-policies

## Failing tests first (TDD)

- [ ] T1 Write failing API tests:
  - IssueForCustomerTest: unknown customer 404, no customer or account rows written, replaces rules including a concurrent double rewrite
  - MyPoliciesTest: list, totalDueCents, 404 on every per-policy read and pay for another owner's policy
  - updates to MyCoverTest, statement and timeline endpoint tests, MyPayments tests and NotifyServiceTest for per-policy links and the "Sign in" issue email

  (testing skill)
- [ ] T2 Write failing web tests: policiesCopy.test.ts, path helpers, and per-policy cases in the myCoverCopy and paymentCopy tests (testing skill)

## Schema

- [ ] T3 Add V13__policy_rewrites.sql: policies.replaces_policy_id (unique FK, check <> id) (repository/migration)

## Broker: issue for an existing customer, and rewrite

- [ ] T4 CustomerRepository.find; PolicyRepository replaces/replacedBy reads, listForCustomer, isOwnedBy (repository)
- [ ] T5 IssuePolicyService previewForCustomer/issueForCustomer:
  - split customer and policy validation
  - lock and check the replaced policy (same customer, cancelled, not already replaced)
  - map DuplicateKeyException to 422
  - reuse the shared insert, post and publish steps

  (service)
- [ ] T6 BrokerIssueController POST /api/broker/customers/{customerId}/policies and /preview (controller)
- [ ] T7 PolicyViews detail and list rows gain customerId, replaces and replacedBy (service)

## Policyholder API

- [ ] T8 MyPoliciesService: list with totalDueCents (positive balances only), and ownedPolicy(me, id) returning 404 when not owned (service)
- [ ] T9 MyCoverService, StatementService.forPolicy, TimelineService.forPolicy and PaymentService.start take a policyId through ownedPolicy; remove latestIdForCustomer and lockLatestIdForCustomer (service)
- [ ] T10 Controllers: GET /api/me/policies and /api/me/policies/{id}/cover|statement|timeline; POST /api/me/payments takes {policyId}; old /api/me routes removed (controller)
- [ ] T11 EmailTemplates and NotifyService: per-policy links; the issue email says "Sign in" when the owner already has an account (service)

## Web

- [ ] T12 lib/policiesCopy.ts and per-policy path helpers, using the glossary terms (lib)
- [ ] T13 NewPolicyPage customer mode at /broker/customers/:customerId/policies/new?replaces=, with the read-only customer card and rewrite pre-fill (page)
- [ ] T14 PolicyWorkspace: "New policy for this customer", "Rewrite this policy", replaces/replaced-by links; PoliciesPage (B1) replaced state (page)
- [ ] T15 App.tsx per-policy /me/policies/:policyId/* routes and redirects from the old routes; new pages/me/PoliciesPage.tsx overview (page)
- [ ] T16 Me pages (Home, Pay, PaymentProgress, Statement, Timeline, Cancellation) use policyId query keys and paths, plus the "All policies" link (page)

## Docs

- [ ] T17 DECISIONS.md: flat cancel + rewrite instead of editing a bound policy; no money moves between policies; old /api/me routes removed; totalDue counts positive balances only
- [ ] T18 README demo steps: flat-cancel a paid policy, rewrite it, the owner's overview of both policies, pay the new one

## Verification

- [ ] T19 Run both suites with coverage on changed files (testing skill)
- [ ] T20 Run QA against acceptance criteria (qa skill)
