-- Catch-up migration: columns that were applied ad-hoc via the SQL editor during
-- the pre-migrations baseline era (before migrations/ existed on 2026-06-27) and
-- never got a committed migration. All are already live on staging; every clause
-- is `add column if not exists`, so this is a no-op there and only does work on a
-- from-scratch replay. Closes the schema.sql <-> migrations/ replay-completeness gap.

-- users: combined-view accent color (ADR-0014)
alter table users
    add column if not exists accent_color text;

-- accounts: manual ordering + archive (ADR-0018 / V1.3 icon+order work)
alter table accounts
    add column if not exists position    int not null default 0,
    add column if not exists is_archived boolean not null default false;

-- categories: same ordering + archive as accounts
alter table categories
    add column if not exists position    int not null default 0,
    add column if not exists is_archived boolean not null default false;

-- transactions: receipt attachment (post-V1) + debt-settlement leg (ADR-0019 #14, 87ee6c4)
alter table transactions
    add column if not exists attachment_url text,
    add column if not exists is_settlement  boolean not null default false;

-- notes: shared-note conflict copy (ADR-0003)
alter table notes
    add column if not exists is_conflict_copy boolean not null default false;

-- partner_debt_payments: transaction-linked settlement legs (ADR-0019 #14, 87ee6c4).
-- No FK to transactions/accounts: display/audit link, fire-and-forget (matches schema.sql).
alter table partner_debt_payments
    add column if not exists payor_account_id uuid,
    add column if not exists payor_txn_id     uuid,
    add column if not exists receiver_txn_id  uuid;

-- PostgREST caches the schema; nudge a reload so any newly-added columns are visible.
notify pgrst, 'reload schema';
