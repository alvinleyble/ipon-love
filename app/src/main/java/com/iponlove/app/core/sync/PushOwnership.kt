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
