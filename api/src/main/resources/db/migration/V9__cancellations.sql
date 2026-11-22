-- Cancelling a policy (cancel-refund spec). Money is integer cents.

-- The day all cover stops (start of day). end_date keeps the original term end, because the days in the term price
-- every line. A policy is CANCELLED exactly when it has a cancel day, and that day falls inside the term: the start
-- day cancels everything, the end day would cancel nothing.
alter table policies add column cancelled_on date;
alter table policies add constraint policies_cancelled_has_day
    check ((status = 'CANCELLED') = (cancelled_on is not null));
alter table policies add constraint policies_cancelled_in_term
    check (cancelled_on is null or (cancelled_on >= start_date and cancelled_on < end_date));

-- A CREDIT gives back a cover's unused days: one negative line per cover, from the CANCEL transaction, with the
-- arithmetic that priced it. The original charges stay live above it; nothing is reversed or edited.
alter table ledger_entries drop constraint ledger_entries_entry_type_check;
alter table ledger_entries add constraint ledger_entries_entry_type_check
    check (entry_type in ('CHARGE', 'REVERSAL', 'PAYMENT', 'REFUND', 'CREDIT'));
alter table ledger_entries add constraint ledger_credit_traced
    check (entry_type <> 'CREDIT' or (amount_cents < 0
                                      and policy_transaction_id is not null
                                      and coverage_id is not null
                                      and calc_days is not null));
