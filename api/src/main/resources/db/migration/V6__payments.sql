-- Card payments through Stripe (collect-premium spec). Money is integer cents.

-- The Stripe customer behind one of ours, created on their first payment so a card can be kept for next time.
alter table customers add column stripe_customer_id text unique;

create sequence payment_seq;

-- One attempt to pay a policy's balance, backed by one Stripe PaymentIntent.
-- status is the only value in the money path that changes after insert, and only a verified Stripe webhook
-- changes it (DECISIONS, 2026-11-22). What the money did is recorded in ledger_entries, which never change.
create table payments (
    id                       bigserial primary key,
    display_id               text        not null unique default ('PAY-' || lpad(nextval('payment_seq')::text, 4, '0')),
    policy_id                bigint      not null references policies (id),
    created_by               bigint references app_users (id),
    stripe_payment_intent_id text        not null unique,
    amount_cents             bigint      not null check (amount_cents > 0),
    status                   text        not null default 'OPEN'
                                         check (status in ('OPEN', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
    payment_method_id        text,
    card_brand               text,
    card_last4               text check (card_last4 ~ '^[0-9]{4}$'),
    failure_reason           text,
    created_at               timestamptz not null default now(),
    settled_at               timestamptz
);

create index payments_policy_idx on payments (policy_id, id);

-- Every Stripe event id we have acted on. Inserting first, in the same transaction as the change it causes,
-- makes a replayed or duplicated delivery a no-op.
create table processed_stripe_events (
    event_id    text        primary key,
    type        text        not null,
    received_at timestamptz not null default now()
);

-- A payment line traces to the payment and to the Stripe event that settled it.
alter table ledger_entries add column payment_id bigint references payments (id);
alter table ledger_entries add column stripe_event_id text;
alter table ledger_entries add constraint ledger_payment_traced
    check (entry_type <> 'PAYMENT' or (payment_id is not null and stripe_event_id is not null));
-- One Stripe event posts at most one line (a second guard behind processed_stripe_events).
create unique index ledger_entries_stripe_event_idx on ledger_entries (stripe_event_id) where stripe_event_id is not null;
