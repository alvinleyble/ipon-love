-- Migration: app_release_info table for the beta version-mismatch gate (Item 9, ADR-0029).
-- Apply on the live Supabase project (vyjaorlevomfqkidttom) via the SQL Editor.
-- Idempotent guard: skips creation if the table already exists. This body matches
-- supabase/schema.sql exactly.
--
-- Single-row, public-read config table. NOT a synced entity — no server_rev/updated_at/
-- pending_sync, never touched by TableSyncer push/pull (ADR-0009 doesn't apply here).
-- Alvin bumps required_version_code by hand (Supabase table editor, which writes as the
-- postgres role and bypasses RLS) before a new build reaches testers.

do $$
begin
    if not exists (select 1 from information_schema.tables where table_name = 'app_release_info') then
        create table app_release_info (
            id                     boolean not null primary key default true,
            required_version_code  int not null,
            check (id)
        );

        alter table app_release_info enable row level security;

        create policy app_release_info_select on app_release_info for select using (true);

        -- Seeded to the versionCode shipped in v1.6.0 so existing testers aren't blocked
        -- until Alvin bumps this row for the next release.
        insert into app_release_info (id, required_version_code) values (true, 2);
    end if;
end $$;
