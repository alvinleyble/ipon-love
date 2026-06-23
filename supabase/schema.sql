-- ============================================================================
--  Ipon, Love — Supabase schema (v1)
--  Source of truth: ARCHITECTURE.md §4 (Data Model).
--  Run in the Supabase SQL editor on a fresh project (region ap-southeast-1).
--
--  Conventions:
--   - Soft delete only: is_deleted = true, never hard delete (sync safety).
--   - updated_at is set by the CLIENT on every write (last-write-wins sync).
--     We deliberately do NOT auto-override it with a trigger — that would break
--     conflict resolution during push. See ARCHITECTURE.md §6.
-- ============================================================================

-- ---------- Enums -----------------------------------------------------------
create type account_type        as enum ('CASH', 'CARD', 'BANK', 'EWALLET');
create type category_type       as enum ('INCOME', 'EXPENSE');
create type transaction_type    as enum ('INCOME', 'EXPENSE', 'TRANSFER');
create type recurring_frequency as enum ('DAILY', 'WEEKLY', 'MONTHLY', 'CUSTOM');

-- ---------- couples ---------------------------------------------------------
-- Created before users because users.couple_id references it. user1/user2 FKs
-- to users are added after users exists (see end of file).
create table couples (
    id          uuid primary key default gen_random_uuid(),
    couple_name text not null,
    invite_code text not null unique,
    user1_id    uuid not null,
    user2_id    uuid,
    created_at  timestamptz not null default now()
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
    updated_at   timestamptz not null default now()
);

alter table couples
    add constraint couples_user1_fk foreign key (user1_id) references users(id) on delete cascade,
    add constraint couples_user2_fk foreign key (user2_id) references users(id) on delete set null;

-- ---------- accounts --------------------------------------------------------
create table accounts (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references users(id) on delete cascade,
    name        text not null,
    type        account_type not null,
    balance     numeric(14,2) not null default 0,
    icon        text,
    color       text,
    position    int not null default 0,
    is_archived boolean not null default false,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    is_deleted  boolean not null default false
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
    is_deleted  boolean not null default false
);

-- ---------- recurring_rules -------------------------------------------------
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
    is_deleted  boolean not null default false
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
    is_deleted        boolean not null default false
);

-- ---------- budgets ---------------------------------------------------------
-- Personal: user_id set, couple_id null. Shared: couple_id set, user_id null.
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
    constraint budget_owner_chk check (
        (user_id is not null and couple_id is null) or
        (user_id is null and couple_id is not null)
    )
);

-- ---------- notes -----------------------------------------------------------
create table notes (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references users(id) on delete cascade,
    title      text,
    content    jsonb,                                   -- rich text delta
    is_shared  boolean not null default false,
    couple_id  uuid references couples(id) on delete set null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    is_deleted boolean not null default false
);

-- ---------- note_images -----------------------------------------------------
create table note_images (
    id          uuid primary key default gen_random_uuid(),
    note_id     uuid not null references notes(id) on delete cascade,
    storage_url text not null,
    position    int not null default 0,
    created_at  timestamptz not null default now()
);

-- ---------- Indexes (sync + common queries) ---------------------------------
create index idx_accounts_user           on accounts(user_id);
create index idx_accounts_updated         on accounts(updated_at);
create index idx_categories_user          on categories(user_id);
create index idx_categories_updated       on categories(updated_at);
create index idx_transactions_user        on transactions(user_id);
create index idx_transactions_updated     on transactions(updated_at);
create index idx_transactions_date        on transactions(date);
create index idx_recurring_user           on recurring_rules(user_id);
create index idx_budgets_user             on budgets(user_id);
create index idx_budgets_couple           on budgets(couple_id);
create index idx_notes_user               on notes(user_id);
create index idx_notes_couple             on notes(couple_id);
create index idx_note_images_note         on note_images(note_id);

-- ============================================================================
--  Row Level Security
--  Everyone is authenticated via Supabase Auth; auth.uid() is the user id.
-- ============================================================================

-- Caller's couple_id, used by combined-view read policies. SECURITY DEFINER so
-- it can read users without recursing through users' own RLS.
create or replace function auth_couple_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
    select couple_id from users where id = auth.uid();
$$;

alter table users           enable row level security;
alter table couples         enable row level security;
alter table accounts        enable row level security;
alter table categories      enable row level security;
alter table transactions    enable row level security;
alter table recurring_rules enable row level security;
alter table budgets         enable row level security;
alter table notes           enable row level security;
alter table note_images     enable row level security;

-- ---- users -----------------------------------------------------------------
create policy users_select on users for select
    using (id = auth.uid() or couple_id = auth_couple_id());
create policy users_insert on users for insert
    with check (id = auth.uid());
create policy users_update on users for update
    using (id = auth.uid()) with check (id = auth.uid());

-- ---- couples ---------------------------------------------------------------
create policy couples_select on couples for select
    using (user1_id = auth.uid() or user2_id = auth.uid());
create policy couples_insert on couples for insert
    with check (user1_id = auth.uid());
create policy couples_update on couples for update
    using (user1_id = auth.uid() or user2_id = auth.uid())
    with check (user1_id = auth.uid() or user2_id = auth.uid());

-- ---- accounts & categories -------------------------------------------------
-- Owner: full CRUD. Partner: read access so the combined view can render the
-- other's transactions with their real account/category names, icons, colors.
create policy accounts_owner on accounts for all
    using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy accounts_partner_read on accounts for select
    using (
        is_deleted = false
        and user_id in (select id from users where couple_id = auth_couple_id())
    );
create policy categories_owner on categories for all
    using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy categories_partner_read on categories for select
    using (
        is_deleted = false
        and user_id in (select id from users where couple_id = auth_couple_id())
    );

-- ---- recurring_rules (owner-only — partners don't need each other's rules) --
create policy recurring_owner on recurring_rules for all
    using (user_id = auth.uid()) with check (user_id = auth.uid());

-- ---- transactions ----------------------------------------------------------
-- Owner: full CRUD. Partner: read only non-private, non-deleted rows (combined view).
create policy transactions_owner on transactions for all
    using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy transactions_partner_read on transactions for select
    using (
        is_private = false
        and is_deleted = false
        and user_id in (select id from users where couple_id = auth_couple_id())
    );

-- ---- budgets ---------------------------------------------------------------
-- Personal budgets: owner only. Shared budgets: any couple member.
create policy budgets_owner on budgets for all
    using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy budgets_couple on budgets for all
    using (couple_id = auth_couple_id())
    with check (couple_id = auth_couple_id());

-- ---- notes -----------------------------------------------------------------
-- Owner: full CRUD. Partner: read only shared notes in the same couple.
create policy notes_owner on notes for all
    using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy notes_partner_read on notes for select
    using (
        is_shared = true
        and is_deleted = false
        and couple_id = auth_couple_id()
    );

-- ---- note_images -----------------------------------------------------------
-- Visible if the parent note is visible to the caller (owner or shared partner).
create policy note_images_access on note_images for all
    using (
        note_id in (
            select id from notes
            where user_id = auth.uid()
               or (is_shared = true and couple_id = auth_couple_id())
        )
    )
    with check (
        note_id in (select id from notes where user_id = auth.uid())
    );
