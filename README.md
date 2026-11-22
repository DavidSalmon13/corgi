# Plainly

Business insurance, explained in plain language. A policy administration platform for the Corgi work trial (Track 1). Brokers issue, change, correct and cancel General Liability policies. Policyholders see what they're covered for, what they owe, and why.

- Brief: [`ASSIGNMENT.md`](ASSIGNMENT.md) · Decisions and assumptions: [`DECISIONS.md`](DECISIONS.md)
- Screen designs: [`design/plainly/`](design/plainly/README.md)
- Product and specs: [`playbook/products/policy-admin/`](playbook/products/policy-admin/mission.md)

All people, businesses and money in this app are fictional. Payments use Stripe test mode.

## Demo accounts

Every demo account uses the password `plainly-demo-2026` (a test credential for fictional people). The sign-in page lists them too, with a button that fills the form.

| Role | Email | Who |
|---|---|---|
| Broker | `sam@plainly.test` | Sam Reyes, who issues and manages policies |
| Policyholder | `maya@juniperandrye.test` | Maya Okafor, owner of Juniper & Rye Bakery LLC, with a policy Sam issued |

To try the owner's first visit: sign in as Sam, create a customer with any email, sign out, then choose **Create your account** on the sign-in page and use that email. Owners can't sign up until their broker has entered their email ([why](DECISIONS.md)).

Sign-in is real: BCrypt password hashes and server-side sessions. What's simplified is that sign-up has no email confirmation, there's no password reset or rate limiting, and the demo broker login above works on every deploy. See `DECISIONS.md`.

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
- `PLAINLY_DEMO_SEED`: on by default. At startup it adds Maya's demo policy and account if they're missing; set it to `false` to skip.

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
     --events payment_intent.processing,payment_intent.payment_failed,payment_intent.canceled,charge.succeeded,charge.failed \
     --forward-to localhost:8080/api/stripe/webhook
   ```
4. Sign in as Maya (her first year is due), choose **Pay**, and use a [Stripe test card](https://docs.stripe.com/testing) with any future expiry and any CVC:

| Card number | What happens |
|---|---|
| `4242 4242 4242 4242` | Paid. The next payment offers it as "Card ending 4242 · Used last time". |
| `4000 0000 0000 0002` | Declined: "Didn't go through", and a $0.00 attempt on the ledger. |

Payments switch on only when all three `STRIPE_*` values are set and the keys are test-mode keys (`sk_test_…`, `pk_test_…`). Without them the app still runs, and the pay button explains that card payments aren't set up. On a deployment, set the three `STRIPE_*` variables and add a webhook endpoint for `https://<your-host>/api/stripe/webhook` in the Stripe dashboard (events `payment_intent.processing`, `payment_intent.payment_failed`, `payment_intent.canceled`, `charge.succeeded`, `charge.failed`); its signing secret is the `STRIPE_WEBHOOK_SECRET` there.
