-- ============================================================================
--  analytics_events — paywall-funnel telemetry (paywall S6, Horizon #15)
--
--  G10 / §10.10: a Room-buffered, PUSH-ONLY telemetry table. The client logs
--  paywall-interaction events (paywall_impression, upsell_tap, purchase_*,
--  restore, refund_detected) into a local Room buffer and flushes them here on
--  the existing full-sync trigger, deleting each row once it lands. No
--  third-party analytics SDK (Firebase banned; no off-device behavioral vendor
--  for a finance app). Only event name/source + user id are stored — never any
--  financial content — so the existing privacy policy already covers it. The
--  funnel is computed in SQL / a Supabase dashboard; the app never reads events
--  back (no syncer pulls this table).
--
--  Client id is generated on-device (UUID) so a retried flush upserts
--  idempotently after an uncertain network outcome — no duplicate rows.
-- ============================================================================
create table if not exists analytics_events (
    id         uuid not null primary key,
    user_id    uuid not null references users(id) on delete cascade,
    name       text not null,
    source     text,
    params     jsonb,
    created_at timestamptz not null default now()
);

create index if not exists analytics_events_user_created_idx
    on analytics_events (user_id, created_at);

alter table analytics_events enable row level security;

-- Insert + read own rows only. The self-scoped select is required so the
-- client's default returning=representation upsert isn't RLS-rejected; the app
-- never actually reads events back.
create policy analytics_events_insert on analytics_events
    for insert with check (user_id = auth.uid());
create policy analytics_events_select on analytics_events
    for select using (user_id = auth.uid());
