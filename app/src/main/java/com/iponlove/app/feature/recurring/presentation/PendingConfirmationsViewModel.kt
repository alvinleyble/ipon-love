package com.iponlove.app.feature.recurring.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.recurring.domain.usecase.ConfirmOccurrenceUseCase
import com.iponlove.app.feature.recurring.domain.usecase.ObservePendingConfirmationsUseCase
import com.iponlove.app.feature.recurring.domain.usecase.SkipPendingOccurrenceUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetPrivacyModeUseCase
import com.iponlove.app.feature.widget.presentation.Widgets
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

/**
 * Drives the Records "To confirm" card (Item 37) — confirm-on-arrival's free core. Hosted
 * alongside [TransactionsViewModel] on the Records screen, kept as its own thin ViewModel so
 * the recurring-confirmation concern stays out of the transactions list state.
 *
 * Confirm materializes a single occurrence (with an optional per-occurrence amount tweak) and
 * repaints the balance widgets — the same post-write refresh the transactions list does. Skip
 * just advances that rule's cursor past the occurrence. "Confirm all" / "Skip all" walk the
 * current list oldest-first (the order the derived list is already sorted in, which keeps the
 * cursor advancing correctly for skip).
 */
@HiltViewModel
class PendingConfirmationsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    observePendingConfirmations: ObservePendingConfirmationsUseCase,
    private val confirmOccurrence: ConfirmOccurrenceUseCase,
    private val skipOccurrence: SkipPendingOccurrenceUseCase,
    private val setPrivacyMode: SetPrivacyModeUseCase,
) : ViewModel() {

    private val collapsed = MutableStateFlow(false)

    val uiState: StateFlow<PendingConfirmationsUiState> =
        combine(observePendingConfirmations(), collapsed) { items, collapsed ->
            PendingConfirmationsUiState(items = items, collapsed = collapsed)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = PendingConfirmationsUiState(),
        )

    /** Record this occurrence. [amountOverride] null = use the rule's template amount. */
    fun confirm(ruleId: String, date: LocalDate, amountOverride: BigDecimal?) {
        viewModelScope.launch {
            confirmOccurrence(ruleId, date, amountOverride)
            Widgets.updateAll(context)
        }
    }

    fun skip(ruleId: String, date: LocalDate) {
        viewModelScope.launch { skipOccurrence(ruleId, date) }
    }

    fun confirmAll() {
        val snapshot = uiState.value.items
        viewModelScope.launch {
            snapshot.forEach { confirmOccurrence(it.ruleId, it.date, null) }
            Widgets.updateAll(context)
        }
    }

    fun skipAll() {
        val snapshot = uiState.value.items
        viewModelScope.launch {
            snapshot.forEach { skipOccurrence(it.ruleId, it.date) }
        }
    }

    /** The card's eye flips the same global Privacy mode (Item 15) as the Settings switch and the
     *  Accounts/Analysis headers — writing through to the one DataStore flag, not a local reveal. */
    fun togglePrivacyMode(enabled: Boolean) {
        viewModelScope.launch { setPrivacyMode(enabled) }
    }

    fun toggleCollapsed() {
        collapsed.value = !collapsed.value
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
