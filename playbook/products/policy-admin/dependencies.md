# Dependencies

## Internal

(none). Single product, no shared internal services.

## External

| Service | Purpose | Auth method | Docs |
|---|---|---|---|
| Stripe (test mode) | Premium collection (PaymentIntents), refunds, webhooks | Secret key (`sk_test_…`) + webhook signing secret (`whsec_…`) | https://docs.stripe.com |
| Resend (sandbox) | Transactional email: policy issued, statement or receipt | API key; sender `onboarding@resend.dev` | https://resend.com/docs |
| Railway | Hosting for web, API and Postgres | Project token (deploys) | https://docs.railway.com |
