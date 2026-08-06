-- ============================================================================
--  Transaction drafts — the parking area  [v1.7.3 Item 8 / ADR-0066]
--
--  A draft is an UNFINISHED transaction form, parked so a busy user can settle
--  it later. It deliberately does NOT live behind an `is_draft` flag on
--  `transactions`: `type`/`amount`/`account_id` are all `not null` there and
--  TransactionValidator additionally requires amount > 0 + an account + a
--  category, all four of which a draft may legitimately fail. A flag would have
--  forced either permanently-nullable columns on the money table or placeholder
--  junk values (ADR-0066 decision 1).
--
--  Because it is a separate table, money-math exclusion is BY CONSTRUCTION —
--  there is no `WHERE is_draft = 0` predicate anywhere, and no query, calculator
--  or partner view changed to land this feature (decision 2).
--
--  Own-user-only, the `notifications` shape: RLS is a single own-row `for all`
--  policy, there is NO partner redacting view and none may be added, and the
--  table is never replicated. A partner can therefore never see a draft
--  structurally, not by policy — so ADR-0004/0005/0011 and the frozen contract
--  §5.3 purge-predicate table need no amendment (decision 3).
--
--  Every content column is nullable, because a draft is a partial form. No FK on
--  `category_id` / `account_id`, matching the same pull-order tolerance
--  `transfer_fee_transaction_id` is given: a parked draft must survive its
--  category being archived while it waits, and must not fail to land because a
--  pulled batch arrived out of dependency order. It degrades to "Uncategorized"
--  on read, exactly as a historical transaction does.
--
--  `id` is an ordinary random v4 uuid — and it IS the future transaction's id
--  (the editor pre-generates it). That is what makes promotion need ORDERING,
--  not atomicity: write the transaction first, retire the draft second, and a
--  re-run is an idempotent upsert of the same id, so money can never double.
--  No `SECURITY DEFINER` RPC, no contract §9 amendment (decision 5).
--
--  `receipt_count` syncs but the photo does not: the file stays local until
--  promotion puts it on the existing transaction_images → Storage path, so a
--  second device renders "📷 1 receipt — on your other device" (decision 4).
--  The Room entity's `local_image_ids` is local-only and is NOT a column here.
-- ============================================================================

create table transaction_drafts (
    id            uuid primary key default gen_random_uuid(),          -- == the future transactions.id
    user_id       uuid not null references users(id) on delete cascade,
    type          transaction_type,                                    -- every content column nullable:
    amount        numeric(14,2),                                       --   a draft is a partial form
    category_id   uuid,                                                -- no FK, deliberately (see header)
    account_id    uuid,                                                -- no FK, deliberately
    to_account_id uuid,                                                -- no FK, deliberately
    note          text,
    date          timestamptz,
    is_private    boolean not null default false,
    receipt_count int not null default 0,                              -- photos held locally on the authoring device
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now(),
    is_deleted    boolean not null default false,
    server_rev    bigint
);

create trigger trg_rev_transaction_drafts
    before insert or update on transaction_drafts
    for each row execute function set_server_rev();

create index idx_transaction_drafts_rev  on transaction_drafts(server_rev);
create index idx_transaction_drafts_user on transaction_drafts(user_id);

alter table transaction_drafts enable row level security;

-- Own rows only, all verbs. There is deliberately NO partner read of any kind:
-- a draft is not shared spending (ADR-0011) and may be a half-typed wrong figure
-- or a receipt for the partner's own gift.
create policy transaction_drafts_owner on transaction_drafts for all
    using (user_id = auth.uid()) with check (user_id = auth.uid());
