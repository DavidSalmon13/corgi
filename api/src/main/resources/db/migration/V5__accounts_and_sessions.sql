-- Real sign-in (spec 2026-11-22-real-sign-in). Every app user signs in with an email and a password.
-- Brokers are seeded; a policyholder's row is created when their policy is issued (email only)
-- and gets its password when the owner signs up.

-- One email belongs to one customer, so sign-up knows whose policy an email claims.
-- Earlier demo data may reuse an owner email: keep the oldest customer's, and make the later
-- ones unique by adding "+<customer id>" before the @ (customer contact data, not ledger data).
update customers c
set owner_email = split_part(c.owner_email, '@', 1) || '+' || c.id || '@' || split_part(c.owner_email, '@', 2)
where exists (
    select 1 from customers older
    where lower(older.owner_email) = lower(c.owner_email) and older.id < c.id
);

create unique index customers_owner_email_key on customers (lower(owner_email));

alter table app_users
    add column email         text,
    add column password_hash text,
    add column signed_up_at  timestamptz;

-- Emails are stored trimmed and lower-case, and compared the same way.
update app_users u
set email = lower(trim(c.owner_email))
from customers c
where c.id = u.customer_id;

-- The demo broker, Sam Reyes. The hash is BCrypt (cost 10) of the demo password "plainly-demo-2026",
-- which the README and the sign-in page list. Fictional account, test credential only.
update app_users
set email         = 'sam@plainly.test',
    password_hash = '$2a$10$JQUWXW.U1FjL6jMFKO2sneUqmAKZRAPiJsKiNTY5hjGCDn0Xy6iHK',
    signed_up_at  = now()
where role = 'BROKER' and display_name = 'Sam Reyes';

alter table app_users alter column email set not null;
create unique index app_users_email_key on app_users (email);
alter table app_users add constraint app_users_email_lower check (email = lower(trim(email)));
-- A password and a sign-up time come together, or not at all.
alter table app_users add constraint app_users_signed_up
    check ((password_hash is null) = (signed_up_at is null));

-- Sessions: the cookie holds a random token; only its SHA-256 hash is stored, so a copy of this
-- table can't be used to sign in. Signing out deletes the row (sessions aren't ledger data).
create table sessions (
    id         bigserial primary key,
    token_hash bytea       not null unique,
    user_id    bigint      not null references app_users (id),
    created_at timestamptz not null default now(),
    expires_at timestamptz not null
);

create index sessions_user_id_idx on sessions (user_id);
