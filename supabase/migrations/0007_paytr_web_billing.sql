-- FOTO FOTO PayTR web checkout foundation.
-- Run after migrations 0001-0006 in the FOTO FOTO Supabase project.
-- No products are seeded: prices/credits must be commercially approved before sales are enabled.

create table if not exists public.billing_products (
  product_id text primary key,
  product_type text not null check (product_type in ('credit_pack')),
  title text not null,
  description text not null default '',
  channel text not null check (channel in ('paytr_web')),
  region text not null default 'TR',
  currency text not null default 'TRY',
  price_minor bigint not null check (price_minor > 0),
  credits bigint not null check (credits > 0),
  active boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.payment_orders (
  id uuid primary key default gen_random_uuid(),
  merchant_oid text not null unique,
  user_id uuid not null references auth.users(id) on delete cascade,
  product_id text not null references public.billing_products(product_id),
  channel text not null default 'paytr_web' check (channel = 'paytr_web'),
  amount_minor bigint not null check (amount_minor > 0),
  currency text not null default 'TRY',
  credits_to_grant bigint not null check (credits_to_grant > 0),
  status text not null default 'pending'
    check (status in ('pending','paid','failed','refunded','disputed')),
  paytr_total_amount bigint,
  payment_type text,
  failure_code text,
  failure_message text,
  created_at timestamptz not null default now(),
  paid_at timestamptz,
  updated_at timestamptz not null default now()
);

create index if not exists payment_orders_user_created_idx
  on public.payment_orders(user_id, created_at desc);

create table if not exists public.payment_events (
  id uuid primary key default gen_random_uuid(),
  provider text not null,
  provider_event_id text not null,
  event_type text not null,
  payload_hash text not null,
  received_at timestamptz not null default now(),
  processed_at timestamptz,
  processing_status text not null default 'received'
    check (processing_status in ('received','processed','ignored','failed')),
  failure_reason text,
  unique(provider, provider_event_id)
);

alter table public.billing_products enable row level security;
alter table public.payment_orders enable row level security;
alter table public.payment_events enable row level security;

drop policy if exists "Users can read active billing products" on public.billing_products;
create policy "Users can read active billing products"
  on public.billing_products for select to authenticated
  using (active = true);

drop policy if exists "Users can read their own payment orders" on public.payment_orders;
create policy "Users can read their own payment orders"
  on public.payment_orders for select to authenticated
  using (auth.uid() = user_id);

grant select on public.billing_products to authenticated;
grant select on public.payment_orders to authenticated;
revoke all on public.payment_events from anon, authenticated;
revoke insert, update, delete on public.billing_products from anon, authenticated;
revoke insert, update, delete on public.payment_orders from anon, authenticated;

alter table public.credit_ledger
  drop constraint if exists credit_ledger_entry_type_check;
alter table public.credit_ledger
  add constraint credit_ledger_entry_type_check
  check (entry_type in ('purchase','video_charge','refund','bonus','adjustment','purchase_reversal'));

create or replace function public.settle_paytr_order(
  p_merchant_oid text,
  p_status text,
  p_total_amount bigint,
  p_payment_type text,
  p_failure_code text,
  p_failure_message text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_order public.payment_orders%rowtype;
  v_balance bigint;
begin
  select * into v_order
    from public.payment_orders
   where merchant_oid = p_merchant_oid
   for update;

  if not found then
    return jsonb_build_object('ok', false, 'reason', 'order_not_found');
  end if;

  if v_order.status in ('paid','failed','refunded','disputed') then
    select balance_credits into v_balance
      from public.wallets where user_id = v_order.user_id;
    return jsonb_build_object('ok', true, 'status', v_order.status,
      'duplicate', true, 'balance', coalesce(v_balance, 0));
  end if;

  if p_status = 'success' then
    -- No-installment checkout is used; reject any callback whose amount differs.
    if p_total_amount <> v_order.amount_minor then
      raise exception 'PayTR amount mismatch for order %', p_merchant_oid;
    end if;

    insert into public.wallets(user_id, balance_credits)
      values(v_order.user_id, 0)
      on conflict(user_id) do nothing;

    update public.wallets
       set balance_credits = balance_credits + v_order.credits_to_grant,
           updated_at = now()
     where user_id = v_order.user_id
     returning balance_credits into v_balance;

    insert into public.credit_ledger
      (user_id, amount_credits, entry_type, description, reference_id)
    values
      (v_order.user_id, v_order.credits_to_grant, 'purchase',
       'PayTR üzerinden kredi paketi satın alımı: ' || v_order.product_id,
       v_order.merchant_oid);

    update public.payment_orders
       set status = 'paid', paytr_total_amount = p_total_amount,
           payment_type = p_payment_type, paid_at = now(), updated_at = now()
     where id = v_order.id;

    return jsonb_build_object('ok', true, 'status', 'paid', 'balance', v_balance);
  end if;

  update public.payment_orders
     set status = 'failed', paytr_total_amount = p_total_amount,
         payment_type = p_payment_type, failure_code = p_failure_code,
         failure_message = left(coalesce(p_failure_message, ''), 500),
         updated_at = now()
   where id = v_order.id;

  select balance_credits into v_balance
    from public.wallets where user_id = v_order.user_id;
  return jsonb_build_object('ok', true, 'status', 'failed', 'balance', coalesce(v_balance, 0));
end;
$$;

revoke all on function public.settle_paytr_order(text,text,bigint,text,text,text)
  from public, anon, authenticated;
grant execute on function public.settle_paytr_order(text,text,bigint,text,text,text)
  to service_role;
