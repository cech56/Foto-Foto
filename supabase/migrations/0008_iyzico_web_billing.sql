-- FOTO FOTO iyzico Checkout Form and wallet settlement.
-- Apply after 0007. Existing PayTR records/functions are retained for audit but no longer used.

alter table public.billing_products drop constraint if exists billing_products_channel_check;
alter table public.billing_products add constraint billing_products_channel_check check (channel in ('iyzico_web'));
alter table public.payment_orders drop constraint if exists payment_orders_channel_check;
alter table public.payment_orders add constraint payment_orders_channel_check check (channel in ('iyzico_web'));
alter table public.payment_orders add column if not exists iyzico_token text;
alter table public.payment_orders add column if not exists iyzico_payment_id text;
create unique index if not exists payment_orders_iyzico_token_uidx on public.payment_orders(iyzico_token) where iyzico_token is not null;
create unique index if not exists payment_orders_iyzico_payment_uidx on public.payment_orders(iyzico_payment_id) where iyzico_payment_id is not null;

create or replace function public.settle_iyzico_order(
  p_token text,
  p_payment_id text,
  p_payment_status text,
  p_paid_price numeric
) returns jsonb
language plpgsql security definer set search_path = ''
as $$
declare
  v_order public.payment_orders%rowtype;
  v_balance bigint;
begin
  select * into v_order from public.payment_orders where iyzico_token = p_token for update;
  if not found then return jsonb_build_object('ok', false, 'reason', 'order_not_found'); end if;
  if v_order.status = 'paid' then
    select balance_credits into v_balance from public.wallets where user_id = v_order.user_id;
    return jsonb_build_object('ok', true, 'status', 'paid', 'duplicate', true, 'balance', coalesce(v_balance, 0));
  end if;
  if v_order.status <> 'pending' then
    return jsonb_build_object('ok', false, 'reason', 'order_not_pending', 'status', v_order.status);
  end if;
  if p_payment_status <> 'SUCCESS' then
    update public.payment_orders set status = 'failed', updated_at = now() where id = v_order.id;
    return jsonb_build_object('ok', true, 'status', 'failed');
  end if;
  if p_payment_id is null or p_payment_id = '' or round(p_paid_price * 100) <> v_order.amount_minor then
    raise exception 'iyzico payment amount or payment id mismatch for token %', p_token;
  end if;
  insert into public.wallets(user_id, balance_credits) values (v_order.user_id, 0) on conflict(user_id) do nothing;
  update public.wallets set balance_credits = balance_credits + v_order.credits_to_grant, updated_at = now()
   where user_id = v_order.user_id returning balance_credits into v_balance;
  insert into public.credit_ledger(user_id, amount_credits, entry_type, description, reference_id)
  values (v_order.user_id, v_order.credits_to_grant, 'purchase',
    'iyzico kredi paketi satın alımı: ' || v_order.product_id, p_payment_id);
  update public.payment_orders set status = 'paid', iyzico_payment_id = p_payment_id,
    paytr_total_amount = null, paid_at = now(), updated_at = now() where id = v_order.id;
  return jsonb_build_object('ok', true, 'status', 'paid', 'balance', v_balance);
end;
$$;

revoke all on function public.settle_iyzico_order(text,text,text,numeric) from public, anon, authenticated;
grant execute on function public.settle_iyzico_order(text,text,text,numeric) to service_role;
