-- Removing product liability mid-term (remove-cover spec). Money is integer cents.

-- The day a removed cover stops (start of day). effective_to keeps the original end, because a credit is priced from
-- it, so the row is marked, not replaced. Null while the cover runs. A removal must fall inside the cover: its start
-- day removes everything, its end day would remove nothing.
alter table coverages add column ended_on date;
alter table coverages add constraint coverages_ended_in_cover
    check (ended_on is null or (ended_on >= effective_from and ended_on < effective_to));

-- A REMOVE_COVERAGE transaction posts one CREDIT for the removed cover's unused days (ledger_credit_traced, V9).
-- The name is Postgres's generated one from V1's inline check, confirmed with pg_constraint.
alter table policy_transactions drop constraint policy_transactions_type_check;
alter table policy_transactions add constraint policy_transactions_type_check
    check (type in ('ISSUE', 'ADD_COVERAGE', 'CORRECT', 'CANCEL', 'REMOVE_COVERAGE'));
