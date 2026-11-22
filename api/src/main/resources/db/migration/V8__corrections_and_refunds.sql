-- Correcting the extra cover's start date (correct-effective-date spec), and the refunds it can owe.
-- Money is integer cents.

-- A correction never edits a cover row: it inserts the corrected one and points the old row at it.
-- Reads skip replaced rows, so a policy still shows one extra cover. Each row is replaced at most once.
alter table coverages add column replaced_by_coverage_id bigint unique references coverages (id);
alter table coverages add constraint coverages_not_replaced_by_self check (replaced_by_coverage_id <> id);

create sequence refund_seq;

-- Money going back to a card, against one earlier payment. Recorded PENDING in the same transaction as the
-- correction that owes it, sent to Stripe after commit (one idempotency key per attempt), and settled only by a
-- verified webhook. Like payments, status is the only money value that changes after insert; what the money did is
-- the REFUND line in ledger_entries, posted once on success.
create table refunds (
    id                    bigserial primary key,
    display_id            text        not null unique default ('RF-' || lpad(nextval('refund_seq')::text, 4, '0')),
    policy_id             bigint      not null references policies (id),
    payment_id            bigint      not null references payments (id),
    policy_transaction_id bigint      not null references policy_transactions (id),
    amount_cents          bigint      not null check (amount_cents > 0),
    status                text        not null default 'PENDING' check (status in ('PENDING', 'SUCCEEDED', 'FAILED')),
    stripe_refund_id      text unique,
    attempt               integer     not null default 1 check (attempt >= 1),
    failure_reason        text,
    created_at            timestamptz not null default now(),
    sent_at               timestamptz,
    settled_at            timestamptz
);

create index refunds_policy_idx on refunds (policy_id, id);
create index refunds_payment_idx on refunds (payment_id);

-- A refund line traces to the refund and to the Stripe event that settled it.
alter table ledger_entries add column refund_id bigint unique references refunds (id);
alter table ledger_entries add constraint ledger_refund_traced
    check (entry_type <> 'REFUND' or (refund_id is not null and stripe_event_id is not null));
