-- Allow authenticated users to read only their own wallet and ledger rows.
-- RLS policies from 0002_wallet.sql remain the row-level security boundary.
grant usage on schema public to authenticated;
grant select on table public.wallets to authenticated;
grant select on table public.credit_ledger to authenticated;

-- Backfill wallets for accounts that existed before the wallet trigger was installed.
insert into public.wallets (user_id)
select id from auth.users
on conflict (user_id) do nothing;
