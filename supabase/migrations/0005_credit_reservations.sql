-- FOTO FOTO migration 0005: atomic server-side credit reservations.
-- Run after migrations 0001-0004 in the FOTO FOTO Supabase project.
-- Only the trusted backend service role may execute the RPC functions.

create table if not exists public.credit_reservations (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  provider_task_id text unique,
  amount_credits bigint not null check (amount_credits > 0),
  description text not null default '',
  status text not null default 'reserved'
    check (status in ('reserved', 'charged', 'refunded')),
  created_at timestamptz not null default now(),
  settled_at timestamptz
);

create index if not exists credit_reservations_user_created_idx
  on public.credit_reservations(user_id, created_at desc);

alter table public.credit_reservations enable row level security;

drop policy if exists "Users can read their own credit reservations" on public.credit_reservations;
create policy "Users can read their own credit reservations"
  on public.credit_reservations for select
  using (auth.uid() = user_id);

create or replace function public.reserve_foto_credits(
  p_user_id uuid,
  p_reservation_id uuid,
  p_amount bigint,
  p_description text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_balance bigint;
begin
  if p_amount <= 0 then
    raise exception 'Invalid credit amount';
  end if;

  insert into public.wallets (user_id, balance_credits)
  values (p_user_id, 0)
  on conflict (user_id) do nothing;

  update public.wallets
     set balance_credits = balance_credits - p_amount,
         updated_at = now()
   where user_id = p_user_id
     and balance_credits >= p_amount
  returning balance_credits into v_balance;

  if not found then
    select balance_credits into v_balance
      from public.wallets where user_id = p_user_id;
    return jsonb_build_object('ok', false, 'balance', coalesce(v_balance, 0));
  end if;

  insert into public.credit_reservations
    (id, user_id, amount_credits, description, status)
  values
    (p_reservation_id, p_user_id, p_amount, coalesce(p_description, ''), 'reserved');

  insert into public.credit_ledger
    (user_id, amount_credits, entry_type, description, reference_id)
  values
    (p_user_id, -p_amount, 'video_charge',
     coalesce(p_description, 'Video üretimi için kredi ayırma'),
     p_reservation_id::text);

  return jsonb_build_object('ok', true, 'balance', v_balance);
end;
$$;

create or replace function public.attach_foto_task(
  p_reservation_id uuid,
  p_provider_task_id text
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
begin
  update public.credit_reservations
     set provider_task_id = p_provider_task_id
   where id = p_reservation_id and status = 'reserved'
     and provider_task_id is null;
  return found;
end;
$$;

create or replace function public.settle_foto_credits(
  p_provider_task_id text,
  p_success boolean
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_reservation public.credit_reservations%rowtype;
  v_balance bigint;
begin
  select * into v_reservation
    from public.credit_reservations
   where provider_task_id = p_provider_task_id
   for update;

  if not found then
    return jsonb_build_object('ok', false, 'reason', 'reservation_not_found');
  end if;

  if v_reservation.status <> 'reserved' then
    select balance_credits into v_balance
      from public.wallets where user_id = v_reservation.user_id;
    return jsonb_build_object('ok', true, 'status', v_reservation.status, 'balance', v_balance);
  end if;

  if p_success then
    update public.credit_reservations
       set status = 'charged', settled_at = now()
     where id = v_reservation.id;
    select balance_credits into v_balance
      from public.wallets where user_id = v_reservation.user_id;
    return jsonb_build_object('ok', true, 'status', 'charged', 'balance', v_balance);
  end if;

  update public.wallets
     set balance_credits = balance_credits + v_reservation.amount_credits,
         updated_at = now()
   where user_id = v_reservation.user_id
  returning balance_credits into v_balance;

  update public.credit_reservations
     set status = 'refunded', settled_at = now()
   where id = v_reservation.id;

  insert into public.credit_ledger
    (user_id, amount_credits, entry_type, description, reference_id)
  values
    (v_reservation.user_id, v_reservation.amount_credits, 'refund',
     'Başarısız üretim için kredi iadesi', v_reservation.id::text);

  return jsonb_build_object('ok', true, 'status', 'refunded', 'balance', v_balance);
end;
$$;

revoke all on function public.reserve_foto_credits(uuid, uuid, bigint, text) from public, anon, authenticated;
revoke all on function public.attach_foto_task(uuid, text) from public, anon, authenticated;
revoke all on function public.settle_foto_credits(text, boolean) from public, anon, authenticated;

grant execute on function public.reserve_foto_credits(uuid, uuid, bigint, text) to service_role;
grant execute on function public.attach_foto_task(uuid, text) to service_role;
grant execute on function public.settle_foto_credits(text, boolean) to service_role;
