-- Migration: transfer_fee_transaction_id column on transactions (Item 12, ADR-0031).
-- Apply on the live Supabase project (vyjaorlevomfqkidttom) via the SQL Editor.
-- Idempotent: safe to re-run.
--
-- Links a TRANSFER row to its optional fee, recorded as a second, linked EXPENSE row
-- (auto-categorized under "Transfer fees") rather than a plain field, so it's real,
-- groupable spend in Analysis. No FK: same rationale as the rest of this table's
-- omitted FKs (a pulled batch can arrive out of dependency order) — the client owns
-- the cascade (editing/deleting the transfer replaces/retires the linked row).

alter table transactions add column if not exists transfer_fee_transaction_id uuid;
