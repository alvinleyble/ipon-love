-- Add opt-in rollover support to budgets (V1.6.2 Item 5, ADR-0036).
-- The effective limit (amount + carried leftover/deficit) is computed at read time
-- client-side, chaining backward through consecutive year_month rows — nothing else
-- to store server-side beyond the toggle itself.

alter table budgets
  add column rollover_enabled boolean not null default false;
