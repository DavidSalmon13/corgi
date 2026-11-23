-- Resend remembers an Idempotency-Key for 24 hours across the whole account, and every database (local, Railway,
-- a reset one) numbers its policies, payments and refunds from 1. So the key sent to Resend can't be just
-- kind/source_id: each claimed email gets a random key of its own, fixed for that row (emails spec code review).
alter table sent_emails add column send_key uuid not null default gen_random_uuid();
