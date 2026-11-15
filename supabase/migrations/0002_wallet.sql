-- FOTO FOTO wallet foundation. Run after 0001_profiles.sql in the FOTO FOTO Supabase project.
-- The mobile app may read its own balance and ledger only. All credit mutations
-- must be performed by a trusted backend using server-side credentials.

create table if not exists public.wallets (
  user_id uuid primary key references auth.users(id) on delete cascade,
  balance_credits bigint not null default 0 check (balance_credits >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.credit_ledger (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  amount_credits bigint not null check (amount_credits <> 0),
  entry_type text not null check (entry_type in ('purchase','video_charge','refund','bonus','adjustment')),
  description text not null default '',
  reference_id text,
  created_at timestamptz not null default now()
);

create index if not exists credit_ledger_user_created_idx
  on public.credit_ledger(user_id, created_at desc);

alter table public.wallets enable row level security;
alter table public.credit_ledger enable row level security;

drop policy if exists "Users can read their own wallet" on public.wallets;
create policy "Users can read their own wallet"
  on public.wallets for select
  using (auth.uid() = user_id);

drop policy if exists "Users can read their own credit history" on public.credit_ledger;
create policy "Users can read their own credit history"
  on public.credit_ledger for select
  using (auth.uid() = user_id);

-- Deliberately no client INSERT/UPDATE/DELETE policies.
-- Create one empty wallet for every new account.
create or replace function public.create_foto_wallet_for_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.wallets (user_id) values (new.id)
  on conflict (user_id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_wallet_created on auth.users;
create trigger on_auth_user_wallet_created
  after insert on auth.users
  for each row execute procedure public.create_foto_wallet_for_user();
