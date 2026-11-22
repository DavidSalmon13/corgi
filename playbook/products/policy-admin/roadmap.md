# Product Roadmap

## Core Features (current state)

None. This is a greenfield product.

## Phase 1: MVP

1. Demo sign-in for each role (Broker, Policyholder) with seeded fictional data.
2. Broker creates a customer and issues a General Liability policy (base premium $1,200/yr).
3. Premium collection through Stripe test mode, showing pending, succeeded or failed, updated by an idempotent webhook.
4. Mid-term add-on coverage ($730/yr), priced pro-rata from its effective date, with the resulting amount due.
5. Effective-date correction by reversing and rebilling, with the original charge and the correction both visible (the trial's test scenario).
6. Cancellation with a pro-rata refund of unearned premium through a Stripe refund, netting any unpaid balance.
7. Policy history and financial statement for both roles, with "how we got this" tracing.
8. Resend emails when a policy is issued and for statements or receipts.
9. Deployed on Railway, with a README listing which integrations are live and which parts are simulated.

## Phase 2: Post-Launch

- Short-rate cancellation fees
- Renewals
- Claims
- Broker commissions
- Installment billing (monthly plans)
- More products and add-ons, with a rating engine
- Real authentication (SSO)
- PDF statements and certificates of insurance
- Port the API to TypeScript to share money and DTO types end to end
