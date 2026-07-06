# Storage partner-read RLS must gate visibility through a SECURITY DEFINER function, not an inline base-table subquery

## Context

Beta-adjacent bug report (Alvin, 2026-07-07): note and receipt photos uploaded fine but never displayed a preview, and tapping a thumbnail showed nothing — for the owner *and* the partner. Investigation (live staging DB + direct HTTP probes against Supabase Storage) found two independent defects stacked on top of each other:

1. **Both Storage buckets (`note-images`, `receipts`) are private**, but the uploaders (`NoteAttachmentUploader`, `ReceiptUploader`) stamped `publicUrl(path)` — a URL form that only resolves against a *public* bucket. Every load 400'd, for everyone. Fixed by switching to `authenticatedUrl(path)` plus a Coil `Interceptor` (`StorageAuthInterceptor`) that attaches the Supabase auth token to Storage requests, and rewrites legacy public-form URLs already in the DB to authenticated-form at request time (no data migration needed).

2. **The partner-read `storage.objects` policies were structurally broken**, independent of (1). `receipts_partner_read` (and the `note_images_partner_read` policy added alongside fix (1)) gated visibility with an inline `EXISTS` subquery against the **base tables** (`transactions`, or `note_images` joined to `notes`):
   ```sql
   -- BROKEN shape:
   create policy receipts_partner_read on storage.objects for select
       to authenticated
       using (
           bucket_id = 'receipts'
           and exists (
               select 1 from transactions t
               where t.attachment_url like '%' || name || '%'
                 and t.is_private = false and t.is_deleted = false
                 and t.user_id in (select id from users where couple_id = auth_couple_id() and id <> auth.uid())
           )
       );
   ```
   A `storage.objects` RLS policy executes under the **requesting** user's session — so the `exists (select 1 from transactions t where ...)` subquery is itself subject to `transactions_owner` (`user_id = auth.uid()`), which is owner-scoped. The partner can never see the *owner's* row through it, so the `EXISTS` is always false and every partner download 400s. Confirmed two ways: the owner's own download succeeds (200) at the same URL; and the identical row-match query returns `true` only when run bypassing RLS (the `postgres` role). This was a **latent bug in the receipts policy since it shipped** (V1.3) — never caught because no UI rendered a partner's receipt until the combined-view thumbnail (this batch) started exercising it.

## Decision

**Any Storage partner-read policy must resolve its visibility check inside a `SECURITY DEFINER` SQL function, never as an inline subquery against an owner-scoped base table.** The function bypasses base-table RLS for its own query, while `auth.uid()` / `auth_couple_id()` inside it still resolve to the **calling** user (they read the request JWT, not the definer role) — so the visibility gate itself is unchanged, only the "can I even see the row to check it" problem goes away. This mirrors the existing `auth_couple_id()` function, which already uses this exact technique to let a user resolve their own `couple_id` without an RLS loop.

```sql
create or replace function public.partner_can_read_receipt(object_name text)
returns boolean language sql stable security definer set search_path = public as $$
    select exists (
        select 1 from transactions t
        where t.attachment_url like '%' || object_name || '%'
          and t.is_private = false and t.is_deleted = false
          and t.user_id in (select id from users where couple_id = auth_couple_id() and id <> auth.uid())
    );
$$;
grant execute on function public.partner_can_read_receipt(text) to authenticated;

create policy receipts_partner_read on storage.objects for select
    to authenticated
    using (bucket_id = 'receipts' and public.partner_can_read_receipt(name));
```

Same shape for `partner_can_read_note_image(object_name)` / `note_images_partner_read`. Both live in `supabase/migrations/2026-07-07_storage_partner_read_rls.sql` and `supabase/schema.sql`.

**Rule for every future shared-attachment feature** (the next one being multi-image receipts, `transaction_images`): a partner-read Storage policy is not "the same visibility check as the redacting view, inlined" — it must be **wrapped in a `SECURITY DEFINER` function**, because a redacting view and a Storage policy execute under different privilege contexts even when checking the identical condition.

## Consequences

- Two reusable definer functions now exist (`partner_can_read_receipt`, `partner_can_read_note_image`) that any future partner-read Storage policy on these tables can call directly instead of re-deriving the join.
- `SECURITY DEFINER` functions run with the definer's (table owner's) privileges for their body — they must stay narrowly scoped (a single boolean visibility check, `stable`, explicit `set search_path`) and never accept caller-controlled SQL, which these do not (only a `text` object-name comparison).
- The redundant, dashboard-applied `note_images_storage_owner` / `note_images_storage_partner_read` policies (untracked in any migration, found live on staging) were dropped in the same migration — one owner policy and one partner-read policy per bucket now.

## Suggested build

Opus, high effort — required tracing RLS execution context across privilege boundaries (a genuinely non-obvious Postgres RLS interaction), not a mechanical fix.
