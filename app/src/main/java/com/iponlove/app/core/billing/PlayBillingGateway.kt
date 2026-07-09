package com.iponlove.app.core.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Real [BillingGateway], a thin wrapper over the Play Billing Library (v9). The SDK-glue that
 * can't be exercised on the JVM (real [BillingClient], an [Activity]) lives here; the reconcile
 * *decision* logic (S4) and the paywall orchestration (S5) that consume this are tested against a
 * fake [BillingGateway] instead, so this class carries no branch worth its own unit test.
 *
 * Since S4 the reconcile loop injects this (it runs on every full sync), so the [BillingClient] is
 * constructed at startup — but a connection only actually opens on the first [queryOwnedPurchases]
 * / [launchPurchaseFlow] call. The purchase flow (S5) is the only path that opens Play's UI.
 */
@Singleton
class PlayBillingGateway @Inject constructor(
    @ApplicationContext context: Context,
) : BillingGateway {

    // replay = 0 (a returning collector must not re-fire a stale purchase), buffered so the
    // non-suspending listener can tryEmit without a subscriber racing to be ready; a missed
    // emission self-heals on the next foreground reconcile (S4), so DROP_OLDEST is safe.
    private val _purchaseResults = MutableSharedFlow<PurchaseResult>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val purchaseResults: SharedFlow<PurchaseResult> = _purchaseResults.asSharedFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener { result, purchases -> onPurchasesUpdated(result, purchases) }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override suspend fun queryOwnedPurchases(): Result<List<OwnedPurchase>> {
        val connected = ensureConnected()
        if (connected != null) return Result.failure(connected)

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = billingClient.queryPurchasesAsync(params)
        val code = result.billingResult.responseCode
        if (code != BillingClient.BillingResponseCode.OK) {
            return Result.failure(BillingException(code, result.billingResult.debugMessage))
        }
        return Result.success(result.purchasesList.map { it.toOwnedPurchase() })
    }

    override suspend fun acknowledge(purchaseToken: String): Result<Unit> {
        val connected = ensureConnected()
        if (connected != null) return Result.failure(connected)

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchaseToken)
            .build()
        val result = billingClient.acknowledgePurchase(params)
        return if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            Result.success(Unit)
        } else {
            Result.failure(BillingException(result.responseCode, result.debugMessage))
        }
    }

    override suspend fun launchPurchaseFlow(activity: Activity): Result<Unit> {
        val connected = ensureConnected()
        if (connected != null) return Result.failure(connected)

        // Play requires fresh ProductDetails to launch a flow (a purchase can't be started from a
        // bare product id). An empty list is the expected dormant/pre-S11 state — the product
        // simply isn't in the Play Console yet — so map it to ITEM_UNAVAILABLE, not a crash.
        val productParams = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(BillingGateway.PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        val detailsResult = billingClient.queryProductDetails(productParams)
        val detailsCode = detailsResult.billingResult.responseCode
        if (detailsCode != BillingClient.BillingResponseCode.OK) {
            return Result.failure(BillingException(detailsCode, detailsResult.billingResult.debugMessage))
        }
        val product = detailsResult.productDetailsList?.firstOrNull()
            ?: return Result.failure(
                BillingException(
                    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE,
                    "Premium product not found in Play Console",
                ),
            )

        val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(product)
            .apply {
                // v9 one-time products may carry per-offer tokens; set one if present (a plain
                // single-price product has none, and the flow launches without it).
                product.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
                    ?.let { setOfferToken(it) }
            }
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val launch = billingClient.launchBillingFlow(activity, flowParams)
        return if (launch.responseCode == BillingClient.BillingResponseCode.OK) {
            Result.success(Unit)
        } else {
            Result.failure(BillingException(launch.responseCode, launch.debugMessage))
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val event = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val premium = purchases.orEmpty()
                    .firstOrNull { BillingGateway.PREMIUM_PRODUCT_ID in it.products }
                if (premium != null) {
                    PurchaseResult.Success(premium.toOwnedPurchase())
                } else {
                    // OK but nothing we sell — treat as a benign no-op failure the ViewModel drops.
                    PurchaseResult.Failed(result.responseCode, "No premium purchase in update")
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseResult.Cancelled
            else -> PurchaseResult.Failed(result.responseCode, result.debugMessage)
        }
        _purchaseResults.tryEmit(event)
    }

    /** Returns null once connected, or the [BillingException] to fail the caller with. */
    private suspend fun ensureConnected(): BillingException? {
        if (billingClient.isReady) return null
        return suspendCancellableCoroutine { cont ->
            billingClient.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (!cont.isActive) return
                    val code = billingResult.responseCode
                    cont.resume(
                        if (code == BillingClient.BillingResponseCode.OK) null
                        else BillingException(code, billingResult.debugMessage)
                    )
                }

                override fun onBillingServiceDisconnected() {
                    // enableAutoServiceReconnection() (v9) retries on the SDK's own schedule;
                    // the next call's ensureConnected() re-checks isReady and reconnects if
                    // still needed. Nothing to do here.
                }
            })
        }
    }
}

private fun Purchase.toOwnedPurchase() = OwnedPurchase(
    productIds = products,
    purchaseToken = purchaseToken,
    isAcknowledged = isAcknowledged,
)
