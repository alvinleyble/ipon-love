package com.iponlove.app.feature.recurring.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import com.iponlove.app.feature.recurring.domain.usecase.ObserveUpcomingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Drives the Records "Coming up" preview (Item 37, Slice 2) — the premium forecast layer. Hosted
 * alongside [TransactionsViewModel] on the Records screen (like [PendingConfirmationsViewModel]),
 * kept thin so the forecast concern stays out of the transactions list state.
 *
 * Folds the derived upcoming window (next [COMING_UP_WINDOW_DAYS] days, capped to
 * [PREVIEW_LIMIT] rows for a glance) with the `RECURRING_FORECAST` lock. The lock is the same
 * `observeLocked(INDIVIDUAL)` seam every S9/S10 gate uses — always false while dormant, so the
 * full preview shows pre-flip; once enforcement is ON without access, the card degrades to a
 * count-only upsell teaser and a tap routes to the paywall.
 */
@HiltViewModel
class ComingUpViewModel @Inject constructor(
    observeUpcoming: ObserveUpcomingUseCase,
    premiumGate: PremiumGate,
    private val analytics: Analytics,
) : ViewModel() {

    val uiState: StateFlow<ComingUpUiState> =
        combine(
            observeUpcoming(windowEnd = { today -> today.plusDays(COMING_UP_WINDOW_DAYS) }),
            premiumGate.observeLocked(Scope.INDIVIDUAL),
        ) { upcoming, locked ->
            ComingUpUiState(items = upcoming.take(PREVIEW_LIMIT), locked = locked)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = ComingUpUiState(),
        )

    /** A tap on the locked teaser — logs the §10.10 funnel touchpoint before the screen routes to
     *  the paywall. Returns the paywall entry source (also the `RECURRING_FORECAST` gate key). */
    fun onUpsell(): String {
        val source = "recurring_forecast"
        analytics.log("upsell_tap", source = source)
        return source
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /** How far ahead the preview looks. Short by design — a glance, not a full schedule. */
        const val COMING_UP_WINDOW_DAYS = 14L

        /** Cap the preview rows so a busy schedule doesn't push the ledger off-screen. */
        const val PREVIEW_LIMIT = 5
    }
}
