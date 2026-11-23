# Plainly

Business insurance, explained in plain language. A policy administration platform for the Corgi work trial, **Track 1 · Policy administration**. Brokers issue, change, correct and cancel General Liability policies. Policyholders see what they're covered for, what they owe, and why.

- **Live app: https://corgi-production-8107a1f1.up.railway.app**
- Sandbox evidence (Stripe, Resend): [`docs/evidence/`](docs/evidence/README.md)
- Decisions and assumptions: [`DECISIONS.md`](DECISIONS.md)
- Screen designs: [`design/plainly/`](design/plainly/README.md)
- Product and specs: [`playbook/products/policy-admin/`](playbook/products/policy-admin/mission.md)

All people, businesses and money in this app are fictional. Payments use Stripe test mode.

## Demo accounts

Every demo account uses the password `plainly-demo-2026` (a test credential for fictional people). The sign-in page lists them too, with a button that fills the form.

**To pay, use Stripe's test card `4242 4242 4242 4242`** with any future expiry date and any CVC. To see a declined payment, use `4000 0000 0000 0002`. These are Stripe test-mode cards, and no real money moves.

| Role | Email | Who |
|---|---|---|
| Broker | `sam@plainly.test` | Sam Reyes, who issues and manages policies |
| Policyholder | `tomas@herreracycle.test` | Tomás Herrera, Herrera Cycle Works LLC: a mid-term endorsement adding product liability |
| Policyholder | `priya@saffronlane.test` | Priya Raman, Saffron Lane Candle Co.: an endorsement whose effective date was corrected (reversal and rebill) |
| Policyholder | `lena@kowalskiprint.test` | Lena Kowalski, Kowalski Print Studio: a cancelled policy with a pro-rata return premium |
| Policyholder | `daniel@achebetailoring.test` | Daniel Achebe, Achebe Tailoring: a cancelled policy and its rewrite, so two policies |

No payments are seeded: every payment must trace to a real Stripe test event. Owners can't sign up until their broker has entered their email ([why](DECISIONS.md)).

Sign-in is real: BCrypt password hashes and server-side sessions. What's simplified is that sign-up has no email confirmation, there's no password reset or rate limiting, and the demo broker login above works on every deploy. See `DECISIONS.md`.

## Integrations: what's live and what's simulated

| Service | Status | What it does here |
|---|---|---|
| **Stripe** (test mode) | Live | Collects premium by card (PaymentIntents with Stripe Elements), records declined cards, and sends return premiums and correction refunds back to the card. A payment or refund changes status only when a signed Stripe webhook says so, and each event id is processed once (`processed_stripe_events`), so every payment line on the ledger names the Stripe event behind it. |
| **Resend** (sandbox) | Live, with the recipient swapped | Sends three emails: policy issued, payment receipt and refund receipt. Each is claimed once in `sent_emails` and sent with an idempotency key. Resend's sandbox delivers only to the account owner, so every email goes to one test inbox (`RESEND_TEST_TO`) and states which owner it was for. |

Simulated or simplified on purpose:

- **Email recipients.** The content, timing and once-only rule are real; only the address is swapped for the test inbox. Going live means verifying a domain ([why](DECISIONS.md)).
- **Pricing.** There's no rating engine or carrier. General liability is a flat $1,200.00 a year and product liability $730.00 a year ($2.00 a day), so every pro-rata amount can be checked by hand.
- **Sign-up** has no email confirmation, and the demo broker's password is public on every deploy, which is acceptable only because all data is fictional.
- **Demo data.** The seeded customers' policies and changes are made through the real services, but no payments are seeded: a payment must come from a real Stripe test event.

## What's simplified, and what I'd build next

The full reasoning for each is in [`DECISIONS.md`](DECISIONS.md).

- **One product:** General Liability, with product liability as its only optional coverage. It can be added once and removed once per policy; its effective date can be corrected any number of times.
- **Cancellation is pro-rata with no short-rate penalty.** The unused days are returned in full and any unpaid premium is taken off the refund first. Real policies cancelled by the insured often keep a short-rate penalty or a minimum earned premium. This is the main thing I left out of the money rules.
- **One timezone:** every policy runs on America/New_York calendar days. A carrier would use the insured's location.
- **Premium is due on receipt:** no installments, billing schedule, grace period or notice of cancellation for non-payment.
- **Left out:** claims, renewals, broker commissions, taxes and fees, self-serve quote and buy, password reset and sign-in rate limiting.

Next, in order: short-rate and minimum earned premium on cancellation, renewals (a new policy period priced from the last one), installment billing with a non-payment cancellation flow, then claims.

## Run locally

Requirements: Java 21, Docker, Node 20.19+.

```bash
cp .env.example .env            # optional: the defaults work as-is
docker compose up -d            # Postgres 16 on localhost:5432
cd api && ./gradlew bootRun     # API on http://localhost:8080; Flyway migrates on start
curl localhost:8080/api/health  # {"status":"UP","database":"UP"}

cd web && npm install && npm run dev   # app on http://localhost:5173, /api proxied to :8080
```

Tests run against a throwaway Postgres started by Testcontainers, so Docker must be running:

```bash
cd api && ./gradlew test
```

Settings for a deployment (Railway):

- The session cookie is marked Secure on every HTTPS request (Railway's proxy headers are trusted). `SESSION_COOKIE_SECURE=true` forces it on regardless.
- `PLAINLY_DEMO_SEED`: on by default. At startup it adds the demo customers above (policies, lifecycle changes and accounts) for any owner email that isn't taken yet; set it to `false` to skip.

To start over with an empty database: `docker compose down -v`. The ledger is append-only even locally, so this is the only way to remove ledger rows.

## Paying with Stripe (test mode)

Policyholders pay what they owe by card on `/me/pay`. A payment is marked paid or declined only when a signed Stripe webhook says so ([why](DECISIONS.md)), so locally you need the Stripe CLI to forward those webhooks.

1. In a Stripe account in **test mode**, copy the keys from Developers → API keys into `.env`: `STRIPE_SECRET_KEY` (`sk_test_…`) and `STRIPE_PUBLISHABLE_KEY` (`pk_test_…`). Never use live keys.
2. Install the [Stripe CLI](https://docs.stripe.com/stripe-cli) and get the webhook signing secret into `.env`:
   ```bash
   stripe listen --api-key "$STRIPE_SECRET_KEY" --print-secret   # prints whsec_… → STRIPE_WEBHOOK_SECRET
   ```
3. Start the API with `.env` loaded, then forward the payment events to it (keep this running while you test):
   ```bash
   cd api && set -a && . ../.env && set +a && ./gradlew bootRun
   stripe listen --api-key "$STRIPE_SECRET_KEY" \
     --events payment_intent.processing,payment_intent.payment_failed,payment_intent.canceled,charge.succeeded,charge.failed,refund.created,refund.updated,refund.failed \
     --forward-to localhost:8080/api/stripe/webhook
   ```

Test cards ([Stripe docs](https://docs.stripe.com/testing)), with any future expiry and any CVC:

| Card number | What happens |
|---|---|
| `4242 4242 4242 4242` | Paid. The next payment offers it as "Card ending 4242 · Used last time". |
| `4000 0000 0000 0002` | Declined: "Didn't go through", and a $0.00 attempt on the ledger. |

Payments switch on only when all three `STRIPE_*` values are set and the keys are test-mode keys (`sk_test_…`, `pk_test_…`). Without them the app still runs, and the pay button explains that card payments aren't set up. On a deployment, set the three `STRIPE_*` variables and add a webhook endpoint for `https://<your-host>/api/stripe/webhook` in the Stripe dashboard (events `payment_intent.processing`, `payment_intent.payment_failed`, `payment_intent.canceled`, `charge.succeeded`, `charge.failed`, `refund.created`, `refund.updated`, `refund.failed`); its signing secret is the `STRIPE_WEBHOOK_SECRET` there.
