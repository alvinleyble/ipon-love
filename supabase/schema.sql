-- ============================================================================
--  Ipon, Love — Supabase schema (v1)
--  Source of truth: ARCHITECTURE.md §4 (Data Model) + docs/adr/0001..0011.
--  Run in the Supabase SQL editor on a fresh project (region ap-southeast-1).
--
--  Conventions / decisions baked in here:
--   - Soft delete only: is_deleted = true, never hard delete (sync safety).      [ADR-0010]
--   - updated_at is the LWW key, set by the CLIENT on every write, offset-
--     corrected toward server time. NO trigger ever overrides it.                [ADR-0001]
--   - server_rev is the PULL cursor: a server-assigned bigint from one global
--     sequence, stamped by a trigger on every upsert. It orders rows by SERVER
--     RECEIPT, separate from updated_at (who-wins). Touches only server_rev.     [ADR-0002]
--   - pending_sync (push selection) is LOCAL-ONLY (Room). It is deliberately
--     NOT in this schema and never sent to Supabase.                             [ADR-0002]
--   - Partner data is read through REDACTING VIEWS (not raw partner RLS) so
--     privatize/delete/unshare converge on the partner's replica.               [ADR-0005]
--   - Account balance is DERIVED from the ledger; only opening_balance syncs.    [ADR-0007]
--   - Pairing/unpairing go through SECURITY DEFINER RPCs.                        [ADR-0006, 0008]
-- ============================================================================

-- ---------- Pull cursor sequence + trigger ----------------------------------
-- One global sequence gives a total server-receipt order across all tables.
create sequence global_server_rev;

-- Stamps server_rev on every insert/update. Intentionally does NOT touch
-- updated_at (ADR-0001 keeps updated_at client-authoritative for LWW).
create or replace function set_server_rev()
returns trigger
language plpgsql
as $$
begin
    new.server_rev := nextval('global_server_rev');
    return new;
end;
$$;

-- ---------- Enums -----------------------------------------------------------
create type account_type        as enum ('CASH', 'CARD', 'BANK', 'EWALLET');
create type category_type       as enum ('INCOME', 'EXPENSE');
create type transaction_type    as enum ('INCOME', 'EXPENSE', 'TRANSFER');
create type recurring_frequency as enum ('DAILY', 'WEEKLY', 'MONTHLY', 'CUSTOM');

-- ---------- couples ---------------------------------------------------------
-- Created before users because users.couple_id references it. user1/user2 FKs
-- to users are added after users exists (see below). Now a fully synced table
-- (server_rev/updated_at/is_deleted) so pairing + unpairing propagate.   [ADR-0006, 0008]
create table couples (
    id          uuid primary key default gen_random_uuid(),
    couple_name text not null,
    invite_code text not null unique,
    user1_id    uuid not null,
    user2_id    uuid,                                  -- null until partner redeems invite
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),    -- client-set LWW key
    is_deleted  boolean     not null default false,    -- soft-deleted on unpair
    server_rev  bigint
);

-- ---------- users -----------------------------------------------------------
-- id mirrors auth.users(id). A row is created post-verification (ARCHITECTURE §5).
create table users (
    id           uuid primary key references auth.users(id) on delete cascade,
    display_name text,
    avatar_url   text,
    accent_color text,                       -- hex, for combined-view color coding
    couple_id    uuid references couples(id) on delete set null,
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    server_rev   bigint
);

alter table couples
    add constraint couples_user1_fk foreign key (user1_id) references users(id) on delete cascade,
    add constraint couples_user2_fk foreign key (user2_id) references users(id) on delete set null;

-- ---------- accounts --------------------------------------------------------
-- balance is DERIVED from the ledger (ADR-0007); only opening_balance syncs.
create table accounts (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null references users(id) on delete cascade,
    name            text not null,
    type            account_type not null,
    opening_balance numeric(14,2) not null default 0,   -- current balance = this + ledger
    icon            text,
    color           text,
    position        int not null default 0,
    is_archived     boolean not null default false,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    is_deleted      boolean not null default false,
    server_rev      bigint
);

-- ---------- categories ------------------------------------------------------
create table categories (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references users(id) on delete cascade,
    name        text not null,
    type        category_type not null,
    icon        text,
    color       text,
    position    int not null default 0,
    is_archived boolean not null default false,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    is_deleted  boolean not null default false,
    server_rev  bigint
);

-- ---------- recurring_rules (owner-only, never shared) -----------------------
create table recurring_rules (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references users(id) on delete cascade,
    frequency   recurring_frequency not null,
    interval    int not null default 1,           -- every N units
    next_date   date not null,
    end_date    date,                              -- null = no end
    template    jsonb not null,                    -- amount, category_id, account_id, note
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    is_deleted  boolean not null default false,
    server_rev  bigint
);

-- ---------- transactions ----------------------------------------------------
create table transactions (
    id                uuid primary key default gen_random_uuid(),
    user_id           uuid not null references users(id) on delete cascade,
    type              transaction_type not null,
    amount            numeric(14,2) not null,
    category_id       uuid references categories(id) on delete set null,   -- null for transfers
    account_id        uuid not null references accounts(id) on delete cascade,
    to_account_id     uuid references accounts(id) on delete set null,     -- transfer dest only
    note              text,
    date              timestamptz not null,
    is_private        boolean not null default false,                      -- hidden from partner
    recurring_rule_id uuid references recurring_rules(id) on delete set null,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    is_deleted        boolean not null default false,
    server_rev        bigint
);

-- ---------- budgets ---------------------------------------------------------
-- Personal: user_id set, couple_id null. Shared: couple_id set, user_id null.
-- Shared budgets are fully shared (both partners read/write); no redaction.
create table budgets (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid references users(id) on delete cascade,
    couple_id   uuid references couples(id) on delete cascade,
    category_id uuid references categories(id) on delete set null,   -- null = overall monthly
    amount      numeric(14,2) not null,
    year_month  text not null,                                       -- "2026-06"
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    is_deleted  boolean not null default false,
    server_rev  bigint,
    constraint budget_owner_chk check (
        (user_id is not null and couple_id is null) or
        (user_id is null and couple_id is not null)
    )
);

-- ---------- notes -----------------------------------------------------------
-- Un-sharing sets is_shared=false but RETAINS couple_id, so the un-share
-- transition still reaches the partner's redacting view to trigger a purge.
-- (Only unpair nulls couple_id, where a bulk local purge handles cleanup.)  [ADR-0005, 0008]
create table notes (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references users(id) on delete cascade,
    title      text,
    content    jsonb,                                   -- rich text delta
    is_shared  boolean not null default false,
    couple_id  uuid references couples(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    is_deleted boolean not null default false,
    server_rev bigint
);

-- ---------- partner_debts ---------------------------------------------------
-- Informal IOUs between the two partners. Scoped to the couple; both members
-- read/write (no redacting view needed — debts are inherently shared data).
-- remaining_balance = amount - sum(partner_debt_payments.amount) — never stored.
-- Soft-deleted on unpair (unpair() function below handles this).
create table partner_debts (
    id          uuid primary key default gen_random_uuid(),
    couple_id   uuid not null references couples(id) on delete cascade,
    borrower_id uuid not null references users(id) on delete cascade,
    lender_id   uuid not null references users(id) on delete cascade,
    amount      numeric(14,2) not null,
    description text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    is_deleted  boolean not null default false,
    server_rev  bigint
);

-- ---------- partner_debt_payments -------------------------------------------
-- Each row is one (partial or full) repayment against a partner_debt.
create table partner_debt_payments (
    id         uuid primary key default gen_random_uuid(),
    debt_id    uuid not null references partner_debts(id) on delete cascade,
    amount     numeric(14,2) not null,
    note       text,
    date       timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    is_deleted boolean not null default false,
    server_rev bigint
);

-- ---------- note_images -----------------------------------------------------
-- Now soft-deletable + synced so image add/remove propagates like everything else.
create table note_images (
    id          uuid primary key default gen_random_uuid(),
    note_id     uuid not null references notes(id) on delete cascade,
    storage_url text not null,
    position    int not null default 0,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    is_deleted  boolean not null default false,
    server_rev  bigint
);

-- ---------- server_rev triggers (one per synced table) ----------------------
create trigger trg_rev_couples            before insert or update on couples              for each row execute function set_server_rev();
create trigger trg_rev_users              before insert or update on users               for each row execute function set_server_rev();
create trigger trg_rev_accounts           before insert or update on accounts            for each row execute function set_server_rev();
create trigger trg_rev_categories         before insert or update on categories          for each row execute function set_server_rev();
create trigger trg_rev_recurring          before insert or update on recurring_rules     for each row execute function set_server_rev();
create trigger trg_rev_transactions       before insert or update on transactions        for each row execute function set_server_rev();
create trigger trg_rev_budgets            before insert or update on budgets             for each row execute function set_server_rev();
create trigger trg_rev_partner_debts      before insert or update on partner_debts       for each row execute function set_server_rev();
create trigger trg_rev_debt_payments      before insert or update on partner_debt_payments for each row execute function set_server_rev();
create trigger trg_rev_notes              before insert or update on notes               for each row execute function set_server_rev();
create trigger trg_rev_note_images        before insert or update on note_images         for each row execute function set_server_rev();

-- ---------- Indexes (sync cursor + common queries) --------------------------
-- Pull is "where server_rev > cursor order by server_rev", so every synced
-- table is indexed on server_rev. Per-table cursor lives client-side.       [ADR-0002]
create index idx_couples_rev          on couples(server_rev);
create index idx_users_rev            on users(server_rev);
create index idx_accounts_rev         on accounts(server_rev);
create index idx_categories_rev       on categories(server_rev);
create index idx_recurring_rev        on recurring_rules(server_rev);
create index idx_transactions_rev     on transactions(server_rev);
create index idx_budgets_rev          on budgets(server_rev);
create index idx_partner_debts_rev    on partner_debts(server_rev);
create index idx_debt_payments_rev    on partner_debt_payments(server_rev);
create index idx_notes_rev            on notes(server_rev);
create index idx_note_images_rev      on note_images(server_rev);

create index idx_accounts_user        on accounts(user_id);
create index idx_categories_user      on categories(user_id);
create index idx_transactions_user    on transactions(user_id);
create index idx_transactions_date    on transactions(date);
create index idx_recurring_user       on recurring_rules(user_id);
create index idx_budgets_user         on budgets(user_id);
create index idx_budgets_couple       on budgets(couple_id);
create index idx_partner_debts_couple on partner_debts(couple_id);
create index idx_debt_payments_debt   on partner_debt_payments(debt_id);
create index idx_notes_user           on notes(user_id);
create index idx_notes_couple         on notes(couple_id);
create index idx_note_images_note     on note_images(note_id);

-- ============================================================================
--  Row Level Security
--  Everyone is authenticated via Supabase Auth; auth.uid() is the user id.
-- ============================================================================

-- Caller's couple_id. SECURITY DEFINER so it can read users without recursing
-- through users' own RLS. Returns null when the caller has no couple.
create or replace function auth_couple_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
    select couple_id from users where id = auth.uid();
$$;

alter table users                   enable row level security;
alter table couples                 enable row level security;
alter table accounts                enable row level security;
alter table categories              enable row level security;
alter table transactions            enable row level security;
alter table recurring_rules         enable row level security;
alter table budgets                 enable row level security;
alter table partner_debts           enable row level security;
alter table partner_debt_payments   enable row level security;
alter table notes                   enable row level security;
alter table note_images             enable row level security;

-- ---- users -----------------------------------------------------------------
-- Same-couple read is fine: users rows carry no private content (just name +
-- accent color). The partner's combined view needs them to render attribution.
create policy users_select on users for select
    using (id = auth.uid() or couple_id = auth_couple_id());
create policy users_insert on users for insert
    with check (id = auth.uid());
create policy users_update on users for update
    using (id = auth.uid()) with check (id = auth.uid());

-- ---- couples ---------------------------------------------------------------
-- Members read/update their own couple. Joining a couple is NOT done here —
-- it goes through redeem_invite() (an unpaired user is neither member yet).  [ADR-0006]
create policy couples_select on couples for select
    using (user1_id = auth.uid() or user2_id = auth.uid());
create policy couples_update on couples for update
    using (user1_id = auth.uid() or user2_id = auth.uid())
    with check (user1_id = auth.uid() or user2_id = auth.uid());
-- (No direct insert policy: couples are created via create_couple().)

-- ---- owner-only base-table policies ----------------------------------------
-- Partners do NOT read these base tables directly. Cross-partner reads go
-- through the redacting views below, which redact content + reveal removals. [ADR-0005]
create policy accounts_owner on accounts for all
    using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy categories_owner on categories for all
    using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy recurring_owner on recurring_rules for all
    using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy transactions_owner on transactions for all
    using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy notes_owner on notes for all
    using (user_id = auth.uid()) with check (user_id = auth.uid());

-- ---- budgets ---------------------------------------------------------------
-- Personal budgets: owner only. Shared budgets: any couple member, full access
-- (no redaction — shared budgets are jointly owned).
create policy budgets_owner on budgets for all
    using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy budgets_couple on budgets for all
    using (couple_id = auth_couple_id())
    with check (couple_id = auth_couple_id());

-- ---- partner_debts ---------------------------------------------------------
-- Both couple members have full access. No redacting view needed — debts are
-- inherently shared data with no per-row privacy concept.
create policy partner_debts_couple on partner_debts for all
    using (couple_id = auth_couple_id())
    with check (couple_id = auth_couple_id());

-- ---- partner_debt_payments -------------------------------------------------
-- Access gated via debt → couple membership.
create policy debt_payments_couple on partner_debt_payments for all
    using  (debt_id in (select id from partner_debts where couple_id = auth_couple_id()))
    with check (debt_id in (select id from partner_debts where couple_id = auth_couple_id()));

-- ---- note_images -----------------------------------------------------------
-- Owner full access. Partner reads happen via partner_note_images (below).
create policy note_images_owner on note_images for all
    using (note_id in (select id from notes where user_id = auth.uid()))
    with check (note_id in (select id from notes where user_id = auth.uid()));

-- ============================================================================
--  Redacting partner views  [ADR-0005]
--  A user reads the PARTNER's rows through these views, never the base tables.
--  Each view: returns partner rows regardless of is_private/is_deleted/is_shared
--  (so transitions-into-invisibility still propagate); nulls content columns
--  when the row is hidden; always reveals id/owner/flags/updated_at/server_rev
--  so the client can purge its local copy. Gated to the caller's couple, caller's
--  own rows excluded (those come full from the base tables).
--
--  These are SECURITY DEFINER views (security_invoker=false): they bypass base-
--  table RLS by design, and the WHERE clause + auth_couple_id() is the gate.
-- ============================================================================

create view partner_transactions with (security_invoker = false) as
    select
        t.id,
        t.user_id,
        case when t.is_private or t.is_deleted then null else t.type          end as type,
        case when t.is_private or t.is_deleted then null else t.amount        end as amount,
        case when t.is_private or t.is_deleted then null else t.category_id   end as category_id,
        case when t.is_private or t.is_deleted then null else t.account_id    end as account_id,
        case when t.is_private or t.is_deleted then null else t.to_account_id end as to_account_id,
        case when t.is_private or t.is_deleted then null else t.note          end as note,
        case when t.is_private or t.is_deleted then null else t.date          end as date,
        t.is_private,
        t.is_deleted,
        t.updated_at,
        t.server_rev
    from transactions t
    where t.user_id <> auth.uid()
      and t.user_id in (select id from users where couple_id = auth_couple_id());

create view partner_accounts with (security_invoker = false) as
    select
        a.id,
        a.user_id,
        case when a.is_deleted then null else a.name        end as name,
        case when a.is_deleted then null else a.type        end as type,
        case when a.is_deleted then null else a.icon        end as icon,
        case when a.is_deleted then null else a.color       end as color,
        case when a.is_deleted then null else a.is_archived end as is_archived,
        -- opening_balance deliberately omitted: partner balances are not shown. [ADR-0011]
        a.is_deleted,
        a.updated_at,
        a.server_rev
    from accounts a
    where a.user_id <> auth.uid()
      and a.user_id in (select id from users where couple_id = auth_couple_id());

create view partner_categories with (security_invoker = false) as
    select
        c.id,
        c.user_id,
        case when c.is_deleted then null else c.name        end as name,
        case when c.is_deleted then null else c.type        end as type,
        case when c.is_deleted then null else c.icon        end as icon,
        case when c.is_deleted then null else c.color       end as color,
        case when c.is_deleted then null else c.is_archived end as is_archived,
        c.is_deleted,
        c.updated_at,
        c.server_rev
    from categories c
    where c.user_id <> auth.uid()
      and c.user_id in (select id from users where couple_id = auth_couple_id());

-- A note is hidden from the partner when not shared or deleted; its row still
-- crosses (couple_id retained on un-share) so the client purges its local copy.
create view partner_notes with (security_invoker = false) as
    select
        n.id,
        n.user_id,
        case when n.is_shared = false or n.is_deleted then null else n.title   end as title,
        case when n.is_shared = false or n.is_deleted then null else n.content end as content,
        n.is_shared,
        n.is_deleted,
        n.couple_id,
        n.updated_at,
        n.server_rev
    from notes n
    where n.user_id <> auth.uid()
      and n.couple_id = auth_couple_id();

-- Images of the partner's shared, non-deleted notes; storage_url redacted once
-- the image or its parent note is no longer visible, so removals propagate.
create view partner_note_images with (security_invoker = false) as
    select
        ni.id,
        ni.note_id,
        case
            when ni.is_deleted or n.is_shared = false or n.is_deleted then null
            else ni.storage_url
        end as storage_url,
        ni.position,
        ni.is_deleted,
        ni.updated_at,
        ni.server_rev
    from note_images ni
    join notes n on n.id = ni.note_id
    where n.user_id <> auth.uid()
      and n.couple_id = auth_couple_id();

grant select on partner_transactions, partner_accounts, partner_categories,
                partner_notes, partner_note_images to authenticated;

-- ============================================================================
--  Pairing / unpairing RPCs  [ADR-0006, 0008]
--  SECURITY DEFINER: they must touch rows the caller's RLS can't (e.g. join a
--  couple the caller is not yet a member of), with validation done in-function.
-- ============================================================================

-- Generate a short, unique, unambiguous invite code (no 0/O/1/I).
create or replace function gen_invite_code()
returns text
language plpgsql
as $$
declare
    alphabet constant text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    code text;
begin
    loop
        code := '';
        for i in 1..6 loop
            code := code || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
        end loop;
        exit when not exists (select 1 from couples where invite_code = code);
    end loop;
    return code;
end;
$$;

-- Create a couple for the caller (becomes user1) and set their couple_id.
create or replace function create_couple(p_name text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_couple_id uuid;
begin
    if (select couple_id from users where id = auth.uid()) is not null then
        raise exception 'already in a couple';
    end if;

    insert into couples (couple_name, invite_code, user1_id, updated_at)
    values (p_name, gen_invite_code(), auth.uid(), now())
    returning id into v_couple_id;

    update users set couple_id = v_couple_id, updated_at = now() where id = auth.uid();
    return v_couple_id;
end;
$$;

-- Redeem an invite code: caller becomes user2. Sidesteps the RLS chicken-and-egg.
create or replace function redeem_invite(p_code text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_couple couples%rowtype;
begin
    if (select couple_id from users where id = auth.uid()) is not null then
        raise exception 'already in a couple';
    end if;

    select * into v_couple from couples
        where invite_code = p_code and is_deleted = false
        for update;

    if not found then
        raise exception 'invalid invite code';
    end if;
    if v_couple.user2_id is not null then
        raise exception 'couple is already full';
    end if;
    if v_couple.user1_id = auth.uid() then
        raise exception 'cannot join your own couple';
    end if;

    update couples set user2_id = auth.uid(), updated_at = now() where id = v_couple.id;
    update users   set couple_id = v_couple.id, updated_at = now() where id = auth.uid();
    return v_couple.id;
end;
$$;

-- Rotate the caller's couple invite code (e.g. after sharing the old one).
create or replace function rotate_invite_code()
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    v_code text := gen_invite_code();
begin
    update couples set invite_code = v_code, updated_at = now()
        where id = auth_couple_id() and (user1_id = auth.uid() or user2_id = auth.uid());
    if not found then
        raise exception 'no couple to rotate';
    end if;
    return v_code;
end;
$$;

-- Dissolve the caller's couple: both leave, shared budgets soft-deleted, shared
-- notes revert to private-to-owner, couple soft-deleted. Each client then bulk-
-- purges replicated non-owned rows on seeing its own couple_id go null.       [ADR-0008]
create or replace function unpair()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_couple_id uuid := auth_couple_id();
begin
    if v_couple_id is null then
        raise exception 'not in a couple';
    end if;
    -- guard: caller must actually be a member
    if not exists (
        select 1 from couples
        where id = v_couple_id and (user1_id = auth.uid() or user2_id = auth.uid())
    ) then
        raise exception 'not a member of this couple';
    end if;

    update budgets set is_deleted = true, updated_at = now()
        where couple_id = v_couple_id and is_deleted = false;

    update partner_debts set is_deleted = true, updated_at = now()
        where couple_id = v_couple_id and is_deleted = false;

    update notes set is_shared = false, couple_id = null, updated_at = now()
        where couple_id = v_couple_id and is_shared = true;

    update users set couple_id = null, updated_at = now() where couple_id = v_couple_id;

    update couples set is_deleted = true, updated_at = now() where id = v_couple_id;
end;
$$;
