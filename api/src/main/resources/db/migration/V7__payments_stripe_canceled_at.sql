-- When Plainly cancelled a payment's PaymentIntent at Stripe because a newer one replaced it (hotfix
-- payment-review-fixes). It's a record of what we asked Stripe to do, not the payment's status: the status
-- still changes only through a verified webhook. A cancelled intent can never be paid, so an old pay page in
-- another tab can't charge the customer a second time.
alter table payments add column stripe_canceled_at timestamptz;
