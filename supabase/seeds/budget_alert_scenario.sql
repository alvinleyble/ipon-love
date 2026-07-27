-- ============================================================================
--  Test seed: put one budget at an exact % spent, so the budget-alert rungs fire
--
--  WHY THIS EXISTS
--  Verifying anything in the notification subsystem (v1.7.1 Items 2-4 and 6)
--  needs a budget sitting at a known percentage. Building that through the UI
--  costs ~30 emulator taps per scenario — create category, create budget, create
--  transaction, repeat — which dominated the cost of Item 6's verification run.
--  This does it in one statement.
--
--  It seeds the SERVER, not the device, on purpose: the client still pulls the
--  rows, still recomputes the percentage, and CheckBudgetAlertsUseCase still
--  decides what to raise. So the real pipeline is exercised end-to-end — only the
--  data entry is skipped, not the logic under test.
--
--  HOW TO RUN
--  Via the Supabase MCP (`execute_sql`) against staging. Edit the params block
--  below, run, then in the app pull-to-refresh (or background/foreground it) to
--  trigger a sync. The alert fires on the sync that follows.
--
--  Re-running is safe: it soft-deletes anything it seeded earlier for the same
--  user + category + month before inserting, so percentages don't accumulate.
--
--  TYPICAL SEQUENCE for the three rungs (run once per rung, syncing between):
--    p_percent =  85  -> warn  rung  (default threshold 80)
--    p_percent = 100  -> limit rung
--    p_percent = 130  -> over  rung  (default threshold 120, opt-in)
--
--  NOTE ON THE INBOX ROW: notification ids embed the slot, not the percentage
--  (`budget:{id}:{month}:{warn|limit|over}`), and generation is create-if-absent.
--  So re-seeding the SAME rung will not raise a second notification — the row
--  already exists. To re-test one rung from scratch, delete its inbox row:
--    delete from notifications
--     where user_id = '<uid>' and id like 'budget:%:warn';
--
--  VERIFIED on staging 2026-07-27 against testdev2: seeded 85%, re-seeded 130%,
--  confirmed exactly one active budget and one active transaction after both runs
--  (no accumulation).
--
--  CLEANUP — undo everything this script seeded for a user:
--    update transactions set is_deleted = true, updated_at = now()
--     where user_id = '<uid>' and note = 'seed:budget_alert_scenario';
--    update budgets b set is_deleted = true, updated_at = now()
--      from categories c
--     where c.id = b.category_id and b.user_id = '<uid>' and c.name = 'Alert Test';
-- ============================================================================

with params as (
    select
        'testdev2@iponlove.com'::text as p_email,      -- which test account
        'Alert Test'::text           as p_category,    -- category name (created if absent)
        5000.00::numeric             as p_budget,      -- budget amount for the month
        85::numeric                  as p_percent,     -- target % of budget to have spent
        to_char(now(), 'YYYY-MM')::text as p_month     -- current month; override to backfill
),
usr as (
    select u.id, p.*
    from params p
    join auth.users au on au.email = p.p_email
    join users u on u.id = au.id
),
-- Any of the user's accounts will do; transactions need one and the balance is
-- irrelevant to budget percentage.
acct as (
    select u.id as user_id, (
        select a.id from accounts a
        where a.user_id = u.id and a.is_deleted = false
        order by a.created_at limit 1
    ) as account_id
    from usr u
),
cat as (
    insert into categories (user_id, created_by, name, type, updated_at)
    select u.id, u.id, u.p_category, 'EXPENSE', now()
    from usr u
    where not exists (
        select 1 from categories c
        where c.user_id = u.id and c.name = u.p_category and c.is_deleted = false
    )
    returning id, user_id
),
cat_id as (
    select coalesce(
        (select id from cat),
        (select c.id from categories c, usr u
          where c.user_id = u.id and c.name = u.p_category and c.is_deleted = false
          limit 1)
    ) as id
),
-- Idempotence: retire the previous seed for this user/category/month first, so a
-- re-run sets the percentage rather than adding to it.
wipe_txn as (
    update transactions t
       set is_deleted = true, updated_at = now()
      from usr u, cat_id c
     where t.user_id = u.id
       and t.category_id = c.id
       and t.note = 'seed:budget_alert_scenario'
       and t.is_deleted = false
    returning t.id
),
wipe_budget as (
    update budgets b
       set is_deleted = true, updated_at = now()
      from usr u, cat_id c
     where b.user_id = u.id
       and b.category_id = c.id
       and b.year_month = u.p_month
       and b.is_deleted = false
    returning b.id
),
new_budget as (
    insert into budgets (user_id, category_id, amount, year_month, updated_at)
    select u.id, c.id, u.p_budget, u.p_month, now()
    from usr u, cat_id c
    returning id, amount
),
new_txn as (
    insert into transactions (user_id, type, amount, category_id, account_id, note, date, updated_at)
    select u.id,
           'EXPENSE',
           round(u.p_budget * u.p_percent / 100.0, 2),
           c.id,
           a.account_id,
           'seed:budget_alert_scenario',
           now(),
           now()
    from usr u, cat_id c, acct a
    returning amount
)
select u.p_email        as account,
       u.p_category     as category,
       u.p_month        as month,
       b.amount         as budget_amount,
       t.amount         as spent,
       u.p_percent      as pct,
       'now pull-to-refresh in the app' as next_step
from usr u, new_budget b, new_txn t;
