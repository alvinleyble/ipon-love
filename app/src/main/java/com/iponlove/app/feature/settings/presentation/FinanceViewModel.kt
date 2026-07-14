package com.iponlove.app.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import com.iponlove.app.feature.settings.domain.usecase.ObserveBudgetStartDayUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveCurrencySymbolUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObservePrivacyModeUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetBudgetStartDayUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetCurrencySymbolUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetPrivacyModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Finance sub-screen VM (v1.6.5 Item 34) — the currency (Item 18), privacy (Item 15), and
 * budget-cycle (Item 10b) settings lifted out of [PersonalizeViewModel] unchanged. All three are
 * instant, undrafted write-throughs: every entry point writes straight to its DataStore flow and
 * every observer (the app-wide LocalCurrencySymbol / LocalPrivacyMode / budget windows) re-collects.
 */
@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val setPrivacyModeUseCase: SetPrivacyModeUseCase,
    private val setCurrencySymbolUseCase: SetCurrencySymbolUseCase,
    private val setBudgetStartDayUseCase: SetBudgetStartDayUseCase,
    observePrivacyMode: ObservePrivacyModeUseCase,
    observeCurrencySymbol: ObserveCurrencySymbolUseCase,
    observeBudgetStartDay: ObserveBudgetStartDayUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinanceUiState())
    val uiState: StateFlow<FinanceUiState> = _uiState

    init {
        combine(
            observeCurrencySymbol(),
            observePrivacyMode(),
            observeBudgetStartDay(),
        ) { currencySymbol, privacyModeOn, budgetStartDay ->
            FinanceUiState(
                currencySymbol = currencySymbol,
                privacyModeEnabled = privacyModeOn,
                budgetStartDay = budgetStartDay,
            )
        }.onEach { snapshot -> _uiState.update { snapshot } }
            .launchIn(viewModelScope)
    }

    /** Instant, undrafted (Item 15): the switch writes straight through; every masking observer
     *  (this VM, the Net-asset eye icons) re-collects from the same DataStore flow. */
    fun setPrivacyMode(enabled: Boolean) {
        viewModelScope.launch { setPrivacyModeUseCase(enabled) }
    }

    /** Instant, undrafted (Item 18) — a cosmetic glyph swap; the app-wide LocalCurrencySymbol
     *  re-collects from the same DataStore flow. */
    fun setCurrencySymbol(symbol: CurrencySymbol) {
        viewModelScope.launch { setCurrencySymbolUseCase(symbol) }
    }

    /** Instant, undrafted (ADR-0046) — the repo clamps to 1..31. Personal budgets re-window
     *  retroactively; spent/rollover are derived, so nothing is rewritten. */
    fun setBudgetStartDay(day: Int) {
        viewModelScope.launch { setBudgetStartDayUseCase(day) }
    }
}
