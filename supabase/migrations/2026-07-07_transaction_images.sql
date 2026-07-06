-- ============================================================================
--  Multi-image receipts: transaction_images child table (V1.6.5)
--
--  Replaces the single `transactions.attachment_url` column with a note_images-
--  style child table so a transaction can carry up to 3 receipt photos. Mirrors
--  note_images end-to-end: soft-deletable + synced (server_rev/updated_at), an
--  owner RLS policy, a redacting `partner_transaction_images` view, and a
--  SECURITY DEFINER Storage partner-read gate (ADR-0043).
--
--  Dev-phase migration (testdevs only, no prod users): backfill each existing
--  non-null attachment_url into ONE transaction_images row (keeping the existing
--  Storage URL as-is — no object re-keying), then DROP the column. Devices pull
--  the backfilled rows on the new table's fresh cursor-0; no local backfill.
--
--  Storage: reuses the existing private `receipts` bucket. New object path is
--  receipts/{userId}/{transactionId}/{imageId}.jpg; the owner policy still keys
--  on folder[1] = userId, so it is unchanged. Existing (backfilled) objects keep
--  their old receipts/{userId}/{transactionId}.jpg path and stay readable.
-- ============================================================================

-- ---------- transaction_images ----------------------------------------------
-- Mirror of note_images. Soft-deletable + synced so receipt add/remove
-- propagates like everything else.
create table transaction_images (
    id             uuid primary key default gen_random_uuid(),
    transaction_id uuid not null references transactions(id) on delete cascade,
    storage_url    text not null,
    position       int not null default 0,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now(),
    is_deleted     boolean not null default false,
    server_rev     bigint
);

create trigger trg_rev_transaction_images before insert or update on transaction_images
    for each row execute function set_server_rev();

create index idx_transaction_images_rev on transaction_images(server_rev);
create index idx_transaction_images_txn on transaction_images(transaction_id);

alter table transaction_images enable row level security;

-- Owner full access, gated through the parent transaction's ownership. Partner
-- reads happen via partner_transaction_images (below).
create policy transaction_images_owner on transaction_images for all
    using (transaction_id in (select id from transactions where user_id = auth.uid()))
    with check (transaction_id in (select id from transactions where user_id = auth.uid()));

-- ---------- backfill from the single attachment_url -------------------------
-- One row per existing receipt, keeping the stored URL verbatim (no re-keying).
-- server_rev is assigned by the trigger on insert, so every device's cursor-0
-- pull of the new table brings these down.
insert into transaction_images (id, transaction_id, storage_url, position, created_at, updated_at, is_deleted)
select gen_random_uuid(), t.id, t.attachment_url, 0, now(), now(), false
from transactions t
where t.attachment_url is not null
  and t.is_deleted = false;

-- ---------- drop the single-column receipt --------------------------------
-- partner_transactions depends on attachment_url, so drop+recreate it without
-- that column before dropping the base column.
drop view if exists partner_transactions;

alter table transactions drop column attachment_url;

create view partner_transactions with (security_invoker = false) as
    select
        t.id,
        t.user_id,
        case when t.is_private or t.is_deleted then null else t.type           end as type,
        case when t.is_private or t.is_deleted then null else t.amount         end as amount,
        case when t.is_private or t.is_deleted then null else t.category_id    end as category_id,
        case when t.is_private or t.is_deleted then null else t.account_id     end as account_id,
        case when t.is_private or t.is_deleted then null else t.to_account_id  end as to_account_id,
        case when t.is_private or t.is_deleted then null else t.note           end as note,
        case when t.is_private or t.is_deleted then null else t.date           end as date,
        t.is_private,
        t.is_deleted,
        t.is_settlement,
        t.updated_at,
        t.server_rev
    from transactions t
    where t.user_id <> auth.uid()
      and t.user_id in (select id from users where couple_id = auth_couple_id());

-- ---------- partner_transaction_images redacting view -----------------------
-- Images of the partner's shared (non-private), non-deleted transactions;
-- storage_url redacted once the image or its parent transaction is no longer
-- visible, so removals propagate as a purge signal (mirrors partner_note_images).
create view partner_transaction_images with (security_invoker = false) as
    select
        ti.id,
        ti.transaction_id,
        case
            when ti.is_deleted or t.is_private or t.is_deleted then null
            else ti.storage_url
        end as storage_url,
        ti.position,
        ti.is_deleted,
        ti.updated_at,
        ti.server_rev
    from transaction_images ti
    join transactions t on t.id = ti.transaction_id
    where t.user_id <> auth.uid()
      and t.user_id in (select id from users where couple_id = auth_couple_id());

grant select on partner_transaction_images to authenticated;

-- ---------- Storage partner-read gate (ADR-0043) ----------------------------
-- Re-point the receipts partner-read check at transaction_images. Same
-- SECURITY DEFINER technique: a storage.objects policy runs under the requesting
-- (partner) user's RLS, which can't see the owner's base rows, so the visibility
-- gate must run in a definer function. Gate unchanged: non-private, non-deleted
-- transaction owned by the caller's partner. The `receipts_partner_read` policy
-- already calls this function, so no policy change is needed.
create or replace function public.partner_can_read_receipt(object_name text)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (
        select 1
        from transaction_images ti
        join transactions t on t.id = ti.transaction_id
        where ti.storage_url like '%' || object_name || '%'
          and ti.is_deleted = false
          and t.is_private = false
          and t.is_deleted = false
          and t.user_id in (
              select id from users
              where couple_id = auth_couple_id()
                and id <> auth.uid()
          )
    );
$$;
