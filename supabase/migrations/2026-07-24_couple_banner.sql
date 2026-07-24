-- v1.7.0 Item 10 — premium couple photo (banner) [ADR-0006/0008 couples-are-RPC-only]
-- Adds couples.banner_url + a set_couple_banner RPC + a private couple-banners Storage bucket
-- with one RLS policy keyed on auth_couple_id(), and folds banner cleanup into unpair().
-- Room v28 -> v29 (plain @AutoMigration, nullable). Ships dormant behind the paywall kill-switch.

-- ---- 1. column ----------------------------------------------------------------
-- Metadata-only add: a plain nullable column, no row rewrite, does not fire the
-- server_rev trigger, so existing couples are not re-synced. null = no photo.
alter table couples add column if not exists banner_url text;

-- ---- 2. RPC -------------------------------------------------------------------
-- Set (or clear, with null) the caller's couple banner. Member-gated exactly like
-- rotate_invite_code; the updated_at bump fires the couples server_rev trigger so the
-- new URL pulls back into both partners' Room (either partner may set it — D1).
create or replace function set_couple_banner(p_url text)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    update couples set banner_url = p_url, updated_at = now()
        where id = auth_couple_id() and (user1_id = auth.uid() or user2_id = auth.uid());
    if not found then
        raise exception 'no couple to update';
    end if;
end;
$$;

-- ---- 3. Storage bucket + RLS --------------------------------------------------
-- Private bucket; path is {couple_id}/{uuid}.jpg so folder[1] = couple_id is the RLS
-- key (mirrors receipts' folder[1] = userId). Both partners resolve the same
-- auth_couple_id() (a SECURITY DEFINER function, so no base-table RLS trap, ADR-0043),
-- so either can read AND write the couple's banner. One policy covers all operations.
insert into storage.buckets (id, name, public)
values ('couple-banners', 'couple-banners', false)
on conflict (id) do nothing;

drop policy if exists couple_banners_rw on storage.objects;
create policy couple_banners_rw on storage.objects for all
    to authenticated
    using (
        bucket_id = 'couple-banners'
        and (storage.foldername(name))[1] = auth_couple_id()::text
    )
    with check (
        bucket_id = 'couple-banners'
        and (storage.foldername(name))[1] = auth_couple_id()::text
    );

-- ---- 4. unpair() cleanup ------------------------------------------------------
-- Delete the couple's banner object when the couple dissolves (decision 6). unpair() is
-- the common orphan path and is already postgres-owned, so it holds the DELETE privilege;
-- the storage.allow_delete_query GUC (Supabase-platform BEFORE-DELETE guard, per
-- delete_account) must be set first. delete_account() inherits this via its unpair() call
-- (an orphan only exists while in a couple, and unpair clears it), so no separate clause is
-- needed there. An orphaned banner is already unreadable post-unpair (auth_couple_id() -> null
-- never matches the folder key) — this is hygiene, not a leak fix.
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

    update savings_goals set is_shared = false, couple_id = null, updated_at = now()
        where couple_id = v_couple_id and is_shared = true;

    update accounts set user_id = created_by, couple_id = null, updated_at = now()
        where couple_id = v_couple_id and created_by is not null;

    update categories set user_id = created_by, couple_id = null, updated_at = now()
        where couple_id = v_couple_id and created_by is not null;

    -- Ring the live-sync bell (ADR-0015) inside this transaction before couple_id clears.
    perform realtime.send(
        '{}'::jsonb,
        'changed',
        'couple:' || v_couple_id::text,
        true
    );

    -- Delete the couple's banner object(s) before the couple row is gone (Item 10, decision 6).
    perform set_config('storage.allow_delete_query', 'true', true);
    delete from storage.objects
        where bucket_id = 'couple-banners'
          and name like v_couple_id::text || '/%';

    update users set couple_id = null, updated_at = now() where couple_id = v_couple_id;

    update couples set is_deleted = true, updated_at = now() where id = v_couple_id;
end;
$$;
