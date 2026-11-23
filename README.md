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
     --events payment_intent.processing,payment_intent.payment_failed,payment_intent.canceled,charge.succeeded,charge.failed,refund.created,refund.updated,refund.failed \
     --forward-to localhost:8080/api/stripe/webhook
   ```
4. Sign in as Maya (her first year is due), choose **Pay**, and use a [Stripe test card](https://docs.stripe.com/testing) with any future expiry and any CVC:

| Card number | What happens |
|---|---|
| `4242 4242 4242 4242` | Paid. The next payment offers it as "Card ending 4242 · Used last time". |
| `4000 0000 0000 0002` | Declined: "Didn't go through", and a $0.00 attempt on the ledger. |

Payments switch on only when all three `STRIPE_*` values are set and the keys are test-mode keys (`sk_test_…`, `pk_test_…`). Without them the app still runs, and the pay button explains that card payments aren't set up. On a deployment, set the three `STRIPE_*` variables and add a webhook endpoint for `https://<your-host>/api/stripe/webhook` in the Stripe dashboard (events `payment_intent.processing`, `payment_intent.payment_failed`, `payment_intent.canceled`, `charge.succeeded`, `charge.failed`, `refund.created`, `refund.updated`, `refund.failed`); its signing secret is the `STRIPE_WEBHOOK_SECRET` there.

## Demo: remove product liability mid-term

A mid-term endorsement that takes product liability off a policy from a chosen day. It needs Stripe set up as above, with `stripe listen` running, because the return premium goes back to the card and is marked settled only by Stripe's signed webhook.

1. **Issue with product liability.** Sign in as Sam, choose **New customer and policy**, and enter a fictional business with an owner email you'll use in step 5 (say `lena@northwindprint.test`). Set the start date to **Sep 1, 2026** and check **Product liability**. The policy period is Sep 1, 2026 → Sep 1, 2027 (365 days): $1,200.00 general liability plus $730.00 product liability, $1,930.00 due.
2. **Pay.** Sign out, choose **Create your account** with that email, then **Pay** the $1,930.00 with card `4242 4242 4242 4242`. Once the webhook arrives, the balance is $0.00.
3. **Remove from Dec 1.** Sign in as Sam, open the policy (B3) and choose **Remove product liability**. B4 opens on the **Remove** tab. Pick **Dec 1, 2026** as the effective date and give a reason, such as "Stopped selling bottled sauces." Before anything is saved, the preview shows:
   - Product liability return premium · $730.00 × 274 ÷ 365 · −$548.00 (274 unused days, pro-rata, no short-rate penalty)
   - Return premium to card ending 4242 · Sent through Stripe when you confirm · $548.00
   - the sentence the customer will see, with your reason word for word
4. **Confirm.** Choose **Remove product liability and refund $548.00**, then confirm. B3 shows the endorsement and credit ids, the product liability bar stops on Dec 1 with the rest striped, and the ledger has one CREDIT of −$548.00. **How we got this** names the REMOVE_COVERAGE transaction. A few seconds later Stripe's `refund.updated` webhook posts the REFUND line, and the refund shows as sent. Add endorsement, Correct and Remove now each say product liability was removed on Dec 1, 2026.
5. **The customer's view.** Sign in as the owner. P1 says product liability ends on Dec 1, 2026, shows your note, and says $548.00 is on its way to the card ending 4242 (then that it was refunded). P2's product liability bar ends on Dec 1, and P3 lists "Product liability removed, return premium", with the arithmetic in its **How we got this** drawer. With Resend set up, the refund receipt email gives the removal as the reason.

If the policy is cancelled later, the cancellation credits product liability only up to Dec 1, so no day is credited twice ([why](DECISIONS.md)).
