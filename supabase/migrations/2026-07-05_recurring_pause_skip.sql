-- Add pause support to recurring_rules (V1.6.1 Item 18, ADR-0035).
-- "Skip next" is not a stored column — it advances next_date one interval forward,
-- so only the pause flag needs a new column.

alter table recurring_rules
  add column is_paused boolean not null default false;

-- Add an "Annually" (YEARLY) option to the recurring frequency enum.
-- Placed before CUSTOM to keep the natural DAILY→WEEKLY→MONTHLY→YEARLY ordering.
alter type recurring_frequency add value if not exists 'YEARLY' before 'CUSTOM';
