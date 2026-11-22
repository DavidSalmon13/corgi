# Statement & History — Product Brief

> Epic: epic.md · Product: ../../mission.md

## Goals

Today each screen shows only the amount due and what the latest change did. Nobody can see the whole story of a policy's money, so core workflow step 5 and the debrief ("show how the app arrived at this amount") are not yet met. The trial also has only one live integration (Stripe), and the brief asks for at least two. This epic delivers:

1. A plain-language statement and cover timeline for the policyholder.
2. A full history for the broker.
3. A "how we got this" view on every amount, tracing ledger entry → policy change or payment → Stripe event, with the date arithmetic shown.
4. A clear reading of the correction scenario, with the original charge, the reversal and the rebill all visible.
5. Resend emails as the second live sandbox integration.

## User Stories

- As a policyholder, I can see a statement of every charge, payment and refund with a running balance, so I know what I owe and why.
- As a policyholder, I can see a timeline of my cover: what I'm covered for, from when to when, and what changed.
- As a policyholder, I can tap any amount and see "how we got this" in plain words (days × daily rate, and which change or payment caused it).
- As a policyholder, when a date was corrected, I see the original charge, its reversal and the new charge, not a silently changed number.
- As a broker, I can see a policy's full history (every transaction, ledger entry, payment and refund, with IDs and Stripe references), so I can answer any question about an amount.
- As a policyholder, I get an email when my policy is issued, and a receipt when a payment or refund settles, with a link to my statement.

## Success Criteria

- The statement's running balance equals the ledger balance, to the cent, on every policy.
- Every amount on the statement and the history opens "how we got this", showing the ledger entry, the policy transaction or payment, and the Stripe event id where there is one.
- The trial scenario (Oct 15 → Oct 8, +$14.00) shows the original charge, the reversal and the rebill. In the debrief the date can be changed live and the statement explains the new amount.
- Both roles see the same numbers; the broker view adds IDs and references.
- Resend (sandbox) sends the issue email and the payment and refund receipts, once per event even when a webhook is retried, with evidence visible in the Resend dashboard.
- Screens match `design/plainly/` (P2 Timeline, P3 Statement in `Main.dc.html`, the P4 drawer, B5 History) in light and dark, at 390 px and on desktop, with no unexplained jargon.

## Out of Scope

- TBD
