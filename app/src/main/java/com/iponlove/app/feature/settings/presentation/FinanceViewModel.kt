package com.iponlove.app.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import com.iponlove.app.feature.settings.domain.usecase.ObserveCurrencySymbolUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObservePrivacyModeUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveReceiptGalleryCopyEnabledUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetCurrencySymbolUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetPrivacyModeUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetReceiptGalleryCopyEnabledUseCase
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
 * Finance sub-screen VM (v1.6.5 Item 34) — the currency (Item 18) and privacy (Item 15) settings
 * lifted out of [PersonalizeViewModel] unchanged. Both are instant, undrafted write-throughs:
 * every entry point writes straight to its DataStore flow and every observer (the app-wide
 * LocalCurrencySymbol / LocalPrivacyMode) re-collects.
 */
@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val setPrivacyModeUseCase: SetPrivacyModeUseCase,
    private val setCurrencySymbolUseCase: SetCurrencySymbolUseCase,
    private val setReceiptGalleryCopyEnabledUseCase: SetReceiptGalleryCopyEnabledUseCase,
    observePrivacyMode: ObservePrivacyModeUseCase,
    observeCurrencySymbol: ObserveCurrencySymbolUseCase,
    observeReceiptGalleryCopyEnabled: ObserveReceiptGalleryCopyEnabledUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinanceUiState())
    val uiState: StateFlow<FinanceUiState> = _uiState

    init {
        combine(
            observeCurrencySymbol(),
            observePrivacyMode(),
            observeReceiptGalleryCopyEnabled(),
        ) { currencySymbol, privacyModeOn, galleryCopyOn ->
            FinanceUiState(
                currencySymbol = currencySymbol,
                privacyModeEnabled = privacyModeOn,
                receiptGalleryCopyEnabled = galleryCopyOn,
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

    /** Gallery copies of scanned receipts (v1.7.3 Item 2, ADR-0062 decision 7). Instant and
     *  undrafted like the rest of this screen; read at Save time by the transaction editor. */
    fun setReceiptGalleryCopy(enabled: Boolean) {
        viewModelScope.launch { setReceiptGalleryCopyEnabledUseCase(enabled) }
    }
}
