-- ============================================================================
--  Entitlement columns are write-locked at the database  (ADR-0060, v1.7.2 Item 1)
--
--  ADR-0044 made entitlement a client-trusted advisory column on the synced
--  `users` row, accepting that a rooted client could self-assert is_premium.
--  `users_update` is row-scoped with NO column restriction, so any authenticated
--  user may write every column on their own row — on Android that needs root or
--  MITM, but on the web app (W1) it is one dev-tools call, and the forged row
--  syncs back down to the phone through our own engine, unlocking the partner too
--  via `me.active || partner.active`.
--
--  This closes the SHAPE of that hole: the four entitlement columns lose direct
--  write access and become reachable only through set_self_entitlement(). The RPC
--  is a PASSTHROUGH today — it validates nothing about the purchase — so a forged
--  call still works. Real Play receipt validation is deliberately deferred
--  (ADR-0060 §5) and must land before enforcement flip-day, the web purchase path
--  (W7), or AI, whichever comes first.
--
--  Reads are untouched. Nothing to backfill — revoking a privilege does not alter
--  stored values, so existing beta comps keep working.
-- ============================================================================

-- ---- 1. Pin entitlement_source to its three legal values --------------------
-- Until now this was only a comment in schema.sql. Beta comps are hand-typed SQL
-- by the database owner, which is exactly the write that introduces a typo.
alter table users drop constraint if exists users_entitlement_source_check;
alter table users add constraint users_entitlement_source_check
    check (entitlement_source in ('PLAY', 'GRANT', 'NONE'));

-- ---- 2. Column allowlist for `authenticated` --------------------------------
-- IMPORTANT (the non-obvious part): a column-level REVOKE is a SILENT NO-OP when
-- the role holds the table-level privilege — Postgres lets the broader grant win,
-- with no error. Supabase grants `authenticated` table-level UPDATE by default,
-- so the plain
--     revoke update (is_premium, ...) on users from authenticated;
-- that this change was originally specified as would have changed nothing at all.
-- The table grant must be dropped first and the writable columns handed back.
--
-- This is therefore an ALLOWLIST, not a denylist: any column added to `users`
-- later is NOT writable by the app until it is added to the grant below. Adding a
-- users column now has two steps, and forgetting the second makes that field
-- silently stop syncing.
--
-- `anon` keeps its table-level grant deliberately: `users_update` is
-- `using (id = auth.uid())`, and auth.uid() is null for anon, so RLS already
-- refuses every anon write. Narrowing it here would be scope creep.
revoke update on users from authenticated;
grant update (
    id,
    display_name,
    avatar_url,
    accent_color,
    avatar_motif,
    couple_id,
    created_at,
    updated_at,
    server_rev
) on users to authenticated;

-- ---- 3. The sole write path for entitlement ---------------------------------
-- SECURITY DEFINER runs as the owner, so it is unaffected by the revoke above —
-- which is what makes it the only remaining door.
--
-- Passthrough by design (ADR-0060 §5): it writes what the client reports. What it
-- DOES enforce is the GRANT no-downgrade rule, which until now existed only as a
-- client-side early return — the web client will have its own reconcile loop, and
-- a beta comp silently wiped on a tester's account is easy to cause and annoying
-- to diagnose.
create or replace function set_self_entitlement(
    p_is_premium    boolean,
    p_premium_until timestamptz,
    p_source        text,
    p_checked_at    timestamptz,
    p_updated_at    timestamptz
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_current users%rowtype;
begin
    if p_source not in ('PLAY', 'GRANT', 'NONE') then
        raise exception 'invalid entitlement_source: %', p_source;
    end if;

    select * into v_current from users where id = auth.uid();
    if not found then
        -- Row not created yet (genuine new signup). The ordinary upsert in the same
        -- push creates it with the column defaults; nothing to reconcile onto.
        return;
    end if;

    -- ADR-0044 §4 / ADR-0060 §4: a beta comp (GRANT) must never be overwritten by a
    -- client's Play-derived state. Mirrors EntitlementRepositoryImpl's early return,
    -- now enforced on both sides of the boundary.
    if v_current.entitlement_source = 'GRANT' and p_source <> 'GRANT' then
        return;
    end if;

    -- Idempotent, for the same reason the client reconcile is (ADR-0044 §10.7 bug #1):
    -- the ordinary profile push calls this on EVERY dirty users row, so writing
    -- unconditionally would bump server_rev on every accent-colour change and make the
    -- partner re-pull the row each time. entitlement_checked_at rides along on a real
    -- change only — its value is diagnostic, never read by a gate.
    if v_current.is_premium         is not distinct from p_is_premium
       and v_current.premium_until  is not distinct from p_premium_until
       and v_current.entitlement_source is not distinct from p_source then
        return;
    end if;

    update users
       set is_premium             = p_is_premium,
           premium_until          = p_premium_until,
           entitlement_source     = p_source,
           entitlement_checked_at = p_checked_at,
           -- ADR-0001 keeps updated_at client-stamped and monotonic; greatest() is a
           -- floor so a stale or skewed client stamp can't move the LWW key backwards.
           updated_at             = greatest(p_updated_at, v_current.updated_at)
     where id = auth.uid();
end;
$$;

grant execute on function
    set_self_entitlement(boolean, timestamptz, text, timestamptz, timestamptz)
    to authenticated;
