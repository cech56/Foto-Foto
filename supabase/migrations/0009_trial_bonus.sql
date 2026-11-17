-- One-time 59-credit trial grant for the designated FOTO FOTO test account.
-- Run this migration in Supabase SQL Editor before deploying/testing the endpoint.

create unique index if not exists credit_ledger_bonus_reference_once_idx
  on public.credit_ledger(user_id, reference_id)
  where entry_type = 'bonus' and reference_id is not null;

create or replace function public.grant_foto_trial_bonus(
  p_user_id uuid,
  p_amount bigint default 59
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_balance bigint;
  v_reference text := 'foto_foto_trial_bonus_59_v1';
begin
  if p_amount <> 59 then
    raise exception 'Invalid trial bonus amount';
  end if;

  insert into public.wallets(user_id, balance_credits)
  values (p_user_id, 0)
  on conflict (user_id) do nothing;

  perform 1 from public.wallets where user_id = p_user_id for update;

  if exists (
    select 1 from public.credit_ledger
    where user_id = p_user_id
      and entry_type = 'bonus'
      and reference_id = v_reference
  ) then
    select balance_credits into v_balance from public.wallets where user_id = p_user_id;
    return jsonb_build_object('ok', true, 'already_claimed', true, 'balance', v_balance);
  end if;

  update public.wallets
     set balance_credits = balance_credits + p_amount,
         updated_at = now()
   where user_id = p_user_id
  returning balance_credits into v_balance;

  insert into public.credit_ledger(user_id, amount_credits, entry_type, description, reference_id)
  values (p_user_id, p_amount, 'bonus', 'FOTO FOTO tek seferlik 59 kredi deneme hediyesi', v_reference);

  return jsonb_build_object('ok', true, 'granted', true, 'balance', v_balance);
end;
$$;

revoke all on function public.grant_foto_trial_bonus(uuid, bigint) from public, anon, authenticated;
grant execute on function public.grant_foto_trial_bonus(uuid, bigint) to service_role;
