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
| 2026-11-22-issue-policy | done | feat/policy-lifecycle/issue-policy · [#22](https://github.com/DavidSalmon13/corgi/pull/22) |
| 2026-11-22-real-sign-in | done | feat/policy-lifecycle/real-sign-in · [#23](https://github.com/DavidSalmon13/corgi/pull/23)–[#34](https://github.com/DavidSalmon13/corgi/pull/34) |
| 2026-11-22-collect-premium | done | feat/policy-lifecycle/collect-premium · [#35](https://github.com/DavidSalmon13/corgi/pull/35)–[#41](https://github.com/DavidSalmon13/corgi/pull/41) |
| 2026-11-22-add-coverage | done | feat/policy-lifecycle/add-coverage · [#42](https://github.com/DavidSalmon13/corgi/pull/42)–[#46](https://github.com/DavidSalmon13/corgi/pull/46) |
| 2026-11-22-correct-effective-date | done | feat/policy-lifecycle/correct-effective-date · [#48](https://github.com/DavidSalmon13/corgi/pull/48) |
| 2026-11-22-cancel-refund | done | feat/policy-lifecycle/cancel-refund · [#50](https://github.com/DavidSalmon13/corgi/pull/50) |
