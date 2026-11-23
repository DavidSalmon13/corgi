# Plainly: screen designs

Design source for the twelve Plainly screens plus the demo sign-in, exported from the design canvas
(https://claude.ai/artifact/DrLpH75E15cMgiEF9rmiMn, private to the owner).

Each `.dc.html` file is one screen. They are design-canvas components, so they render inside that canvas
(they load the canvas's `support.js`) rather than as standalone pages. `canvas.json` holds the layout.
Every screen has a `dark` toggle, and most have a `state` tweak covering the states listed in the brief.

Every screen uses the same story: the extra cover's start moves from Oct 15 to Oct 8, adding $14.00
(7 days × $2.00). The pricing rules are in `DECISIONS.md`.

## Policyholder (mobile, 390 px)

| File | Screen | States |
| --- | --- | --- |
| `Main.dc.html` | P3 Statement | normal, refund issued; correction card opens and closes |
| `P1-Home.dc.html` | P1 Home: Your cover | amount due, nothing due, payment waiting, payment failed, refund on its way, cancelled |
| `P4-HowWeGotThis.dc.html` | P4 "How we got this" drawer | charge, reversal, payment, refund |
| `P2-Timeline.dc.html` | P2 Cover timeline | base only, with extra cover, after a correction, cancelled |
| `P5-Pay.dc.html` | P5 Pay | card form, waiting for your bank, paid, didn't go through |
| `P6-Cancellation.dc.html` | P6 Cancellation summary | refund waiting, refunded |
| `SignIn.dc.html` | Sign-in with role switcher (demo) | — |
| `L1-Landing.dc.html` | L1 Landing for guests: what we offer, how it works, sign in / create your account | light, dark |

## Broker (desktop pages)

| File | Screen | States |
| --- | --- | --- |
| `B4-ChangePreview.dc.html` | B4 Change with money preview | Add cover / Correct / Cancel tabs; preview (owes more), preview (refund due), confirm, saved, refund failed |
| `B3-Workspace.dc.html` | B3 Policy workspace | active, balance due, cancelled |
| `B1-Policies.dc.html` | B1 Policies | populated (with filters), empty |
| `B2-NewPolicy.dc.html` | B2 New customer and policy | valid, field errors |
| `B5-History.dc.html` | B5 History | — |

## Assumptions made in the designs

- **Ledger and transaction IDs:** the brief only fixed LE-0006, LE-0007 and PTX-0003, so the rest are numbered in order. LE-0001 to LE-0008 cover the story; cancellation adds PTX-0004 and LE-0009 to LE-0011.
- **Stripe references:** they use format placeholders (`pi_3Q…`, `evt_1Q…`, `re_3Q…`).
- **"Refund due" story:** the reason text for the Oct 22 version is invented.
- **Extra copy:** "Banks take 5–10 days to show a refund" and the Stripe test-mode note on sign-in were added.
- **B1 list:** the other four businesses are demo data, with owner names left as placeholders.
