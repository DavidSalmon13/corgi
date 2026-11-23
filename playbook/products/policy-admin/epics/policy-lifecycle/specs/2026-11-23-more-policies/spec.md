# Several policies per customer, and rewrite

> Epic: ../../epic.md
> Status: shaped
> Branch: feat/policy-lifecycle/more-policies
> Covers: /me/policies/:policyId, /me/policies/:policyId/*, /api/me/policies, /api/me/policies/{id}/cover, /api/me/policies/{id}/statement, /api/me/policies/{id}/timeline, /api/broker/customers/{id}/policies, /api/broker/customers/{id}/policies/preview, /broker/customers/:customerId/policies/new, api/src/main/resources/db/migration/V13__policy_rewrites.sql, web/src/pages/me/PoliciesPage.tsx, web/src/lib/policiesCopy.ts

## Overview

A customer can hold several policies. A broker can issue a policy for an existing customer, or **rewrite** a cancelled one. A rewrite is the standard fix for a policy issued wrong: flat-cancel it (cancel on its start day for a full credit), then issue a corrected policy linked to it. The start date of a bound policy is never edited in place.

The policyholder sees all their policies and pays each one on its own. Money doesn't move between policies: the cancelled one's return premium goes back to the card, and the new one is due.

This closes the "the original policy was wrong" gap behind the brief's "broker … corrects mistakes". It keeps the epic's rules: nothing is overwritten, and every amount traces to its transaction.

Today one policy per customer is assumed in two places:
- `PolicyRepository.latestIdForCustomer`, used by `MyCoverService`, `StatementService`, `TimelineService` and `PaymentService.start`;
- `IssuePolicyService`, which always creates a customer.

The schema already allows several policies. There are no designs for this flow. The UI extends P1 (cards), B2 (customer mode), B3 (actions and links) and B1, and new copy uses the glossary/`Term` "?" explanation.

## Goals & User Stories

- As a **broker**, I can issue another policy for a customer I already have, without re-entering them.
- As a **broker**, when a policy was issued wrong I cancel it flat, then rewrite it in one click from the cancelled policy. The two are linked both ways.
- As a **policyholder** with several policies, I see them all at a glance: what each costs, what I owe in total, and which policy replaced which. Each one opens its own cover, statement, timeline and Pay.
- As a **policyholder** with one policy, nothing changes.

## Acceptance Criteria

Worked example:
- GL-2026-0013 was issued from Sep 1, 2026 instead of Oct 1, and paid ($1,200.00).
- The broker cancels it on Sep 1, a flat cancellation. $1,200.00 is refunded to card 4242.
- The broker rewrites it from Oct 1, 2026 to Oct 1, 2027, issuing a new policy for $1,200.00.

1. AC1: On B3, every policy shows **New policy for this customer**. It opens B2 at `/broker/customers/:customerId/policies/new`, where the customer is read-only (business, owner, email). Only the start date and extra cover are asked, with the same price preview as today's issue.
2. AC2: A cancelled policy that isn't replaced shows **Rewrite this policy** as its main action. It opens B2 with `?replaces=:policyId`, extra cover pre-set from the old policy, and a note "Replaces GL-2026-0013 (cancelled Sep 1, 2026)". Saving records `policies.replaces_policy_id`.
3. AC3: A policy can be replaced only if it belongs to the same customer, is cancelled, and isn't already replaced. This is checked under the replaced policy's row lock and backed by a unique index. A breach returns a 422 under `replaces` with a plain message; an unknown customer returns 404.
4. AC4: B3 shows "Replaces GL-…" and "Replaced by GL-…" as links. In B1, a replaced policy reads "Cancelled · replaced by GL-…".
5. AC5: Issuing for an existing customer writes no customer or account rows. The policy-issued email says "Sign in to see it" when the owner already has an account, and "Set up your account" otherwise.
6. AC6: `GET /api/me/policies` lists the owner's policies, newest first. Each row has its number, period, status, balance, money in flight (payment waiting or refund on its way), and replaces/replacedBy. The response adds `totalDueCents`, the sum of positive balances only.
7. AC7: With one policy, `/me` is today's home for it. With two or more, `/me` shows **Your policies**: the total due, then one P1-style card per policy (number, period, status, amount due or refund on its way, replaced note). Each card opens `/me/policies/:id`.
8. AC8: Home, Pay, Statement, Timeline and Cancellation live under `/me/policies/:id/...`. Each shows an **All policies** link when the owner has two or more. The old `/me/pay`, `/me/statement`, `/me/timeline` and `/me/cancellation` redirect to the newest policy's page.
9. AC9: `GET /api/me/policies/{id}/cover|statement|timeline` and `POST /api/me/payments {policyId}` return 404 for a policy the signed-in owner doesn't own. A payment pays only that policy's balance.
10. AC10: Links in the issue, receipt and refund emails point at the policy's own pages.
11. AC11: New copy uses the standard term (Rewrite, Flat cancellation, Policy period) with the "?" explanation, and `jargon.test.ts` passes.

## Technical Approach

- **Schema (`V13__policy_rewrites.sql`):** `policies.replaces_policy_id bigint null references policies(id)`, unique, with a check `replaces_policy_id <> id`.
- **Repositories:**
  - `CustomerRepository.find(id)`;
  - `PolicyRepository` reads for replaces/replacedBy, `listForCustomer(customerId)` and `isOwnedBy`. Drop `latestIdForCustomer` and `lockLatestIdForCustomer`.
- **Issue:**
  - `IssuePolicyService.previewForCustomer` and `issueForCustomer` split `validate` into customer checks and policy checks.
  - The customer must exist. Lock and check the replaced policy, and map `DuplicateKeyException` to a 422.
  - Reuse the shared steps: insert the policy, its transaction and covers, post the ledger, publish `PolicyIssued`.
  - `BrokerIssueController` adds `POST /api/broker/customers/{customerId}/policies` and `/preview` with `{startDate, productLiability, replacesPolicyId?}`.
  - `PolicyViews` detail and list rows gain `customerId`, `replaces` and `replacedBy`.
- **Policyholder:**
  - `MyPoliciesService` provides `list(me)` and `ownedPolicy(me, id)` (404 when not owned).
  - `MyCoverService.cover(me, id)`, `StatementService.forPolicy`, `TimelineService.forPolicy` and `PaymentService.start(me, policyId)` go through `ownedPolicy`.
  - Controllers serve `/api/me/policies` and `/api/me/policies/{id}/…`. The old `/api/me/cover|statement|timeline` routes are removed.
  - `EmailTemplates` take `policyId` for links, plus `hasAccount` for the issue email.
- **Web:**
  - `App.tsx`: `/me` picks the single home or `PoliciesPage`. Per-policy routes, with redirects from the old ones.
  - Me pages use query keys such as `['my-cover', policyId]`, and every invalidation includes the id.
  - `lib/policiesCopy.ts` and path helpers.
  - `NewPolicyPage` customer mode.
  - `PolicyWorkspace`: actions and links. `PoliciesPage` (B1): replaced state.

## Out of Scope

- Paying several policies in one payment.
- Moving money from the old policy to the new one: the old is refunded and the new is due.
- A broker customer page, customer search, or editing customer details.
- Renewals.
- Rewriting an active policy in one step: cancel it first.
- Seeding a second demo policy.

## Standards Applied

- [global/tech-stack](../../../../../../standards/global/tech-stack.md): Spring Boot 4 / JdbcClient, Flyway, React + Vite + Tailwind v4, TanStack Query, Vitest.
- [backend/layers](../../../../../../standards/backend/layers.md): controller → service → repository; only repositories run SQL; cross-module calls go service to service.
- [global/git-workflow](../../../../../../standards/global/git-workflow.md): branch naming, commits, PRs of at most 10 files.

## Changelog

| Date | Author | Type | Change | Ref |
|---|---|---|---|---|
| 2026-11-23 | david-schwartzman | created | Initial shaping | — |
