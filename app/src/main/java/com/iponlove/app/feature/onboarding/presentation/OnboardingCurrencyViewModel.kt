package com.iponlove.app.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import com.iponlove.app.feature.settings.domain.usecase.ObserveCurrencySymbolUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetCurrencySymbolUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the onboarding graph's Currency step (Item 27, split from Item 18). Reuses the same
 * `currency_prefs` DataStore + use cases as the Settings → Personalize picker — this screen is
 * purely a second entry point onto the app-wide [com.iponlove.app.core.ui.LocalCurrencySymbol]
 * preference, not a new storage/model concern.
 */
@HiltViewModel
class OnboardingCurrencyViewModel @Inject constructor(
    private val observeCurrencySymbol: ObserveCurrencySymbolUseCase,
    private val setCurrencySymbol: SetCurrencySymbolUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingCurrencyUiState())
    val state: StateFlow<OnboardingCurrencyUiState> = _state.asStateFlow()

    init {
        // Seeds the pre-selection from whatever's currently stored (defensively — a fresh install
        // is always PHP, but this keeps the screen honest if onboarding is ever re-entered).
        viewModelScope.launch {
            val current = observeCurrencySymbol().first()
            _state.update { it.copy(selected = current) }
        }
    }

    /** Instant, undrafted like the Settings picker (Item 18) — no Apply gate, so
     *  [com.iponlove.app.core.ui.LocalCurrencySymbol] re-collects and the rest of the app
     *  (including any onboarding screens rendered after this one) reflects the choice immediately. */
    fun selectSymbol(symbol: CurrencySymbol) {
        _state.update { it.copy(selected = symbol) }
        viewModelScope.launch { setCurrencySymbol(symbol) }
    }
}
