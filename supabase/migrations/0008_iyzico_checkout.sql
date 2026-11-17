-- FOTO FOTO migration 0008: switch checkout catalog/orders from PayTR to iyzico Checkout Form.
-- Run after 0007_paytr_web_billing.sql. Safe for projects where 0007 was already applied.

alter table public.billing_products
  drop constraint if exists billing_products_channel_check;
alter table public.billing_products
  add constraint billing_products_channel_check
  check (channel in ('paytr_web', 'iyzico_checkout'));

alter table public.payment_orders
  drop constraint if exists payment_orders_channel_check;
alter table public.payment_orders
  add constraint payment_orders_channel_check
  check (channel in ('paytr_web', 'iyzico_checkout'));

alter table public.payment_orders
  add column if not exists iyzico_token text,
  add column if not exists iyzico_payment_id text,
  add column if not exists basket_id text;

create unique index if not exists payment_orders_iyzico_token_uidx
  on public.payment_orders(iyzico_token)
  where iyzico_token is not null;

-- Move the catalog to the newly selected provider. Keep products inactive until
-- approved prices and credit quantities are configured by the owner.
update public.billing_products
   set channel = 'iyzico_checkout',
       active = false,
       updated_at = now();

create or replace function public.settle_iyzico_order(
  p_conversation_id text,
  p_token text,
  p_payment_id text,
  p_price numeric,
  p_paid_price numeric,
  p_currency text,
  p_basket_id text
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
   where merchant_oid = p_conversation_id
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

  if v_order.channel <> 'iyzico_checkout'
     or v_order.iyzico_token is distinct from p_token
     or v_order.currency <> p_currency
     or v_order.basket_id is distinct from p_basket_id
     or round(p_price * 100) <> v_order.amount_minor
     or round(p_paid_price * 100) <> v_order.amount_minor then
    raise exception 'iyzico payment details do not match order %', p_conversation_id;
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
     'iyzico üzerinden kredi paketi satın alımı: ' || v_order.product_id,
     p_conversation_id);

  update public.payment_orders
     set status = 'paid', iyzico_payment_id = p_payment_id,
         paid_at = now(), updated_at = now()
   where id = v_order.id;

  return jsonb_build_object('ok', true, 'status', 'paid', 'balance', v_balance);
end;
$$;

revoke all on function public.settle_iyzico_order(text,text,text,numeric,numeric,text,text)
  from public, anon, authenticated;
grant execute on function public.settle_iyzico_order(text,text,text,numeric,numeric,text,text)
  to service_role;
