alter table partner_debts
    add column if not exists source_transaction_id uuid;

alter table partner_debt_payments
    add column if not exists is_netting boolean not null default false,
    add column if not exists counter_debt_id uuid references partner_debts(id) on delete cascade;

-- PostgREST caches the schema; nudge a reload so the new columns are visible immediately.
notify pgrst, 'reload schema';
