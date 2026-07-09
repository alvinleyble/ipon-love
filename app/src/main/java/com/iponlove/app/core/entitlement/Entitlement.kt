package com.iponlove.app.core.entitlement

import java.time.Instant

/** Mirrors `entitlement_source` on the synced `users` row (D2 / ADR-0044 §4). A [GRANT] is a
 *  beta comp written server-side; the foreground Play-reconcile loop (S4) must skip it (G7) so
 *  it is never overwritten by a `queryPurchasesAsync` NOT_OWNED result. */
enum class EntitlementSource { PLAY, GRANT, NONE }

/**
 * A user's premium entitlement snapshot, mirrored 1:1 from `users.is_premium` /
 * `premium_until` / `entitlement_source` (D2 / ADR-0044). [premiumUntil] is dormant under
 * one-time billing — D7 collapses the state machine to OWNED/NOT_OWNED — but is still honored
 * by [isActive] so a future subscription re-pivot needs no change here.
 */
data class Entitlement(
    val isPremium: Boolean,
    val premiumUntil: Instant?,
    val source: EntitlementSource,
) {
    fun isActive(now: Instant): Boolean =
        isPremium && (premiumUntil == null || premiumUntil.isAfter(now))

    companion object {
        val NONE = Entitlement(isPremium = false, premiumUntil = null, source = EntitlementSource.NONE)
    }
}
