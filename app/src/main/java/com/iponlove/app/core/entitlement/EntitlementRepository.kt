package com.iponlove.app.core.entitlement

import kotlinx.coroutines.flow.Flow

/**
 * The runtime read + reconcile face of premium entitlement (paywall S4). The public seam every
 * gate (S7+) and the paywall screen (S5) consult — deliberately in `core/entitlement`, not
 * `feature/user`, so consumers never reach into the user feature and don't need to know that
 * entitlement is physically *stored* on the synced `users` row (D2 / ADR-0044).
 *
 * Reads are always local + instant (Room-backed); [reconcile] is the only thing that touches the
 * network, and only ever *refreshes* the cache.
 */
interface EntitlementRepository {

    /** The signed-in user's own entitlement; [Entitlement.NONE] before the row / first sync. */
    fun observeSelf(): Flow<Entitlement>

    /**
     * The partner's entitlement (trusted unconditionally — a device can't query the other Play
     * account, ADR-0044 §2), or null when unpaired / the partner row hasn't synced in yet.
     * This is the [Scope.SHARED] half of [EffectiveAccess].
     */
    fun observePartner(): Flow<Entitlement?>

    /**
     * Reconcile *own* entitlement from Play Billing onto the synced `users` row — the
     * cache-of-Play write (ADR-0044 §1). Called on every foreground / reconnect / WorkManager
     * full sync (as a [core.sync.FullSyncStep][com.iponlove.app.core.sync.FullSyncStep]) and,
     * later, directly after a purchase completes (S5). Best-effort and idempotent: a transient
     * Play failure leaves the last-known-good cache untouched, a `GRANT` comp is never overwritten
     * (G7), and an unchanged result writes nothing.
     */
    suspend fun reconcile()
}
