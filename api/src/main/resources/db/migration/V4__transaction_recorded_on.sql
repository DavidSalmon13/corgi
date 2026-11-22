-- The policy calendar day a broker action was recorded (from the app's New York clock, not
-- the database server's). Drives "last action" in lists and the date on ledger lines.
alter table policy_transactions add column recorded_on date not null default current_date;
