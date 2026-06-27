-- Migration: shared accounts & categories (V1.3 item #11, couple-owned) — ADR-0018
-- Apply on the live Supabase project (vyjaorlevomfqkidttom) via the SQL Editor.
-- Idempotent: safe to re-run. Existing rows (user_id set, couple_id null) satisfy the
-- new owner-check on the personal branch, so no data backfill is needed.

begin;

-- ---------- accounts: nullable user_id + couple_id/created_by + owner-check -----------
alter table accounts alter column user_id drop not null;
alter table accounts add column if not exists couple_id  uuid references couples(id) on delete cascade;
alter table accounts add column if not exists created_by uuid references users(id)   on delete set null;
alter table accounts drop constraint if exists account_owner_chk;
alter table accounts add  constraint account_owner_chk check (
    (user_id is not null and couple_id is null) or
    (user_id is null and couple_id is not null)
);

-- ---------- categories: same shape ----------------------------------------------------
alter table categories alter column user_id drop not null;
alter table categories add column if not exists couple_id  uuid references couples(id) on delete cascade;
alter table categories add column if not exists created_by uuid references users(id)   on delete set null;
alter table categories drop constraint if exists category_owner_chk;
alter table categories add  constraint category_owner_chk check (
    (user_id is not null and couple_id is null) or
    (user_id is null and couple_id is not null)
);

-- ---------- indexes on the new couple_id columns --------------------------------------
create index if not exists idx_accounts_couple   on accounts(couple_id);
create index if not exists idx_categories_couple on categories(couple_id);

-- ---------- no private spend on a shared account (ADR-0018) ---------------------------
-- A CHECK can't span tables, so a BEFORE trigger backs up TransactionValidator. Covers
-- both the source (account_id) and a transfer's destination (to_account_id).
create or replace function enforce_no_private_on_shared_account()
returns trigger
language plpgsql
as $$
begin
    if new.is_private and new.is_deleted = false
       and exists (
           select 1 from accounts
           where id in (new.account_id, new.to_account_id)
             and couple_id is not null
       ) then
        raise exception 'private transactions are not allowed on a shared account';
    end if;
    return new;
end;
$$;

drop trigger if exists trg_no_private_on_shared on transactions;
create trigger trg_no_private_on_shared
    before insert or update on transactions
    for each row execute function enforce_no_private_on_shared_account();

-- ---------- couple-owned RLS policies (mirror budgets_couple) -------------------------
-- Couple-owned rows replicate to both partners through the BASE-table pull (these
-- policies return them), NOT the redacting partner_* views, so opening_balance crosses
-- and the joint balance is computable. [ADR-0018]
drop policy if exists accounts_couple on accounts;
create policy accounts_couple on accounts for all
    using (couple_id = auth_couple_id())
    with check (couple_id = auth_couple_id());

drop policy if exists categories_couple on categories;
create policy categories_couple on categories for all
    using (couple_id = auth_couple_id())
    with check (couple_id = auth_couple_id());

-- ---------- harden partner views: couple-owned rows must NOT leak through here --------
create or replace view partner_accounts with (security_invoker = false) as
    select
        a.id,
        a.user_id,
        case when a.is_deleted then null else a.name        end as name,
        case when a.is_deleted then null else a.type        end as type,
        case when a.is_deleted then null else a.icon        end as icon,
        case when a.is_deleted then null else a.color       end as color,
        case when a.is_deleted then null else a.is_archived end as is_archived,
        -- opening_balance deliberately omitted: partner balances are not shown. [ADR-0011]
        a.is_deleted,
        a.updated_at,
        a.server_rev
    from accounts a
    where a.couple_id is null              -- couple-owned accounts cross via the base table (ADR-0018), not here
      and a.user_id <> auth.uid()
      and a.user_id in (select id from users where couple_id = auth_couple_id());

create or replace view partner_categories with (security_invoker = false) as
    select
        c.id,
        c.user_id,
        case when c.is_deleted then null else c.name        end as name,
        case when c.is_deleted then null else c.type        end as type,
        case when c.is_deleted then null else c.icon        end as icon,
        case when c.is_deleted then null else c.color       end as color,
        case when c.is_deleted then null else c.is_archived end as is_archived,
        c.is_deleted,
        c.updated_at,
        c.server_rev
    from categories c
    where c.couple_id is null              -- couple-owned categories cross via the base table (ADR-0018), not here
      and c.user_id <> auth.uid()
      and c.user_id in (select id from users where couple_id = auth_couple_id());

-- ---------- unpair(): revert shared accounts & categories to creator -----------------
create or replace function unpair()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_couple_id uuid := auth_couple_id();
begin
    if v_couple_id is null then
        raise exception 'not in a couple';
    end if;
    -- guard: caller must actually be a member
    if not exists (
        select 1 from couples
        where id = v_couple_id and (user1_id = auth.uid() or user2_id = auth.uid())
    ) then
        raise exception 'not a member of this couple';
    end if;

    update budgets set is_deleted = true, updated_at = now()
        where couple_id = v_couple_id and is_deleted = false;

    update partner_debts set is_deleted = true, updated_at = now()
        where couple_id = v_couple_id and is_deleted = false;

    update notes set is_shared = false, couple_id = null, updated_at = now()
        where couple_id = v_couple_id and is_shared = true;

    -- Shared accounts & categories revert to their creator's personal rows (ADR-0018):
    -- the creator keeps the account with its balance/history; the other partner's client
    -- purges its replica (created_by <> self) when it sees couple_id clear.
    update accounts set user_id = created_by, couple_id = null, updated_at = now()
        where couple_id = v_couple_id and created_by is not null;

    update categories set user_id = created_by, couple_id = null, updated_at = now()
        where couple_id = v_couple_id and created_by is not null;

    update users set couple_id = null, updated_at = now() where couple_id = v_couple_id;

    update couples set is_deleted = true, updated_at = now() where id = v_couple_id;
end;
$$;

commit;
