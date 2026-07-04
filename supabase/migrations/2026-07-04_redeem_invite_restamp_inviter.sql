-- Migration: redeem_invite() now re-stamps the INVITER's users row so the redeemer actually
-- pulls the partner's user row into local Room.
-- Apply on the live Supabase project (vyjaorlevomfqkidttom) via the SQL Editor.
-- Idempotent: create-or-replace, no data changes. This body matches supabase/schema.sql exactly.
--
-- Bug this fixes: the person who ENTERED an invite code (the redeemer, user2) never received
-- the inviter's (user1's) users row, so CoupleMembers.partner stayed null for them — degrading
-- combined-view name/color attribution AND hiding the "Paid for partner" toggle in Add
-- Transaction (which is gated on a known partner id).
--
-- Why: the users table pulls incrementally with `server_rev > cursor` and the cursor only
-- moves forward (ADR-0002). By the time user2 redeems, their users cursor already sits at
-- their own row's server_rev. redeem_invite touched only user2's own row + the couples row,
-- leaving user1's row at its PRE-pairing server_rev — below user2's cursor — so user2's next
-- pull (server_rev > cursor) skipped it forever (until user1 happened to edit their profile).
-- Re-stamping user1's row here bumps its server_rev via nextval above user2's cursor, and the
-- post-RPC syncEngine.sync() in CoupleRepositoryImpl then pulls it immediately.
--
-- `updated_at = updated_at` fires trg_rev_users (server_rev = nextval) WITHOUT changing the
-- client-authoritative LWW key (ADR-0001) — so it can never clobber an unpushed local profile
-- edit on the inviter's device; it only makes the row re-pullable.
--
-- LESSON (from 2026-07-03_unpair_reconcile): create-or-replace means the LAST definition run
-- wins, so this migration pastes the CURRENT COMPLETE redeem_invite body, never a partial snapshot.

create or replace function redeem_invite(p_code text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_couple couples%rowtype;
begin
    if (select couple_id from users where id = auth.uid()) is not null then
        raise exception 'already in a couple';
    end if;

    select * into v_couple from couples
        where invite_code = p_code and is_deleted = false
        for update;

    if not found then
        raise exception 'invalid invite code';
    end if;
    if v_couple.user2_id is not null then
        raise exception 'couple is already full';
    end if;
    if v_couple.user1_id = auth.uid() then
        raise exception 'cannot join your own couple';
    end if;

    update couples set user2_id = auth.uid(), updated_at = now() where id = v_couple.id;
    update users   set couple_id = v_couple.id, updated_at = now() where id = auth.uid();

    -- Re-stamp the inviter's (user1's) row so its server_rev jumps ABOVE the redeemer's
    -- already-advanced users pull cursor (see header). `updated_at = updated_at` bumps
    -- server_rev via trg_rev_users without disturbing the LWW key (ADR-0001).
    update users   set updated_at = updated_at where id = v_couple.user1_id;
    return v_couple.id;
end;
$$;
