-- Migration: shared savings goals base schema (V1.5 slice 9, ADR-0025) — F9
-- Apply on the live Supabase project (vyjaorlevomfqkidttom) via the SQL Editor.
-- Idempotent: `create table if not exists` / `create index if not exists` /
-- `drop policy if exists` + create / `create or replace view`. No data changes.
--
-- Why this exists: this DDL was originally applied live as an ad-hoc, uncommitted
-- "slice9_migration.sql" run through the SQL Editor (referenced by name in the commit
-- messages for 2026-07-03_unpair_broadcast_bell.sql and 2026-07-03_unpair_reconcile.sql)
-- rather than being checked into supabase/migrations/. schema.sql was always the source
-- of truth and matches what's live, but a fresh environment could not be replayed from
-- migrations/ alone. This file backfills that gap (F9, docs/v1.5-post-ship-audit.md).
--
-- SEQUENCING: this is the ORIGINAL slice-9 shape — goal_contributions has one combined
-- `for all` policy, and partner_goal_contributions has no contributor-membership check.
-- It must be treated as applying BEFORE (chronologically earlier than) the other
-- 2026-07-03 savings migrations, which patch this base on the live project in this order:
--   1. (this file)                                   — create the tables/RLS/views
--   2. 2026-07-03_unpair_broadcast_bell.sql            — unrelated to this table, no-op here
--   3. 2026-07-03_unpair_reconcile.sql                 — unrelated to this table, no-op here
--   4. 2026-07-03_goal_contributions_rls_split.sql      — splits goal_contributions_author (F1)
--   5. 2026-07-03_partner_goal_contributions_membership.sql — adds the membership check (F3)
-- On a fresh environment, apply all five in that order (or simply run schema.sql directly,
-- which already reflects the fully patched end state).

begin;

-- ---------- savings_goals ---------------------------------------------------
-- Personal-by-default savings goal, optionally shared to the couple via the generic
-- sharing layer (is_shared + couple_id), exactly like notes. saved_amount is NOT a column:
-- it is DERIVED from goal_contributions (ADR-0025), so concurrent contributions from both
-- partners never clobber a shared mutable counter under row-level LWW (ADR-0001/0007).
-- Metadata (name/target/date/icon/color) is creator-owned; only the creator edits it.
-- Un-sharing sets is_shared=false but RETAINS couple_id so the transition still reaches the
-- partner's redacting view as a purge signal (ADR-0005); only unpair nulls couple_id.
create table if not exists savings_goals (
    id            uuid primary key default gen_random_uuid(),
    user_id       uuid not null references users(id) on delete cascade,   -- creator / owner
    couple_id     uuid references couples(id) on delete set null,
    is_shared     boolean not null default false,
    name          text not null,
    target_amount numeric(14,2) not null,
    target_date   date,                                                   -- optional deadline
    icon          text,
    color         text,
    is_archived   boolean not null default false,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    is_deleted    boolean not null default false,
    server_rev    bigint
);

-- ---------- goal_contributions ----------------------------------------------
-- Append-only ledger backing a goal's DERIVED saved_amount. Each row is owned by its
-- CONTRIBUTOR (user_id), so on a shared goal BOTH partners contribute, each writing their
-- own independent rows — distinct ids never conflict under LWW (ADR-0025), which a shared
-- stored counter would. No couple_id / is_shared column: a contribution inherits its
-- shared-ness from its parent goal (single source of truth), gated via the join in
-- partner_goal_contributions below.
create table if not exists goal_contributions (
    id         uuid primary key default gen_random_uuid(),
    goal_id    uuid not null references savings_goals(id) on delete cascade,
    user_id    uuid not null references users(id) on delete cascade,   -- contributor
    amount     numeric(14,2) not null,
    note       text,
    date       timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    is_deleted boolean not null default false,
    server_rev bigint
);

-- ---------- server_rev triggers ----------------------------------------------
drop trigger if exists trg_rev_savings_goals on savings_goals;
create trigger trg_rev_savings_goals      before insert or update on savings_goals       for each row execute function set_server_rev();

drop trigger if exists trg_rev_goal_contributions on goal_contributions;
create trigger trg_rev_goal_contributions before insert or update on goal_contributions  for each row execute function set_server_rev();

-- ---------- Indexes (sync cursor + common queries) --------------------------
create index if not exists idx_savings_goals_rev        on savings_goals(server_rev);
create index if not exists idx_goal_contributions_rev   on goal_contributions(server_rev);
create index if not exists idx_savings_goals_user        on savings_goals(user_id);
create index if not exists idx_savings_goals_couple      on savings_goals(couple_id);
create index if not exists idx_goal_contributions_goal   on goal_contributions(goal_id);
create index if not exists idx_goal_contributions_user   on goal_contributions(user_id);

-- ---------- Row Level Security ------------------------------------------------
alter table savings_goals           enable row level security;
alter table goal_contributions      enable row level security;

-- ---- savings_goals ---------------------------------------------------------
-- Writes are owner-only (the creator owns the metadata — name/target/date/icon/color).
-- SELECT is broader — own goals PLUS goals shared into the caller's couple — because the
-- partner must read a shared goal's BASE row so a goal_contributions insert can reference
-- it (the redacting partner_savings_goals view can't satisfy an RLS sub-select). A shared
-- goal has no per-field privacy, so exposing its base row leaks nothing the redacting view
-- wasn't already showing. INSERT/UPDATE/DELETE stay owner-only.                 [ADR-0025]
drop policy if exists savings_goals_select on savings_goals;
create policy savings_goals_select on savings_goals for select
    using (user_id = auth.uid() or (couple_id = auth_couple_id() and is_shared));
drop policy if exists savings_goals_insert on savings_goals;
create policy savings_goals_insert on savings_goals for insert
    with check (user_id = auth.uid());
drop policy if exists savings_goals_update on savings_goals;
create policy savings_goals_update on savings_goals for update
    using (user_id = auth.uid()) with check (user_id = auth.uid());
drop policy if exists savings_goals_delete on savings_goals;
create policy savings_goals_delete on savings_goals for delete
    using (user_id = auth.uid());

-- ---- goal_contributions ----------------------------------------------------
-- ORIGINAL shape (single combined policy): you read/edit/delete only your OWN contribution
-- rows (`using`); partner contributions arrive via the partner_goal_contributions view,
-- never this base table. You may INSERT a contribution only against a goal you can access —
-- your own, or a goal shared into your couple — so both partners fund a shared goal
-- (`with check`). The savings_goals sub-select is satisfied by savings_goals_select above.
-- Later split per-command by 2026-07-03_goal_contributions_rls_split.sql (F1) so an author
-- can always UPDATE/DELETE their own row even after the goal is unshared/unpaired out from
-- under a pending offline contribution — apply that migration after this one.   [ADR-0025]
drop policy if exists goal_contributions_author on goal_contributions;
create policy goal_contributions_author on goal_contributions for all
    using (user_id = auth.uid())
    with check (
        user_id = auth.uid()
        and goal_id in (
            select id from savings_goals
            where user_id = auth.uid()
               or (couple_id = auth_couple_id() and is_shared)
        )
    );

-- ---------- Redacting partner views [ADR-0005] -------------------------------

-- A savings goal is hidden from the partner when unshared or deleted; its row still crosses
-- (couple_id retained on un-share) so the client purges its local copy. A shared goal has no
-- per-field privacy beyond the shared/deleted gate — name/target are the point of sharing.
create or replace view partner_savings_goals with (security_invoker = false) as
    select
        g.id,
        g.user_id,
        case when g.is_shared = false or g.is_deleted then null else g.name          end as name,
        case when g.is_shared = false or g.is_deleted then null else g.target_amount  end as target_amount,
        case when g.is_shared = false or g.is_deleted then null else g.target_date    end as target_date,
        case when g.is_shared = false or g.is_deleted then null else g.icon           end as icon,
        case when g.is_shared = false or g.is_deleted then null else g.color          end as color,
        case when g.is_shared = false or g.is_deleted then null else g.is_archived    end as is_archived,
        g.is_shared,
        g.is_deleted,
        g.couple_id,
        g.updated_at,
        g.server_rev
    from savings_goals g
    where g.user_id <> auth.uid()
      and g.couple_id = auth_couple_id();

-- ORIGINAL shape (no contributor-membership check): the partner's contributions to any goal
-- shared in the couple (whether they or you own the goal). amount/note/date are nulled once
-- the contribution is deleted OR its parent goal is unshared/deleted, so the removal
-- propagates to the partner's replica as a purge signal (ADR-0005). A contribution carries
-- no couple_id — its shared-ness is the goal's, via the join. Later gated on live contributor
-- membership by 2026-07-03_partner_goal_contributions_membership.sql (F3), to stop an
-- ex-partner's contribution resurfacing when the same goal is re-shared into a DIFFERENT
-- couple — apply that migration after this one.
create or replace view partner_goal_contributions with (security_invoker = false) as
    select
        gc.id,
        gc.goal_id,
        gc.user_id,
        case when gc.is_deleted or g.is_deleted or g.is_shared = false then null else gc.amount end as amount,
        case when gc.is_deleted or g.is_deleted or g.is_shared = false then null else gc.note   end as note,
        case when gc.is_deleted or g.is_deleted or g.is_shared = false then null else gc.date   end as date,
        gc.is_deleted,
        gc.updated_at,
        gc.server_rev
    from goal_contributions gc
    join savings_goals g on g.id = gc.goal_id
    where gc.user_id <> auth.uid()
      and g.couple_id = auth_couple_id();

grant select on partner_savings_goals, partner_goal_contributions to authenticated;

commit;
