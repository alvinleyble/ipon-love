-- Migration: gate the partner_goal_contributions view on live contributor membership (F3).
-- Apply on the live Supabase project (vyjaorlevomfqkidttom) via the SQL Editor.
-- Idempotent: `create or replace view` (column list/order unchanged). No data changes.
--
-- Why this exists: a savings goal keeps its contribution rows forever (contributions are benign
-- orphans after unpair — ADR-0025). The view redacted content only on deleted / goal-deleted /
-- goal-unshared, and its WHERE gate checked the goal's CURRENT couple_id but never whether the
-- CONTRIBUTOR is still a member of that couple. So re-sharing an old goal into a DIFFERENT couple
-- resurfaced an ex-partner's amount + note to the new partner and re-added it to the derived total
-- on both devices (privacy leak + silent miscount).
--
-- Fix: null amount/note/date when the contributor is no longer a member of the goal's couple
-- (`gc.user_id not in (select id from users where couple_id = g.couple_id)`). The Android mapper
-- already folds a null amount into is_deleted, so the partner syncer purges the stale replica —
-- same purge-signal path as unshare/delete. The legitimate same-couple unshare→reshare case is
-- untouched (the partner is still a member). Body matches supabase/schema.sql exactly.

create or replace view partner_goal_contributions with (security_invoker = false) as
    select
        gc.id,
        gc.goal_id,
        gc.user_id,
        case when gc.is_deleted or g.is_deleted or g.is_shared = false
                  or gc.user_id not in (select id from users where couple_id = g.couple_id)
             then null else gc.amount end as amount,
        case when gc.is_deleted or g.is_deleted or g.is_shared = false
                  or gc.user_id not in (select id from users where couple_id = g.couple_id)
             then null else gc.note   end as note,
        case when gc.is_deleted or g.is_deleted or g.is_shared = false
                  or gc.user_id not in (select id from users where couple_id = g.couple_id)
             then null else gc.date   end as date,
        gc.is_deleted,
        gc.updated_at,
        gc.server_rev
    from goal_contributions gc
    join savings_goals g on g.id = gc.goal_id
    where gc.user_id <> auth.uid()
      and g.couple_id = auth_couple_id();

grant select on partner_goal_contributions to authenticated;

-- ----------------------------------------------------------------------------
-- Manual verification (run as each account, or inspect with service_role):
--
--   Setup: Alvin owns goal G, shares it to couple AP; Patty contributes 500.
--          Unpair AP. Alvin pairs with Charlie, re-shares G into couple AC.
--
--   As Charlie, Patty's row must come back REDACTED (amount/note/date NULL) so
--   Charlie's client purges it and the total excludes Patty's 500:
--     select id, user_id, amount, note, date from partner_goal_contributions
--     where goal_id = '<G>';   -- Patty's row: amount/note/date all NULL
--
--   Regression guard — same-couple unshare→reshare must STILL show Patty's amount:
--     (Alvin+Patty; Alvin unshares then re-shares G to the SAME couple AP)
--     Patty's row: amount = 500, note intact (she is still a member of AP).
-- ----------------------------------------------------------------------------
