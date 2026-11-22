-- Plainly core schema. Money is integer cents (bigint). Dates are end-exclusive,
-- America/New_York calendar days, stored as plain dates.

create sequence policy_number_seq;
create sequence policy_transaction_seq;
create sequence ledger_entry_seq;

create table customers (
    id                   bigserial primary key,
    legal_name           text        not null,
    business_description text        not null,
    owner_name           text        not null,
    owner_email          text        not null,
    created_at           timestamptz not null default now()
);

-- Demo sign-in only (simulated auth): a broker, or the owner of one customer.
create table app_users (
    id           bigserial primary key,
    role         text        not null check (role in ('BROKER', 'POLICYHOLDER')),
    display_name text        not null,
    subtitle     text,
    customer_id  bigint references customers (id),
    created_at   timestamptz not null default now(),
    check ((role = 'POLICYHOLDER') = (customer_id is not null))
);

create table policies (
    id            bigserial primary key,
    policy_number text        not null unique,
    customer_id   bigint      not null references customers (id),
    product       text        not null default 'GENERAL_LIABILITY',
    start_date    date        not null,
    end_date      date        not null,
    status        text        not null default 'ACTIVE' check (status in ('ACTIVE', 'CANCELLED')),
    created_at    timestamptz not null default now(),
    check (start_date < end_date)
);

create table coverages (
    id                   bigserial primary key,
    policy_id            bigint      not null references policies (id),
    kind                 text        not null check (kind in ('BASE', 'PRODUCT_LIABILITY')),
    annual_premium_cents bigint      not null check (annual_premium_cents >= 0),
    effective_from       date        not null,
    effective_to         date        not null,
    created_at           timestamptz not null default now(),
    check (effective_from < effective_to)
);

-- Every broker action is one policy transaction; ledger lines point back to it.
create table policy_transactions (
    id             bigserial primary key,
    display_id     text        not null unique default ('PTX-' || lpad(nextval('policy_transaction_seq')::text, 4, '0')),
    policy_id      bigint      not null references policies (id),
    type           text        not null check (type in ('ISSUE', 'ADD_COVERAGE', 'CORRECT', 'CANCEL')),
    effective_date date        not null,
    reason         text,
    created_by     bigint references app_users (id),
    created_at     timestamptz not null default now()
);

-- Append-only (see V2). Positive = the customer owes more; negative = credit.
create table ledger_entries (
    id                    bigserial primary key,
    display_id            text        not null unique default ('LE-' || lpad(nextval('ledger_entry_seq')::text, 4, '0')),
    policy_id             bigint      not null references policies (id),
    policy_transaction_id bigint references policy_transactions (id),
    coverage_id           bigint references coverages (id),
    entry_type            text        not null check (entry_type in ('CHARGE', 'REVERSAL', 'PAYMENT', 'REFUND')),
    entry_date            date        not null,
    description           text        not null,
    amount_cents          bigint      not null,
    -- The arithmetic behind a pro-rata line: calc_annual_cents × calc_days ÷ calc_term_days.
    calc_annual_cents     bigint,
    calc_days             integer,
    calc_term_days        integer,
    cover_from            date,
    cover_to              date,
    reverses_entry_id     bigint unique references ledger_entries (id),
    posted_at             timestamptz not null default now(),
    check (entry_type not in ('CHARGE', 'REVERSAL') or policy_transaction_id is not null),
    check ((calc_annual_cents is null) = (calc_days is null) and (calc_days is null) = (calc_term_days is null))
);

create index ledger_entries_policy_idx on ledger_entries (policy_id, id);
create index policy_transactions_policy_idx on policy_transactions (policy_id, id);
create index coverages_policy_idx on coverages (policy_id);
create index policies_customer_idx on policies (customer_id);
