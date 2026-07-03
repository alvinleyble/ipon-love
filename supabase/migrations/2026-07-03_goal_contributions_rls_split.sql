-- Migration: split the goal_contributions RLS policy so goal-membership is re-validated on
-- INSERT ONLY (F1). Apply on the live Supabase project (vyjaorlevomfqkidttom) via the SQL Editor.
-- Idempotent: drops the old policy + any of the new ones first, then recreates. No data changes.
--
-- Why this exists: the original policy was `for all ... with check (goal membership)`, so the
-- membership sub-select re-ran on EVERY write, INSERT and UPDATE alike. When an owner unshares a
-- goal (or the couple unpairs), a non-owner's already-authored contribution row no longer
-- satisfies that check. Their next sync's bulk upsert is RLS-rejected → throws → and the author
-- cannot even tombstone the poisoned row (the same check rejects the UPDATE), wedging their whole
-- sync until app-data is cleared. Splitting the policy lets an author always UPDATE/DELETE their
-- OWN row (ownership-only), while INSERT still requires the target goal be accessible so a partner
-- can only fund a goal that is genuinely shared to them. Body matches supabase/schema.sql exactly.

drop policy if exists goal_contributions_author on goal_contributions;
drop policy if exists goal_contributions_select on goal_contributions;
drop policy if exists goal_contributions_insert on goal_contributions;
drop policy if exists goal_contributions_update on goal_contributions;
drop policy if exists goal_contributions_delete on goal_contributions;

create policy goal_contributions_select on goal_contributions for select
    using (user_id = auth.uid());
create policy goal_contributions_insert on goal_contributions for insert
    with check (
        user_id = auth.uid()
        and goal_id in (
            select id from savings_goals
            where user_id = auth.uid()
               or (couple_id = auth_couple_id() and is_shared)
        )
    );
create policy goal_contributions_update on goal_contributions for update
    using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy goal_contributions_delete on goal_contributions for delete
    using (user_id = auth.uid());
