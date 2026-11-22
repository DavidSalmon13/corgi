# Policy Lifecycle

> Product: ../../mission.md
> Status: planning
> Created: 2026-11-22 · Roles planned: PM, Tech

## Summary

A broker can run a General Liability policy from start to finish: issue it, add coverage mid-term, correct an effective date and cancel it. The policyholder pays through Stripe test mode. Every step posts append-only, pro-rata ledger entries that trace back to the policy transaction or payment behind them. When this ships, the trial scenario (moving an add-on's start a week earlier, three weeks after it was paid) works with the original charge and the correction both visible.

## Documents

- [Product brief](product-brief.md)
- [Tech plan](tech-plan.md)

## Specs

| Spec | Status | Branch |
|---|---|---|
| 2026-11-22-issue-policy | shaped | feat/policy-lifecycle/issue-policy |
