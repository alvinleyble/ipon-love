-- ============================================================================
--  Premium entitlement schema — dormant paywall infra, S1 (Horizon #15)
--
--  Lays the persistence floor for the premium paywall (subscription-paywall-
--  design.md §12 · S1). Everything ships DORMANT: enforcement_enabled defaults
--  OFF and is_premium defaults false, so nothing locks until Alvin's explicit
--  post-beta go.
--
--  1) Four entitlement columns on the synced `users` row (D2 / ADR-0044). These
--     are a client-maintained cache of Play state, carried on the users row so
--     the couple can read each other's status (either-partner-unlocks-both, D1).
--     NOTE: there is NO partner_users redacting view — partner user rows are read
--     directly from the base table via the `users_select` same-couple policy
--     (below in schema.sql), which already exposes ALL columns. So these four are
--     partner-visible automatically, which is exactly what D2/ADR-0044 require
--     (entitlement must NOT be redacted). Nothing to add to a view.
--
--  2) `app_config` — a single-row remote-override table (D3): the master
--     enforcement kill-switch + per-cap overrides. Cached in Room, read locally,
--     refreshed on the existing sync trigger. Flipping enforcement or tuning a
--     cap needs no app release.
--     Per-user beta grants do NOT live here — a grant is written straight to the
--     users row (is_premium=true, entitlement_source='GRANT') so it propagates to
--     the partner like a purchase (ADR-0044 §4). app_config carries only the
--     kill-switch + cap overrides.
-- ============================================================================

-- ---- 1. users entitlement columns ------------------------------------------
alter table users add column if not exists is_premium             boolean not null default false;
alter table users add column if not exists premium_until          timestamptz;           -- null = never expires (one-time model, D7)
alter table users add column if not exists entitlement_source     text not null default 'NONE';  -- PLAY | GRANT | NONE (ADR-0044 §4)
alter table users add column if not exists entitlement_checked_at timestamptz;           -- last-reconcile diagnostic; never read by a gate

-- ---- 2. app_config (single-row remote override) ----------------------------
create table if not exists app_config (
    id                  boolean not null primary key default true,   -- single row (mirrors app_release_info)
    enforcement_enabled boolean not null default false,              -- master kill-switch; ships OFF
    cap_overrides       jsonb,                                       -- nullable; D3 per-cap tuning, parsed into PlanLimits
    updated_at          timestamptz not null default now(),
    check (id)
);

alter table app_config enable row level security;

-- Read-only to clients (like app_release_info). Writes are console/service-role only.
create policy app_config_select on app_config for select using (true);

-- Seed the dormant row.
insert into app_config (id, enforcement_enabled) values (true, false)
    on conflict (id) do nothing;
