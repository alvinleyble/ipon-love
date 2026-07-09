package com.iponlove.app.core.billing

import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryPurchasesAsync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Real [BillingGateway], a thin wrapper over the Play Billing Library (v9). Not unit-tested
 * directly — same precedent as `SupabaseUserRemoteSource`/`SupabaseAppConfigRemoteSource`, which
 * carry no logic of their own; the reconcile logic that consumes this (S4) is tested against a
 * fake [BillingGateway] instead.
 *
 * Nothing in the app injects [BillingGateway] yet (S3 is unwired, like S2), so Hilt never
 * constructs this class and no [BillingClient] connection is ever opened — this slice has no
 * on-device-observable behavior.
 */
@Singleton
class PlayBillingGateway @Inject constructor(
    @ApplicationContext context: Context,
) : BillingGateway {

    // No purchase flow is launched through this gateway (see BillingGateway's kdoc), but the SDK
    // still requires a listener at construction time; it is never meaningfully invoked here.
    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(PurchasesUpdatedListener { _, _ -> })
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
