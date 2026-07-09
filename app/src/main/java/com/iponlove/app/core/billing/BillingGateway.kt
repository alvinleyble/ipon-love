package com.iponlove.app.core.billing

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

/** A Play Billing failure, carrying the raw `BillingClient.BillingResponseCode` (e.g.
 *  `SERVICE_DISCONNECTED`, `NETWORK_ERROR`) so a caller can tell a transient/retryable failure
 *  from a real one without depending on the SDK's constants directly. */
class BillingException(val responseCode: Int, message: String) : Exception(message)

/**
 * Play Billing wrapper behind an interface (paywall S3 / ADR-0044 suggested build). Scoped to
 * the two read-path operations the dormant entitlement reconcile loop (S4) and the paywall's
 * "Restore purchases" action (S5) need — launching the purchase flow itself is S5's job, tied to
 * that screen's Activity and lifecycle, so it is deliberately not part of this wrapper.
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

    companion object {
        /** The one-time ₱249 non-consumable product this app sells (§10.5, D7). */
        const val PREMIUM_PRODUCT_ID = "premium"
    }
}
