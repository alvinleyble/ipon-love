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
-- Personal: user_id set, couple_id null. Shared (couple-owned, ADR-0018): the
-- reverse, exactly like budgets. created_by records the creator so un-share/unpair
-- can revert-to-creator (user_id = created_by, couple_id = null), preserving the
-- account + its balance/history for whoever made it.
create table accounts (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid references users(id) on delete cascade,
    couple_id       uuid references couples(id) on delete cascade,
    created_by      uuid references users(id) on delete set null,
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
    server_rev      bigint,
    constraint account_owner_chk check (
        (user_id is not null and couple_id is null) or
        (user_id is null and couple_id is not null)
    )
);

-- ---------- categories ------------------------------------------------------
-- Personal vs. shared (couple-owned) mirrors accounts above (ADR-0018).
create table categories (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid references users(id) on delete cascade,
    couple_id   uuid references couples(id) on delete cascade,
    created_by  uuid references users(id) on delete set null,
    name        text not null,
    type        category_type not null,
    icon        text,
    color       text,
    position    int not null default 0,
    is_archived boolean not null default false,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    is_deleted  boolean not null default false,
    server_rev  bigint,
    constraint category_owner_chk check (
        (user_id is not null and couple_id is null) or
        (user_id is null and couple_id is not null)
    )
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
    attachment_url    text,                                                -- Supabase Storage URL for receipt photo (post-V1)
    is_settlement     boolean not null default false,                      -- partner-debt settlement leg: counts toward balance, excluded from Analysis (ADR-0019 #14)
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
    id               uuid primary key default gen_random_uuid(),
    user_id          uuid not null references users(id) on delete cascade,
    title            text,
    content          jsonb,                             -- rich text delta
    is_shared        boolean not null default false,
    couple_id        uuid references couples(id) on delete set null,
    is_conflict_copy boolean not null default false,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    is_deleted       boolean not null default false,
    server_rev       bigint
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
    -- Display-only back-reference to the transaction that spawned this debt via the
    -- "paid for partner" toggle (ADR-0019 #12). No FK: it's fire-and-forget — editing or
    -- deleting that transaction must never cascade to the debt, and it may point at a row
    -- the reader can't even see (the lender's private expense).
    source_transaction_id uuid,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    is_deleted  boolean not null default false,
    server_rev  bigint
);

-- ---------- partner_debt_payments -------------------------------------------
-- Each row is one (partial or full) repayment against a partner_debt.
-- A *netting* payment (is_netting = true, ADR-0019 #9) is an auto-generated
-- offset created when an opposing debt is recorded: it carries counter_debt_id,
-- the opposing debt it offsets. Its id is a deterministic UUIDv5 of the two debt
-- ids, so two partners netting the same opposing pair offline converge on one
-- row instead of double-netting. counter_debt_id is null for manual repayments.
create table partner_debt_payments (
    id              uuid primary key default gen_random_uuid(),
    debt_id         uuid not null references partner_debts(id) on delete cascade,
    amount          numeric(14,2) not null,
    note            text,
    date            timestamptz not null,
    is_netting      boolean not null default false,
    counter_debt_id uuid references partner_debts(id) on delete cascade,
    -- Settlement legs (ADR-0019 #14): payor's account + EXPENSE txn, and the receiver's
    -- optional INCOME txn once they add it to their account. No FK to transactions: those
    -- are per-user rows on different devices; this is a display/audit link, fire-and-forget.
    payor_account_id uuid,
    payor_txn_id     uuid,
    receiver_txn_id  uuid,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    is_deleted      boolean not null default false,
    server_rev      bigint
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

-- ---------- savings_goals ---------------------------------------------------
-- Personal-by-default savings goal, optionally shared to the couple via the generic
-- sharing layer (is_shared + couple_id), exactly like notes. saved_amount is NOT a column:
-- it is DERIVED from goal_contributions (ADR-0025), so concurrent contributions from both
-- partners never clobber a shared mutable counter under row-level LWW (ADR-0001/0007).
-- Metadata (name/target/date/icon/color) is creator-owned; only the creator edits it.
-- Un-sharing sets is_shared=false but RETAINS couple_id so the transition still reaches the
-- partner's redacting view as a purge signal (ADR-0005); only unpair nulls couple_id.
create table savings_goals (
    id            uuid primary key default gen_random_uuid(),
    user_id       uuid not null references users(id) on delete cascade,   -- creator / owner
    couple_id     uuid references couples(id) on delete set null,
    is_shared     boolean not null default false,
    name          text not null,
    target_amount numeric(14,2) not null,
    target_date   date,                                                   -- optional deadline
    icon          text,
    color         text,
    is_archived   boolean not null default false,
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    is_deleted    boolean not null default false,
    server_rev    bigint
);

-- ---------- goal_contributions ----------------------------------------------
-- Append-only ledger backing a goal's DERIVED saved_amount. Each row is owned by its
-- CONTRIBUTOR (user_id), so on a shared goal BOTH partners contribute, each writing their
-- own independent rows — distinct ids never conflict under LWW (ADR-0025), which a shared
-- stored counter would. No couple_id / is_shared column: a contribution inherits its
-- shared-ness from its parent goal (single source of truth), gated via the join in
-- partner_goal_contributions below.
create table goal_contributions (
    id         uuid primary key default gen_random_uuid(),
    goal_id    uuid not null references savings_goals(id) on delete cascade,
    user_id    uuid not null references users(id) on delete cascade,   -- contributor
    amount     numeric(14,2) not null,
    note       text,
    date       timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    is_deleted boolean not null default false,
    server_rev bigint
);

-- ---------- no private spend on a shared account (ADR-0018) -----------------
-- A shared account's balance is shown to both partners, which is only computable
-- because ALL its activity is non-private (the principled carve-out from ADR-0011).
-- This is the DB-level backstop to TransactionValidator: a CHECK can't span tables, so
-- a trigger enforces it. Covers both the source (account_id) and a transfer's
-- destination (to_account_id) — either touching a couple-owned account forces non-private.
create or replace function enforce_no_private_on_shared_account()
returns trigger
language plpgsql
as $$
begin
    if new.is_private and new.is_deleted = false
       and exists (
           select 1 from accounts
           where id in (new.account_id, new.to_account_id)
             and couple_id is not null
       ) then
        raise exception 'private transactions are not allowed on a shared account';
    end if;
    return new;
end;
$$;

create trigger trg_no_private_on_shared
    before insert or update on transactions
    for each row execute function enforce_no_private_on_shared_account();

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
create trigger trg_rev_savings_goals      before insert or update on savings_goals       for each row execute function set_server_rev();
create trigger trg_rev_goal_contributions before insert or update on goal_contributions  for each row execute function set_server_rev();

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
create index idx_savings_goals_rev        on savings_goals(server_rev);
create index idx_goal_contributions_rev   on goal_contributions(server_rev);

create index idx_accounts_user        on accounts(user_id);
create index idx_accounts_couple      on accounts(couple_id);
create index idx_categories_user      on categories(user_id);
create index idx_categories_couple    on categories(couple_id);
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
create index idx_savings_goals_user       on savings_goals(user_id);
create index idx_savings_goals_couple     on savings_goals(couple_id);
create index idx_goal_contributions_goal  on goal_contributions(goal_id);
create index idx_goal_contributions_user  on goal_contributions(user_id);

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
alter table savings_goals           enable row level security;
alter table goal_contributions      enable row level security;

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

-- Shared (couple-owned) accounts & categories: any couple member, full access, no
-- redaction — jointly owned, exactly like budgets_couple. These rows replicate to both
-- partners through the BASE-table pull (this policy returns them), NOT the redacting
-- partner_* views, so opening_balance crosses and the joint balance is computable. [ADR-0018]
create policy accounts_couple on accounts for all
    using (couple_id = auth_couple_id())
    with check (couple_id = auth_couple_id());
create policy categories_couple on categories for all
    using (couple_id = auth_couple_id())
    with check (couple_id = auth_couple_id());
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

-- ---- savings_goals ---------------------------------------------------------
-- Writes are owner-only (the creator owns the metadata — name/target/date/icon/color).
-- SELECT is broader — own goals PLUS goals shared into the caller's couple — because the
-- partner must read a shared goal's BASE row so a goal_contributions insert can reference
-- it (the redacting partner_savings_goals view can't satisfy an RLS sub-select). A shared
-- goal has no per-field privacy, so exposing its base row leaks nothing the redacting view
-- wasn't already showing. INSERT/UPDATE/DELETE stay owner-only.                 [ADR-0025]
create policy savings_goals_select on savings_goals for select
    using (user_id = auth.uid() or (couple_id = auth_couple_id() and is_shared));
create policy savings_goals_insert on savings_goals for insert
    with check (user_id = auth.uid());
create policy savings_goals_update on savings_goals for update
    using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy savings_goals_delete on savings_goals for delete
    using (user_id = auth.uid());

-- ---- goal_contributions ----------------------------------------------------
-- You read/edit/delete only your OWN contribution rows (`using`); partner contributions
-- arrive via the partner_goal_contributions view, never this base table. You may INSERT a
-- contribution only against a goal you can access — your own, or a goal shared into your
-- couple — so both partners fund a shared goal (`with check`). The savings_goals sub-select
-- is satisfied by savings_goals_select above.                                   [ADR-0025]
create policy goal_contributions_author on goal_contributions for all
    using (user_id = auth.uid())
    with check (
        user_id = auth.uid()
        and goal_id in (
            select id from savings_goals
            where user_id = auth.uid()
               or (couple_id = auth_couple_id() and is_shared)
        )
    );

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
        t.server_rev,
        case when t.is_private or t.is_deleted then null else t.attachment_url end as attachment_url
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
    where a.couple_id is null              -- couple-owned accounts cross via the base table (ADR-0018), not here
      and a.user_id <> auth.uid()
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
    where c.couple_id is null              -- couple-owned categories cross via the base table (ADR-0018), not here
      and c.user_id <> auth.uid()
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

-- A savings goal is hidden from the partner when unshared or deleted; its row still crosses
-- (couple_id retained on un-share) so the client purges its local copy. A shared goal has no
-- per-field privacy beyond the shared/deleted gate — name/target are the point of sharing.
create view partner_savings_goals with (security_invoker = false) as
    select
        g.id,
        g.user_id,
        case when g.is_shared = false or g.is_deleted then null else g.name          end as name,
        case when g.is_shared = false or g.is_deleted then null else g.target_amount  end as target_amount,
        case when g.is_shared = false or g.is_deleted then null else g.target_date    end as target_date,
        case when g.is_shared = false or g.is_deleted then null else g.icon           end as icon,
        case when g.is_shared = false or g.is_deleted then null else g.color          end as color,
        case when g.is_shared = false or g.is_deleted then null else g.is_archived    end as is_archived,
        g.is_shared,
        g.is_deleted,
        g.couple_id,
        g.updated_at,
        g.server_rev
    from savings_goals g
    where g.user_id <> auth.uid()
      and g.couple_id = auth_couple_id();

-- The partner's contributions to any goal shared in the couple (whether they or you own the
-- goal). amount/note/date are nulled once the contribution is deleted OR its parent goal is
-- unshared/deleted, so the removal propagates to the partner's replica as a purge signal
-- (ADR-0005). A contribution carries no couple_id — its shared-ness is the goal's, via the join.
create view partner_goal_contributions with (security_invoker = false) as
    select
        gc.id,
        gc.goal_id,
        gc.user_id,
        case when gc.is_deleted or g.is_deleted or g.is_shared = false then null else gc.amount end as amount,
        case when gc.is_deleted or g.is_deleted or g.is_shared = false then null else gc.note   end as note,
        case when gc.is_deleted or g.is_deleted or g.is_shared = false then null else gc.date   end as date,
        gc.is_deleted,
        gc.updated_at,
        gc.server_rev
    from goal_contributions gc
    join savings_goals g on g.id = gc.goal_id
    where gc.user_id <> auth.uid()
      and g.couple_id = auth_couple_id();

grant select on partner_savings_goals, partner_goal_contributions to authenticated;

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
-- notes + savings goals revert to private-to-owner, shared accounts & categories
-- revert-to-creator, couple soft-deleted. Each client then bulk-purges replicated non-owned rows on
-- seeing its own couple_id go null.                                     [ADR-0008, 0018]
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

    -- Shared savings goals revert to the creator's personal goals (ADR-0025), like notes.
    -- Goal contributions are untouched: each is owned by its contributor, and each client
    -- purges the OTHER partner's contribution replicas when it sees couple_id clear. Own
    -- contributions to an ex-partner's goal are left as benign, invisible orphans.
    update savings_goals set is_shared = false, couple_id = null, updated_at = now()
        where couple_id = v_couple_id and is_shared = true;

    -- Shared accounts & categories revert to their creator's personal rows (ADR-0018):
    -- the creator keeps the account with its balance/history; the other partner's client
    -- purges its replica (created_by <> self) when it sees couple_id clear.
    update accounts set user_id = created_by, couple_id = null, updated_at = now()
        where couple_id = v_couple_id and created_by is not null;

    update categories set user_id = created_by, couple_id = null, updated_at = now()
        where couple_id = v_couple_id and created_by is not null;

    -- Ring the live-sync bell (ADR-0015) from the DATABASE, inside this same transaction, so the
    -- partner who did NOT initiate the unpair pulls immediately instead of waiting for a coarse
    -- trigger (app resume / periodic background sync / manual pull-to-refresh). This is the only
    -- server-side broadcast in the schema, and it has to be one: the instant unpair() commits the
    -- initiator's own auth_couple_id() goes null (ADR-0008), so a CLIENT ping after the RPC is
    -- RLS-blocked by couple_channel_members, and pinging before the RPC races the mutation.
    -- realtime.send() runs inside this SECURITY DEFINER (postgres-owned => BYPASSRLS) function, so
    -- it is exempt from that per-broadcaster RLS by construction; emitting it BEFORE we null
    -- couple_id also keeps the channel predicate satisfied even if that exemption were ever removed.
    -- Payload is an EMPTY object: the ping carries zero row data (ADR-0015 redaction) and the
    -- partner reacts by PULLING through the redacting views (ADR-0005). private=true matches the
    -- client's isPrivate channel, and Realtime fans the message out only after COMMIT, so the
    -- partner never pulls before the mutation lands.
    perform realtime.send(
        '{}'::jsonb,                     -- payload: empty, content-less bell (ADR-0015)
        'changed',                       -- event: matches SupabaseCoupleBell.EVENT
        'couple:' || v_couple_id::text,  -- topic: this dissolving couple's channel
        true                             -- private: matches the client's isPrivate = true
    );

    update users set couple_id = null, updated_at = now() where couple_id = v_couple_id;

    update couples set is_deleted = true, updated_at = now() where id = v_couple_id;
end;
$$;

-- Returns the current server time. Used by the Android client to calibrate its
-- SyncClock offset (ADR-0001) at the end of each successful sync.
create or replace function get_server_time()
returns timestamptz
language sql
security definer
set search_path = public
as $$ select now() $$;

-- ============================================================================
--  Realtime "bell" channel authorization  [ADR-0015]
--  Live sync uses a PRIVATE Realtime Broadcast channel `couple:{coupleId}` as a
--  content-less notification: after a push that sent rows, the writer broadcasts a
--  tiny "changed" ping; the partner reacts by PULLING — and partner data still flows
--  only through the redacting views (ADR-0005), so the ping carrying no row data
--  means redaction is never bypassed.
--
--  Private channels are gated by RLS on realtime.messages. This policy lets a member
--  subscribe to (select) and broadcast on (insert) only their OWN couple's topic:
--  topic = 'couple:' || their auth_couple_id(). Without it, anyone who learned a
--  couple id could spam pull-triggers at a couple or probe whether it is active —
--  matching the security-first posture of the redacting-views design. public-schema
--  qualify the helper since the policy is evaluated in the realtime schema context.
-- ============================================================================
alter table realtime.messages enable row level security;

create policy couple_channel_members on realtime.messages
    for all
    to authenticated
    using (
        public.auth_couple_id() is not null
        and realtime.topic() = 'couple:' || public.auth_couple_id()::text
    )
    with check (
        public.auth_couple_id() is not null
        and realtime.topic() = 'couple:' || public.auth_couple_id()::text
    );

-- ============================================================================
--  Receipts Storage bucket  [ADR-0020]
--  Owner: full access scoped to their own folder receipts/{userId}/...
--  Partner read: a couple member can SELECT (download) the partner's receipt
--  object if it appears in a non-private, non-deleted transaction row — same
--  visibility gate as the partner_transactions redacting view.
-- ============================================================================

alter table storage.objects enable row level security;

create policy receipts_owner on storage.objects for all
    to authenticated
    using (
        bucket_id = 'receipts'
        and (storage.foldername(name))[1] = auth.uid()::text
    )
    with check (
        bucket_id = 'receipts'
        and (storage.foldername(name))[1] = auth.uid()::text
    );

create policy receipts_partner_read on storage.objects for select
    to authenticated
    using (
        bucket_id = 'receipts'
        and exists (
            select 1 from transactions t
            where t.attachment_url like '%' || name || '%'
              and t.is_private = false
              and t.is_deleted = false
              and t.user_id in (
                  select id from users
                  where couple_id = public.auth_couple_id()
                    and id <> auth.uid()
              )
        )
    );
