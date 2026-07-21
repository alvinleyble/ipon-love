-- v1.6.7 Item 3 Leg 1 — motif avatars (free for everyone).
-- One synced cosmetic column on users, mirroring accent_color (ADR-0014): a motif-avatar
-- key string (heart/leaf/bloom/sparkle/wave/crescent/gem/sprout). null = Heart default.
-- Partner-visible via the existing same-couple users_select policy (cosmetic, not redacted, D2).
-- Metadata-only add, no row rewrite — doesn't touch set_server_rev, so existing rows aren't re-synced.
alter table users add column if not exists avatar_motif text;
