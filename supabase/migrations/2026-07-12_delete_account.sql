-- ============================================================================
--  Delete account (v1.6.5 Item 6, ADR-0045) — Google Play User Data policy
-- ============================================================================
--  A departing user is the ONE sanctioned exception to ADR-0010 (never hard
--  delete): this hard-deletes the caller's auth.users row and lets the verified
--  ON DELETE CASCADE graph physically remove their entire account, in a single
--  atomic transaction. Idempotent (create or replace) — safe to re-run.
--
--  Order matters:
--    1. If paired, dissolve the couple via the EXISTING unpair() — reverts shared
--       rows to their creator, rings the realtime bell, nulls both couple_ids —
--       so the partner is cleaned up the normal way BEFORE the cascade fires.
--       (When the leaver is user1, the final delete cascade-drops the couples row
--       and its couple-owned tombstones; harmless, because the partner reacts to
--       their OWN users.couple_id going null + the bell, not to pulling those
--       tombstones — see ADR-0045 and WatchUnpairUseCase.)
--    2. Delete the caller's Storage object rows (receipts + note-images under the
--       {uid}/ prefix). The FK cascade misses storage.objects (it has no FK to
--       auth.users), so this is explicit; it makes every receipt/note image
--       permanently unreachable in the same transaction. Physical blobs are then
--       orphaned but inaccessible (private buckets, owner auth gone) — acceptable
--       per ADR-0045; the client is always online for this call (offline-gated).
--    3. Delete auth.users → cascade removes public.users + every owned row
--       (accounts, categories, transactions, budgets, notes, recurring_rules,
--       savings_goals, goal_contributions, partner_debts + child image/payment
--       tables, analytics_events) AND GoTrue's own sessions/refresh_tokens/
--       identities. This is the whole deletion — no per-table wipe is written.
--
--  SECURITY DEFINER, owned by postgres (which holds DELETE on auth.users and
--  storage.objects — both verified live 2026-07-12), so it reaches rows the
--  caller's own RLS cannot. auth.uid() is read from the request JWT and is
--  unaffected by the definer role, exactly like unpair().
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

    -- 1. Dissolve the couple first, via the tested unpair path (partner cleanup + bell).
    --    Guarded so unpair()'s own "not in a couple" raise never fires.
    if auth_couple_id() is not null then
        perform unpair();
    end if;

    -- 2. Storage objects — the FK cascade can't reach these (no FK to auth.users).
    delete from storage.objects
        where bucket_id in ('receipts', 'note-images')
          and name like v_uid::text || '/%';

    -- 3. The single delete that matters: the cascade physically removes everything else.
    delete from auth.users where id = v_uid;
end;
$$;

-- Destructive: keep it off anon. The internal auth.uid() guard is the real gate
-- (matching the pairing RPCs, which rely on their guard alone); this explicit
-- grant is cheap defense-in-depth given what the function does.
revoke all on function delete_account() from public, anon;
grant execute on function delete_account() to authenticated;
