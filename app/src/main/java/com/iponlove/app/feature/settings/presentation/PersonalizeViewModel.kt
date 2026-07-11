package com.iponlove.app.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.config.AppConfigRepository
import com.iponlove.app.core.entitlement.EntitlementRepository
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.feature.settings.data.ThemeDraftRepository
import com.iponlove.app.feature.settings.domain.model.ThemePalette
import com.iponlove.app.feature.settings.domain.model.ThemePreferences
import com.iponlove.app.feature.settings.domain.usecase.ObserveThemePreferencesUseCase
import com.iponlove.app.feature.settings.domain.usecase.SaveThemePreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class PersonalizeViewModel @Inject constructor(
    private val observeTheme: ObserveThemePreferencesUseCase,
    private val saveTheme: SaveThemePreferencesUseCase,
    private val themeDraft: ThemeDraftRepository,
    private val analytics: Analytics,
    appConfig: AppConfigRepository,
    entitlement: EntitlementRepository,
    premiumGate: PremiumGate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonalizeUiState())
    val uiState: StateFlow<PersonalizeUiState> = _uiState

    init {
        viewModelScope.launch {
            val saved = observeTheme().first()
            _uiState.update {
                it.copy(draftPalette = saved.palette, draftIsDark = saved.isDark)
            }
        }
        // The Premium entry is hidden while the paywall is dormant (enforcement OFF) and only
        // appears once enforcement flips ON (S5 / Item 12 / §10.7). Its label follows the user's
        // own cached entitlement, live. `paletteLocked` (S9) rides the same reactive source so the
        // grid re-locks/unlocks (and G8 re-derives the effective palette) without a restart.
        combine(
            appConfig.observe(),
            entitlement.observeSelf(),
            premiumGate.observeLocked(),
        ) { config, self, paletteLocked ->
            Triple(config.enforcementEnabled, self.isActive(Instant.now()), paletteLocked)
        }.onEach { (enforcementOn, isPremium, paletteLocked) ->
            _uiState.update {
                it.copy(
                    showPremiumEntry = enforcementOn,
                    isPremium = isPremium,
                    paletteLocked = paletteLocked,
                )
            }
        }.launchIn(viewModelScope)
    }

    fun selectPalette(palette: ThemePalette) {
        // Defensive: a locked Premium palette never becomes the draft (the screen routes it to the
        // paywall instead). Keeps a locked user's chosen palette in DataStore untouched so Apply
        // can't overwrite it — the non-destructive half of G8.
        if (_uiState.value.paletteLocked && !palette.isFree) return
        _uiState.update { it.copy(draftPalette = palette, saved = false) }
        themeDraft.set(ThemePreferences(palette = palette, isDark = _uiState.value.draftIsDark))
    }

    /** A tap on a locked (Premium) swatch — logs the §10.10 funnel touchpoint before the screen
     *  routes to the paywall. */
    /** Locked-palette tap: logs the §10.10 touchpoint and returns the paywall entry source. */
    fun onLockedPaletteTap(): String {
        val source = "palette"
        analytics.log("upsell_tap", source = source)
        return source
    }

    fun toggleDarkMode(isDark: Boolean) {
        _uiState.update { it.copy(draftIsDark = isDark, saved = false) }
        themeDraft.set(ThemePreferences(palette = _uiState.value.draftPalette, isDark = isDark))
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            saveTheme(ThemePreferences(palette = state.draftPalette, isDark = state.draftIsDark))
            _uiState.update { it.copy(saved = true) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        themeDraft.clear()
    }
}
