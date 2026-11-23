-- A broker's phone number, so a policyholder can reach the broker who set up their policy (P1, P6, the landing page).
-- Nullable: a broker may have none. The demo broker gets a fictional number from the 555-0100–0199 range, which is
-- reserved for fiction and never rings anyone.
alter table app_users add column phone text;

update app_users set phone = '(415) 555-0142' where role = 'BROKER' and email = 'sam@plainly.test';
