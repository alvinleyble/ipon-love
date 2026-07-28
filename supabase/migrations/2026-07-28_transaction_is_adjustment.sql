-- v1.7.1 Item 13 (ADR-0057): manual balance-correction ledger rows.
-- `is_adjustment` mirrors `is_settlement`'s shape — a marked, real, dated transaction row:
-- counts toward balance (ADR-0007) and appears in Records/Combined feed, but is excluded from
-- Analysis, Budgets, and Combined spend totals (client-side calculators, no RLS/view change there).

alter table transactions
    add column if not exists is_adjustment boolean not null default false;

-- Rebuild partner_transactions to surface the new column to the partner-read path (ADR-0005).
-- CREATE OR REPLACE VIEW can only append columns at the end, not insert one mid-list, so this
-- drops and recreates rather than replacing in place.
drop view if exists partner_transactions;

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
        t.is_adjustment,
        t.updated_at,
        t.server_rev
        -- Receipts cross via partner_transaction_images (below), not this view.
    from transactions t
    where t.user_id <> auth.uid()
      and t.user_id in (select id from users where couple_id = auth_couple_id());

grant select on partner_transactions to authenticated;
