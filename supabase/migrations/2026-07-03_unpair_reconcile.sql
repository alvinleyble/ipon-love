-- Migration: reconcile unpair() to its FINAL body — savings-goal revert (ADR-0025) AND the
-- realtime.send() live-sync bell (ADR-0015) both present.
-- Apply on the live Supabase project (vyjaorlevomfqkidttom) via the SQL Editor.
-- APPLIED & confirmed 2026-07-03. Idempotent: safe to re-run (create or replace, no data
-- changes). This unpair() body matches supabase/schema.sql exactly.
--
-- Why this exists: unpair() is create-or-replace, so the LAST definition run wins.
-- slice9_migration.sql (savings schema; its unpair() had the goal-revert but NO bell) ran
-- first, then 2026-07-03_unpair_broadcast_bell.sql (its unpair() had the bell but omitted
-- the goal-revert, scoped out because savings_goals wasn't live yet) ran second and replaced
-- the whole body — leaving live unpair() with the bell but MISSING the goal-revert. This
-- restores both. LESSON: any migration redefining unpair() must paste the CURRENT COMPLETE
-- body from schema.sql, never a partial snapshot.

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

    -- Shared savings goals revert to the creator's personal goals (ADR-0025), like notes.
    -- Goal contributions are untouched: each is owned by its contributor, and each client
    -- purges the OTHER partner's contribution replicas when it sees couple_id clear. Own
    -- contributions to an ex-partner's goal are left as benign, invisible orphans.
    update savings_goals set is_shared = false, couple_id = null, updated_at = now()
        where couple_id = v_couple_id and is_shared = true;

    -- Shared accounts & categories revert to their creator's personal rows (ADR-0018):
    -- the creator keeps the account with its balance/history; the other partner's client
    -- purges its replica (created_by <> self) when it sees couple_id clear.
    update accounts set user_id = created_by, couple_id = null, updated_at = now()
        where couple_id = v_couple_id and created_by is not null;

    update categories set user_id = created_by, couple_id = null, updated_at = now()
        where couple_id = v_couple_id and created_by is not null;

    -- Ring the live-sync bell (ADR-0015) from the DATABASE, inside this same transaction, so the
    -- partner who did NOT initiate the unpair pulls immediately instead of waiting for a coarse
    -- trigger (app resume / periodic background sync / manual pull-to-refresh). This is the only
    -- server-side broadcast in the schema, and it has to be one: the instant unpair() commits the
    -- initiator's own auth_couple_id() goes null (ADR-0008), so a CLIENT ping after the RPC is
    -- RLS-blocked by couple_channel_members, and pinging before the RPC races the mutation.
    -- realtime.send() runs inside this SECURITY DEFINER (postgres-owned => BYPASSRLS) function, so
    -- it is exempt from that per-broadcaster RLS by construction; emitting it BEFORE we null
    -- couple_id also keeps the channel predicate satisfied even if that exemption were ever removed.
    -- Payload is an EMPTY object: the ping carries zero row data (ADR-0015 redaction) and the
    -- partner reacts by PULLING through the redacting views (ADR-0005). private=true matches the
    -- client's isPrivate channel, and Realtime fans the message out only after COMMIT, so the
    -- partner never pulls before the mutation lands.
    perform realtime.send(
        '{}'::jsonb,                     -- payload: empty, content-less bell (ADR-0015)
        'changed',                       -- event: matches SupabaseCoupleBell.EVENT
        'couple:' || v_couple_id::text,  -- topic: this dissolving couple's channel
        true                             -- private: matches the client's isPrivate = true
    );

    update users set couple_id = null, updated_at = now() where couple_id = v_couple_id;

    update couples set is_deleted = true, updated_at = now() where id = v_couple_id;
end;
$$;
