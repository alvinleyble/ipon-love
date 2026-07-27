-- ============================================================================
--  Test seed: a confirm-on-arrival recurring rule already due, for verifying
--  v1.7.1 Item 1 (recurring due-date reminder notification, ADR-0052) without
--  driving 30+ taps through the recurring-rule editor.
--
--  Seeds the SERVER, matching supabase/seeds/budget_alert_scenario.sql's
--  approach: the client still pulls the rule, still runs
--  ObservePendingConfirmationsUseCase / CheckRecurringDueUseCase for real —
--  only the data entry is skipped.
--
--  HOW TO RUN: via the Supabase MCP (execute_sql) against staging. Edit the
--  params block, run, then pull-to-refresh (or background/foreground) the app.
--  The reminder fires on the sync that follows (RecurringReminderWorker, wired
--  at MainActivity post-sync + SyncWorker post-sync).
--
--  Re-running is safe: soft-deletes the previous seed for this user + note
--  before inserting a fresh rule (a fresh id — deliberately, so the deterministic
--  occurrence id changes too and the backlog/inbox dedup doesn't mask a re-test).
--
--  CLEANUP:
--    update recurring_rules set is_deleted = true, updated_at = now()
--     where user_id = '<uid>' and template->>'note' = 'seed:recurring_reminder_scenario';
-- ============================================================================

with params as (
    select
        'testdev2@iponlove.com'::text as p_email,      -- which test account
        'Reminder Test'::text        as p_category,    -- category name (created if absent)
        'EXPENSE'::category_type     as p_category_type,-- EXPENSE -> "Have you paid for..."; INCOME -> "Did your ... arrive?"
        1500.00::numeric             as p_amount,
        (current_date - 3)::date     as p_due_date      -- in the past -> already due, catch-up fires
),
usr as (
    select u.id, p.*
    from params p
    join auth.users au on au.email = p.p_email
    join users u on u.id = au.id
),
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
    select u.id, u.id, u.p_category, u.p_category_type, now()
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
wipe_rule as (
    update recurring_rules r
       set is_deleted = true, updated_at = now()
      from usr u
     where r.user_id = u.id
       and r.template->>'note' = 'seed:recurring_reminder_scenario'
       and r.is_deleted = false
    returning r.id
),
new_rule as (
    insert into recurring_rules (user_id, frequency, next_date, template, auto_post, updated_at)
    select u.id,
           'MONTHLY',
           u.p_due_date,
           jsonb_build_object(
               'amount', u.p_amount,
               'account_id', a.account_id,
               'category_id', c.id,
               'note', 'seed:recurring_reminder_scenario'
           ),
           false,
           now()
    from usr u, acct a, cat_id c
    returning id, next_date
)
select u.p_email        as account,
       u.p_category     as category,
       u.p_category_type as category_type,
       r.next_date      as due_date,
       u.p_amount       as amount,
       'now pull-to-refresh in the app' as next_step
from usr u, new_rule r;
