-- The ledger is append-only: mistakes are fixed by posting a reversal and a rebill,
-- never by changing or removing a line. Enforced here so no code path can bypass it.

create function ledger_entries_append_only() returns trigger
    language plpgsql as
$$
begin
    raise exception 'ledger_entries is append-only: % is not allowed; post a reversal instead', tg_op
        using errcode = 'P0001';
end;
$$;

create trigger ledger_entries_no_update_or_delete
    before update or delete on ledger_entries
    for each row execute function ledger_entries_append_only();

create trigger ledger_entries_no_truncate
    before truncate on ledger_entries
    for each statement execute function ledger_entries_append_only();
