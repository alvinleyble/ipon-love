-- ============================================================================
--  Storage partner-read RLS — SECURITY DEFINER fix
--
--  Bug: both Storage buckets are private, and the partner-read policies checked
--  visibility with an EXISTS subquery against the BASE tables (transactions /
--  note_images + notes). A storage.objects policy runs under the *requesting*
--  user's RLS, and those base tables are owner-scoped (user_id = auth.uid()), so
--  the partner's subquery can never see the OWNER's row — the EXISTS is always
--  false and every partner download 400s. (The receipts policy had this latent
--  bug since it shipped; it was simply never exercised until the combined-view
--  receipt UI landed.)
--
--  Fix: move the visibility check into SECURITY DEFINER functions (same technique
--  as auth_couple_id()). The function body bypasses base-table RLS, while auth.uid()
--  / auth_couple_id() still resolve to the CALLER (they read the request JWT, not
--  the definer role), so the couple + shared/non-private + not-deleted gate is
--  unchanged — only the RLS-visibility problem is removed.
--
--  Also drops the redundant, equally-broken dashboard-applied policies
--  (note_images_storage_owner / note_images_storage_partner_read) so each bucket
--  has exactly one owner policy and one partner-read policy.
-- ============================================================================

-- Receipt object readable by the partner? (partner_transactions visibility gate.)
create or replace function public.partner_can_read_receipt(object_name text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1 from transactions t
        where t.attachment_url like '%' || object_name || '%'
          and t.is_private = false
          and t.is_deleted = false
          and t.user_id in (
              select id from users
              where couple_id = auth_couple_id()
                and id <> auth.uid()
          )
    );
$$;

-- Note image readable by the partner? (partner_note_images visibility gate.)
create or replace function public.partner_can_read_note_image(object_name text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from note_images ni
        join notes n on n.id = ni.note_id
        where ni.storage_url like '%' || object_name || '%'
          and ni.is_deleted = false
          and n.is_shared = true
          and n.is_deleted = false
          and n.user_id in (
              select id from users
              where couple_id = auth_couple_id()
                and id <> auth.uid()
          )
    );
$$;

grant execute on function public.partner_can_read_receipt(text) to authenticated;
grant execute on function public.partner_can_read_note_image(text) to authenticated;

-- Rebuild the partner-read policies on top of the definer functions.
drop policy if exists receipts_partner_read on storage.objects;
create policy receipts_partner_read on storage.objects for select
    to authenticated
    using (bucket_id = 'receipts' and public.partner_can_read_receipt(name));

drop policy if exists note_images_partner_read on storage.objects;
drop policy if exists note_images_storage_partner_read on storage.objects;
create policy note_images_partner_read on storage.objects for select
    to authenticated
    using (bucket_id = 'note-images' and public.partner_can_read_note_image(name));

-- Drop the redundant dashboard-applied owner dup (note_images_owner already covers it).
drop policy if exists note_images_storage_owner on storage.objects;
