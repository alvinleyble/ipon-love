package com.iponlove.app.core.entitlement

import com.iponlove.app.core.billing.BillingGateway
import com.iponlove.app.feature.user.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reconciles Play Billing → the synced `users` entitlement columns, and exposes the cached
 * entitlement for reads (ADR-0044). All the *policy* the ADR exists to protect lives in
 * [reconcile]; the row read/write mechanics (stamping, `pending_sync`, `requestPush`) stay in
 * [UserRepository], which owns the `users` row.
 *
 * The `now` provider is injectable only so tests can pin `entitlement_checked_at`; under one-time
 * billing (D7) nothing in the reconcile *decision* depends on the clock — `premium_until` is
 * always null and the state collapses to OWNED/NOT_OWNED.
 */
@Singleton
class EntitlementRepositoryImpl @Inject constructor(
    private val userRepository: UserRepository,
    private val billing: BillingGateway,
) : EntitlementRepository {

    override fun observeSelf(): Flow<Entitlement> = userRepository.observeSelfEntitlement()

    override fun observePartner(): Flow<Entitlement?> = userRepository.observePartnerEntitlement()

    override suspend fun reconcile() {
        val current = userRepository.getSelfEntitlement() ?: return // row not created yet — no-op

        // G7 / ADR-0044 §4: a beta comp (GRANT) is written server-side and must never be
        // overwritten by *this* device's Play state. Skip before even querying Play, so a
        // NOT_OWNED result can't wipe the comp. This is the single case where the device does
        // not defer to Play — and the flip-flop trap the ADR exists to prevent (§10.7 bug #1).
        if (current.source == EntitlementSource.GRANT) return

        // Advisory + offline-tolerant (ADR-0044 §5, §10.4): a transient Play failure
        // (SERVICE_DISCONNECTED, NETWORK_ERROR) leaves the last-known-good column untouched —
        // a subscriber is never re-locked just because a query flaked or the device is offline.
        val purchases = billing.queryOwnedPurchases().getOrElse { return }
        val premiumPurchases = purchases.filter { BillingGateway.PREMIUM_PRODUCT_ID in it.productIds }

        // A non-consumable left unacknowledged auto-refunds after ~3 days (Play policy), so
        // acknowledge every owned-but-unacked purchase to make the entitlement durable.
        // Best-effort: a failure here just retries on the next foreground, well inside that window.
        premiumPurchases.filterNot { it.isAcknowledged }
            .forEach { billing.acknowledge(it.purchaseToken) }

        val owned = premiumPurchases.isNotEmpty()
        val newSource = if (owned) EntitlementSource.PLAY else EntitlementSource.NONE

        // Idempotent (the flip-flop trap again): only write when the Play-derived state actually
        // differs, so a steady-state foreground never dirties the users row — which would re-push
        // it and make the partner re-pull it every single session. entitlement_checked_at
        // therefore advances only on a real change; that is the deliberate cost of not churning
        // the sync fabric every foreground (its value is diagnostic only, never read by a gate).
        if (current.isPremium == owned && current.source == newSource) return

        userRepository.writeSelfEntitlement(
            entitlement = Entitlement(
                isPremium = owned,
                premiumUntil = null, // one-time (D7) never expires; nullable path kept for a re-pivot
                source = newSource,
            ),
            checkedAt = Instant.now(),
        )
    }
}
