package com.iponlove.app.core.sync

/**
 * True when a dirty row of a *flip-model* table (accounts, categories, budgets — the ones whose
 * ownership flips to `couple_id` with a null `user_id` when shared, ADR-0018) can actually be
 * pushed by this session, i.e. the server's RLS will accept it: it is the user's own personal
 * row (`userId == me`), or a couple-owned row of the user's **current** couple
 * (`coupleId == myCoupleId`).
 *
 * A row that is partner-owned, bears a stale `coupleId` from a dissolved couple (the unpair
 * race), or was reverted-to-creator onto the *other* partner (the un-share poison row,
 * v1.6.5 Item 20) can never satisfy that check and would fail the whole table's atomic upsert
 * batch — wedging every pending row, and with it sign-out (ADR-0021). The flip-model syncers
 * filter such rows out of the push, leaving them benign local orphans until a pull converges
 * them. Generalises the `goal_contributions` F1 guard (ADR-0025) to the flip-model tables.
 *
 * [myCoupleId] null (unpaired, or the local users row absent) admits only own personal rows —
 * any dirty couple row is then stale and correctly skipped.
 */
fun isLocallyPushable(userId: String?, coupleId: String?, me: String, myCoupleId: String?): Boolean =
    userId == me || (coupleId != null && coupleId == myCoupleId)

/**
 * The couple-scoped counterpart of [isLocallyPushable], for the *always-couple* tables
 * (`partner_debts` / `partner_debt_payments`, v1.6.5 Item 20 follow-up). These are **not**
 * flip-model — `couple_id` is `NOT NULL`, there is no personal form, and RLS is purely
 * `couple_id = auth_couple_id()` — so a row is pushable exactly when its couple is this
 * session's **current** couple.
 *
 * The hazard is the same unpair-race F1 residual budgets shared: a dirty row still stamped
 * with a dissolved couple's id can never satisfy RLS and would fail the table's atomic upsert
 * batch, wedging every pending row (and sign-out, ADR-0021). Both debt tables are purged on
 * unpair, so this only guards the narrow window where a dirty row outlives (or is re-created
 * after) that purge; skipped rows stay benign local orphans until a pull converges them.
 *
 * `partner_debt_payments` carries no `couple_id` of its own — its couple is its parent debt's
 * (`debt_id -> partner_debts.couple_id`), matching the payment RLS subquery — so the caller
 * resolves the parent debt's couple before applying this.
 *
 * [myCoupleId] null (unpaired, or the local users row absent) makes every couple row stale and
 * correctly skipped.
 */
fun isCoupleRowPushable(coupleId: String?, myCoupleId: String?): Boolean =
    myCoupleId != null && coupleId == myCoupleId
