package com.iponlove.app.core.billing

import android.app.Activity
import kotlinx.coroutines.flow.SharedFlow

/**
 * A single INAPP purchase owned by the signed-in Play account, decoupled from
 * `com.android.billingclient.api.Purchase` so callers never import the Play Billing SDK
 * directly — this indirection is what makes [BillingGateway] fakeable on the JVM path (§12 S3).
 * [productIds] mirrors `Purchase.getProducts()` (a purchase can in principle bundle more than
 * one product, though this app only ever sells [PREMIUM_PRODUCT_ID]).
 */
data class OwnedPurchase(
    val productIds: List<String>,
    val purchaseToken: String,
    val isAcknowledged: Boolean,
)

/**
 * The asynchronous outcome of a launched purchase flow (S5). Play reports it via
 * `PurchasesUpdatedListener` *after* [BillingGateway.launchPurchaseFlow] returns — so it can't be
 * the return value of that call. [Cancelled] is split out from [Failed] because a user backing
 * out of the Play sheet is the common, non-error case and must not surface as an error message.
 */
sealed interface PurchaseResult {
    data class Success(val purchase: OwnedPurchase) : PurchaseResult
    data object Cancelled : PurchaseResult
    data class Failed(val responseCode: Int, val message: String) : PurchaseResult
}

/** A Play Billing failure, carrying the raw `BillingClient.BillingResponseCode` (e.g.
 *  `SERVICE_DISCONNECTED`, `NETWORK_ERROR`) so a caller can tell a transient/retryable failure
 *  from a real one without depending on the SDK's constants directly. */
class BillingException(val responseCode: Int, message: String) : Exception(message)

/**
 * Play Billing wrapper behind an interface (paywall S3/S5 / ADR-0044). Covers the read path the
 * dormant reconcile loop (S4) and the paywall's "Restore purchases" action (S5) need, plus the
 * purchase-flow launch (S5) that S3 deferred because it needs an [Activity].
 */
interface BillingGateway {

    /**
     * All INAPP purchases currently owned by the signed-in Play account. Play ties purchases to
     * the Play account, not our Supabase login, so this single call *is* both the silent
     * background reconcile check (S4) and the user-facing "Restore purchases" action (S5) — Play
     * has no separate restore API for non-consumables; a new device signed into the same Play
     * account already sees past purchases here (§3 of the design doc).
     */
    suspend fun queryOwnedPurchases(): Result<List<OwnedPurchase>>

    /**
     * Acknowledges [purchaseToken]. Idempotent from Play's side is not guaranteed — a caller
     * must check [OwnedPurchase.isAcknowledged] before calling this (Play errors on a
     * double-acknowledge); this method is a mechanical wrapper, not the guard itself. An
     * unacknowledged non-consumable auto-refunds after ~3 days (Play policy), so the reconcile
     * loop (S4) must call this for every unacknowledged owned purchase it sees.
     */
    suspend fun acknowledge(purchaseToken: String): Result<Unit>

    /**
     * Every purchase-flow outcome the SDK reports (hot, Application-scoped). The paywall
     * ViewModel (S5) collects this while the screen is open and, on [PurchaseResult.Success],
     * calls the reconcile loop (S4) to persist entitlement onto the synced `users` row — which
     * also pushes it so the partner unlocks shared surfaces (D1/D2). A missed emission is not
     * lost: the next foreground full-sync reconciles the same purchase (S4).
     */
    val purchaseResults: SharedFlow<PurchaseResult>

    /**
     * Launches Play's purchase UI for [PREMIUM_PRODUCT_ID] over [activity]. The [Result] here only
     * reports whether the flow could be *launched* (product resolvable, UI shown) — the purchase
     * outcome itself arrives asynchronously on [purchaseResults]. While the paywall is dormant and
     * the Play Console product doesn't exist yet (pre-S11), this fails fast with `ITEM_UNAVAILABLE`.
     */
    suspend fun launchPurchaseFlow(activity: Activity): Result<Unit>

    companion object {
        /** The one-time ₱249 non-consumable product this app sells (§10.5, D7). */
        const val PREMIUM_PRODUCT_ID = "premium"
    }
}
