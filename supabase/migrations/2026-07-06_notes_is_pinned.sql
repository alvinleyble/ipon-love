-- Add pin support to notes (V1.6.3 Item 1, ADR-0040).
-- Pin is a synced boolean on the note row: pinned notes hoist into a "Pinned"
-- section, ordering otherwise unchanged. Pin/unpin reuses the existing note
-- write path (LWW), so there is nothing to store beyond the flag itself.

alter table notes
    add column if not exists is_pinned boolean not null default false;

-- The partner view must carry the owner's pin so a shared, pinned note stays
-- pinned in the partner's replicated copy (ADR-0040). Appended at the end so
-- this is a clean CREATE OR REPLACE.
create or replace view partner_notes with (security_invoker = false) as
    select
        n.id,
        n.user_id,
        case when n.is_shared = false or n.is_deleted then null else n.title   end as title,
        case when n.is_shared = false or n.is_deleted then null else n.content end as content,
        n.is_shared,
        n.is_deleted,
        n.couple_id,
        n.updated_at,
        n.server_rev,
        n.is_pinned
    from notes n
    where n.user_id <> auth.uid()
      and n.couple_id = auth_couple_id();

-- PostgREST caches the schema; nudge a reload so the new column is visible immediately.
notify pgrst, 'reload schema';
