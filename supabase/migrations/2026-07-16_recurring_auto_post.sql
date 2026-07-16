-- Item 37 (v1.6.6) — confirm-on-arrival recurring (Slice 1, free core).
-- Adds a per-rule flag distinguishing the two recurring behaviours:
--   auto_post = false  → "ask me to confirm on arrival" (the NEW default for every rule,
--                        income and bills alike); the rule does not materialize — its cursor
--                        parks and pending occurrences are derived from next_date..today and
--                        confirmed one-by-one by the user (with an optional per-occurrence
--                        amount tweak).
--   auto_post = true   → legacy silent auto-post (opt-in for truly-fixed charges); the
--                        materialization pass generates + advances as before.
--
-- Metadata-only add with a constant default: no row rewrite, does NOT fire set_server_rev,
-- so existing rows are not re-synced. Existing rules become confirm-on-arrival by default.
-- Applied + verified live on staging 2026-07-16 via the Supabase MCP apply_migration.
alter table recurring_rules
    add column auto_post boolean not null default false;
