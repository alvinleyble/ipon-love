-- Migration: unpair() broadcasts the live-sync bell (ADR-0015) from the database
-- Apply on the live Supabase project (vyjaorlevomfqkidttom) via the SQL Editor.
-- Idempotent: safe to re-run (create or replace function). No table/column changes.
--
-- Fixes: the partner who did NOT initiate an unpair had no way to learn the couple
-- dissolved except a coarse trigger (app resume / periodic background sync / manual
-- pull-to-refresh) -- every other sync change rides the live bell, unpair silently
-- didn't.
--
-- Scoped to the unpair() shape currently live (shared accounts/categories, ADR-0018) --
-- deliberately does NOT include the savings_goals revert block from the in-progress
-- ADR-0025 slice in schema.sql, since that table doesn't exist on this project yet.
-- When that slice ships its own migration, it redefines unpair() again and MUST carry
-- forward the realtime.send() call below.

begin;

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

commit;
