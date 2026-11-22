# Corgi Work Trial — Assignment Instructions

## What we're looking for

We want to use both roles in your platform, follow the core workflow, and understand where the money went. Connect at least two external services using their sandboxes. Choose integrations that support your track's core workflow. In your repository, tell us which integrations are live and which parts you simulated. Use US dollars, fictional customer data, and test credentials. You should be able to complete the trial without spending money.

## How we review your work

| Area | Points |
|---|---|
| Understanding of the financial problem | 30 |
| Working app | 25 |
| Sandbox integrations | 20 |
| Live debrief | 15 |
| Engineering judgment | 10 |

> **Note: In the debrief**
>
> During the debrief, we'll try the scenario from your track and ask you to show how the app arrived at a particular amount. We'll also discuss one choice you made about what to leave out.

> **Note: Working independently**
>
> Keep a short, dated decision log as you work. If a detail is missing, use your judgment and write down the assumption. A few sentences about a decision that affected the build are enough.

## How to submit

Choose your start time in this platform. Before the deadline shown here, open Submission and provide all five items:

1. A link to the deployed app.
2. Instructions or demo credentials for both roles.
3. A repository link we can open. Include your chosen track and decision log in the repository.
4. A link to a video of up to five minutes showing the transaction in your app.
5. A link to evidence from your sandbox integrations, such as event logs or screenshots.

Select **Submit work trial** and confirm. Your work stays yours.

---

# Chosen track: Track 1 · Policy administration

## The platform

> Build a platform for managing commercial insurance policies. Brokers should be able to issue a policy, change coverage during its term, and cancel it. Customers should be able to see their coverage and understand every charge, payment, and refund.

**Who uses it**

- **Broker:** manages policies and corrects mistakes.
- **Policyholder:** views coverage, pays amounts due, and checks their statement.

## The core workflow

1. Create a customer and issue a policy with coverage, start and end dates, and a premium. A simple pricing model is enough; document your assumptions.
2. Collect the premium through a sandbox integration and show whether payment is pending, successful, or failed.
3. Add coverage partway through the term. Calculate the additional premium from the date the coverage takes effect and show the resulting amount due.
4. Cancel the policy and process any refund due under your documented cancellation rule.
5. Give both roles a clear view of the policy history and financial statement. A reviewer should be able to trace each amount to the action and payment record behind it.

## The scenario we will test

A customer has paid for their policy and an additional coverage change. Three weeks later, the broker discovers that the extra coverage began a week earlier than recorded. Correct the effective date and show the revised amount due, keeping the original charge and the correction visible.

During the debrief, we will change the date and ask you to explain the calculation and what the customer sees.

## Scope and judgment

Start with one insurance product and a simple premium calculation. Prioritize the workflow above. Claims, renewals, and broker commissions are possible extensions once that workflow works. Explain what you simplified and what you would build next.

---

# Guidance from the recruiter (not in the written brief)

The frontend matters a lot to the reviewers. It doesn't need to be complex, but:

- **Clean and easy to understand.** In a field like insurance this is especially important: a policyholder should grasp their coverage, what they owe, and why, without needing to know insurance jargon.
- **Show creativity.** The interface is a place to show product and design thinking, not just a functional admin screen.
