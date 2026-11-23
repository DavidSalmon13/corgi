-- Emails to the policyholder (emails spec): one row per email we mean to send, claimed in the same transaction as
-- the money change that causes it and sent through Resend after that transaction commits.

-- (kind, source_id) is the email's identity: the policy for POLICY_ISSUED, the payment for PAYMENT_RECEIPT, the
-- refund for REFUND_RECEIPT. The unique key is what makes a retried webhook send nothing more.
create table sent_emails (
    id          bigserial primary key,
    kind        text        not null check (kind in ('POLICY_ISSUED', 'PAYMENT_RECEIPT', 'REFUND_RECEIPT')),
    source_id   bigint      not null,
    -- the owner's address, as recorded; demo mail is delivered to one test inbox instead (DECISIONS)
    to_address  text        not null,
    subject     text        not null,
    -- PENDING until the send after commit; SKIPPED when email is switched off (no Resend key)
    status      text        not null default 'PENDING'
                            check (status in ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    resend_id   text,
    error       text,
    created_at  timestamptz not null default now(),
    sent_at     timestamptz,
    constraint sent_emails_once unique (kind, source_id),
    constraint sent_emails_sent_has_id check ((status = 'SENT') = (resend_id is not null))
);
