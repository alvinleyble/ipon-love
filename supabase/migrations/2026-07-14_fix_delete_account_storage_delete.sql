-- ============================================================================
--  Fix delete_account(): storage.objects has its own guard (v1.6.5, ADR-0045)
-- ============================================================================
--  delete_account() (2026-07-12) was failing in production: storage.objects
--  carries a BEFORE DELETE STATEMENT trigger (storage.protect_delete(),
--  Supabase-platform-managed, not ours) that raises unless the transaction-
--  local GUC `storage.allow_delete_query` is set to 'true' first. This applies
--  even to a SECURITY DEFINER function owned by postgres — ownership/role
--  bypasses RLS, not this trigger. The failed DELETE aborted the whole
--  transaction, so nothing was ever deleted (not even auth.users).
--
--  Fix: set the GUC `true, true` (transaction-local) immediately before the
--  storage delete, exactly as the trigger function itself expects.
-- ============================================================================

create or replace function delete_account()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then
        raise exception 'not authenticated';
    end if;

    if auth_couple_id() is not null then
        perform unpair();
    end if;

    perform set_config('storage.allow_delete_query', 'true', true);
    delete from storage.objects
        where bucket_id in ('receipts', 'note-images')
          and name like v_uid::text || '/%';

    delete from auth.users where id = v_uid;
end;
$$;

revoke all on function delete_account() from public, anon;
grant execute on function delete_account() to authenticated;
