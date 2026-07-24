package com.iponlove.app.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.feature.settings.data.ThemeDraftRepository
import com.iponlove.app.feature.settings.domain.model.ThemeMode
import com.iponlove.app.feature.settings.domain.model.ThemePalette
import com.iponlove.app.feature.settings.domain.model.ThemePreferences
import com.iponlove.app.feature.settings.domain.usecase.ObserveThemePreferencesUseCase
import com.iponlove.app.feature.settings.domain.usecase.SaveThemePreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Appearance sub-screen VM (v1.6.5 Item 34) — the palette + dark-mode live preview lifted out of
 * [PersonalizeViewModel] unchanged. ADR-0014: `draft*` previews on tap (mirrored into
 * [ThemeDraftRepository] for the app-wide live preview) and only persists to DataStore on [save].
 * The G8 palette lock (S9) rides [PremiumGate.observeLocked] so the grid re-locks/unlocks live.
 */
@HiltViewModel
class AppearanceViewModel @Inject constructor(
    private val observeTheme: ObserveThemePreferencesUseCase,
    private val saveTheme: SaveThemePreferencesUseCase,
    private val themeDraft: ThemeDraftRepository,
    private val analytics: Analytics,
    premiumGate: PremiumGate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppearanceUiState())
    val uiState: StateFlow<AppearanceUiState> = _uiState

    init {
        viewModelScope.launch {
            val saved = observeTheme().first()
            _uiState.update { it.copy(draftPalette = saved.palette, draftMode = saved.mode) }
        }
        premiumGate.observeLocked()
            .onEach { locked -> _uiState.update { it.copy(paletteLocked = locked) } }
            .launchIn(viewModelScope)
    }

    fun selectPalette(palette: ThemePalette) {
        // Defensive: a locked Premium palette never becomes the draft (the screen routes it to the
        // paywall instead). Keeps a locked user's chosen palette in DataStore untouched so Apply
        // can't overwrite it — the non-destructive half of G8.
        if (_uiState.value.paletteLocked && !palette.isFree) return
        _uiState.update { it.copy(draftPalette = palette, saved = false) }
        themeDraft.set(ThemePreferences(palette = palette, mode = _uiState.value.draftMode))
    }

    /** Locked-palette tap: logs the §10.10 touchpoint and returns the paywall entry source. */
    fun onLockedPaletteTap(): String {
        val source = "palette"
        analytics.log("upsell_tap", source = source)
        return source
    }

    fun setThemeMode(mode: ThemeMode) {
        _uiState.update { it.copy(draftMode = mode, saved = false) }
        themeDraft.set(ThemePreferences(palette = _uiState.value.draftPalette, mode = mode))
    }

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            saveTheme(ThemePreferences(palette = state.draftPalette, mode = state.draftMode))
            _uiState.update { it.copy(saved = true) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        themeDraft.clear()
    }
}
