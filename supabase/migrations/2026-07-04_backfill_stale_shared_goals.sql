-- Migration: one-time backfill for savings goals stuck "shared" after an unpair that
-- predates the goal-revert fix.
-- Apply on the live Supabase project (vyjaorlevomfqkidttom) via the SQL Editor.
-- Idempotent: safe to re-run (WHERE clause only matches rows still in the bad state).
--
-- Why this exists: 2026-07-03_unpair_reconcile.sql restored the `update savings_goals
-- set is_shared = false, couple_id = null ...` clause inside unpair() (ADR-0025), but
-- that only fixes FUTURE calls to the function. Any couple that unpaired while the live
-- unpair() was missing that clause (the window described in that migration's own
-- comment) could be left with a `savings_goals` row still carrying `is_shared = true`
-- and a `couple_id` pointing at an already-dissolved couple, uncorrectable by any client
-- sync since the server-side data itself was never updated.
--
-- Investigated via Alvin's own "Trip to Japan" goal, which displayed as Shared
-- post-unpair. Verified live on 2026-07-04: that specific row was NOT actually stale
-- server-side (is_shared was already false, updated_at 2026-07-03 17:45 -- reverted
-- correctly, likely by the same unpair() call that the reconcile migration's fix
-- landed around). Running this statement against it matched 0 rows. The client-side
-- display was just a locally cached row waiting on its next sync pull to pick up the
-- already-correct server_rev.
--
-- Kept as a general-purpose safety net rather than discarded: multiple test accounts
-- (testdev2-5) exercised pairing/unpairing during the same window the live unpair()
-- was missing this clause, so another goal from a different couple could still be
-- genuinely stale server-side even though this particular one wasn't. Safe to run
-- regardless -- it only touches rows actually matching the bad state.
--
-- This mirrors exactly what unpair() itself now does for a dissolved couple, scoped to
-- goals whose couple was already torn down (couples.is_deleted = true).
update savings_goals sg
set is_shared = false,
    couple_id = null,
    updated_at = now()
from couples c
where sg.couple_id = c.id
  and c.is_deleted = true
  and sg.is_shared = true;
