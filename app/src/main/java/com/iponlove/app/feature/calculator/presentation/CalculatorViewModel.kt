package com.iponlove.app.feature.calculator.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.entitlement.PremiumGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Soft-gate state for the Calculator module (S9 — `Feature.CALCULATOR`, individual scope). The
 * calculator itself is stateless UI over the pure [CalculatorEngine][com.iponlove.app.feature.calculator.domain.CalculatorEngine];
 * this ViewModel exists only to observe the lock so the module tile shows a Premium wall under
 * enforcement. [locked] is false while dormant (enforcement OFF), so nothing changes pre-flip.
 */
@HiltViewModel
class CalculatorViewModel @Inject constructor(
    premiumGate: PremiumGate,
    private val analytics: Analytics,
) : ViewModel() {

    val locked: StateFlow<Boolean> = premiumGate.observeLocked().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = false,
    )

    /** A tap on the locked module's "Get Premium" — logs the §10.10 funnel touchpoint. */
    fun onUpsellTap() {
        analytics.log("upsell_tap", source = "calculator")
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
