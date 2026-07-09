package com.iponlove.app.core.entitlement

/**
 * The boundary math for a count-capped entity (CONTEXT.md "Cap count" / G1 / T1 Freeze): a
 * create is blocked once [currentCount] has already reached [limit] — block-on-create, never
 * block-on-exceed, so this predicate is consulted at create-time only. [currentCount] must
 * already reflect G1's counting rule (all non-deleted rows, archived included; un-settled only
 * for partner debts) — that count is a per-entity DAO query built when each cap is wired
 * (Phase 2), not this object's concern.
 */
object CapCount {
    fun blocksCreate(currentCount: Int, limit: Int): Boolean = currentCount >= limit
}
