-- Several policies per customer, and rewrite (more-policies spec).

-- A rewrite is a new policy that replaces a cancelled one issued wrong. The old policy is never edited: it stays
-- cancelled, with its own ledger, and the new one points back at it. Unique, so a policy is rewritten at most once
-- even when two brokers rewrite it at the same moment (the service also checks under the old policy's row lock and
-- turns a clash on this index into a 422). The same-customer and cancelled rules need other rows, so the service
-- checks those.
alter table policies add column replaces_policy_id bigint references policies (id);
alter table policies add constraint policies_replaces_once unique (replaces_policy_id);
alter table policies add constraint policies_replaces_another
    check (replaces_policy_id is null or replaces_policy_id <> id);
