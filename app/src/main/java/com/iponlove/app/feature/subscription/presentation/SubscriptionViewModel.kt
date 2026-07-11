package com.iponlove.app.feature.subscription.presentation

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.billing.BillingGateway
import com.iponlove.app.core.billing.PurchaseResult
import com.iponlove.app.core.entitlement.EntitlementRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Drives the paywall (S5). Pure orchestration over two fakeable seams — [EntitlementRepository]
 * (read the cached column + reconcile) and [BillingGateway] (launch Play, observe the outcome) —
 * so the un-testable Play SDK never enters the JVM test path.
 *
 * The one hard rule this encodes: **after a purchase we don't trust the callback's payload, we
 * [EntitlementRepository.reconcile]** — that re-queries Play, acknowledges, and writes the synced
 * `users` column (which pushes, propagating the unlock to the partner, D1/D2). [observeSelf] then
 * flips the UI to "active" with no app restart.
 */
@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val entitlement: EntitlementRepository,
    private val billing: BillingGateway,
    private val analytics: Analytics,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState

    init {
        // Screen open == a paywall impression (the VM is scoped to this nav entry). The entry
        // surface arrives as a nav arg — "settings" from the Settings Premium row, or a gate's
        // cap key (the same string the gate logs to `upsell_tap`) — so the funnel can attribute
        // each impression to what drove it (Item 21). Defaults to "settings" if the route omits it.
        val source = savedStateHandle.get<String>(SOURCE_KEY) ?: DEFAULT_SOURCE
        analytics.log("paywall_impression", source = source)

        entitlement.observeSelf()
            .onEach { self ->
                _uiState.update { it.copy(loading = false, isPremium = self.isActive(Instant.now())) }
            }
            .launchIn(viewModelScope)

        billing.purchaseResults
            .onEach { handlePurchaseResult(it) }
            .launchIn(viewModelScope)
    }

    fun onBuy(activity: Activity) {
        viewModelScope.launch {
            _uiState.update { it.copy(purchaseInProgress = true, message = null) }
            analytics.log("purchase_started")
            val launched = billing.launchPurchaseFlow(activity)
            if (launched.isFailure) {
                // The result listener never fires if the flow couldn't even launch (e.g. the
                // product isn't in the Play Console yet — expected while dormant, pre-S11).
                _uiState.update {
                    it.copy(
                        purchaseInProgress = false,
                        message = "Premium isn't available yet. Please try again later.",
                    )
                }
            }
            // On a successful launch we keep purchaseInProgress until the listener reports back.
        }
    }

    fun onRestore() {
        viewModelScope.launch {
            _uiState.update { it.copy(restoreInProgress = true, message = null) }
            analytics.log("restore")
            entitlement.reconcile()
            // reconcile() writes the column before returning, so the cached value is now current.
            val active = entitlement.observeSelf().first().isActive(Instant.now())
            _uiState.update {
                it.copy(
                    restoreInProgress = false,
                    message = if (active) "Purchases restored." else "No previous purchase found.",
                )
            }
        }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(message = null) }
    }

    private fun handlePurchaseResult(result: PurchaseResult) {
        viewModelScope.launch {
            when (result) {
                is PurchaseResult.Success -> {
                    analytics.log("purchase_success")
                    entitlement.reconcile()
                    _uiState.update { it.copy(purchaseInProgress = false, message = "Welcome to Premium!") }
                }
                PurchaseResult.Cancelled -> {
                    analytics.log("purchase_cancelled")
                    _uiState.update { it.copy(purchaseInProgress = false) }
                }
                is PurchaseResult.Failed ->
                    _uiState.update {
                        it.copy(purchaseInProgress = false, message = "Purchase couldn't be completed.")
                    }
            }
        }
    }

    companion object {
        /** Nav-arg key for the entry surface that opened the paywall (read off [SavedStateHandle]). */
        const val SOURCE_KEY = "source"
        /** Fallback surface when the route carries no source (e.g. the Settings Premium row). */
        const val DEFAULT_SOURCE = "settings"
    }
}
