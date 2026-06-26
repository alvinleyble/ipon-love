package com.iponlove.app.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.settings.data.ThemeDraftRepository
import com.iponlove.app.feature.settings.domain.model.ThemePalette
import com.iponlove.app.feature.settings.domain.model.ThemePreferences
import com.iponlove.app.feature.settings.domain.usecase.ObserveThemePreferencesUseCase
import com.iponlove.app.feature.settings.domain.usecase.SaveThemePreferencesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonalizeViewModel @Inject constructor(
    private val observeTheme: ObserveThemePreferencesUseCase,
    private val saveTheme: SaveThemePreferencesUseCase,
    private val themeDraft: ThemeDraftRepository,
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
    }

    fun selectPalette(palette: ThemePalette) {
        _uiState.update { it.copy(draftPalette = palette, saved = false) }
        themeDraft.set(ThemePreferences(palette = palette, isDark = _uiState.value.draftIsDark))
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
