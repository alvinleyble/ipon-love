package com.iponlove.app.feature.settings.data

import com.iponlove.app.feature.settings.domain.model.ThemePreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory draft for the live theme preview in [PersonalizeScreen]. Never persisted.
 * [PersonalizeViewModel] writes here on every palette/dark tap; clears on ViewModel disposal
 * (user left the screen). [MainActivity] merges draft + DataStore so IponTheme (which wraps
 * the NavBar) reacts live — not just composables inside PersonalizeScreen's local subtree.
 */
@Singleton
class ThemeDraftRepository @Inject constructor() {
    private val _draft = MutableStateFlow<ThemePreferences?>(null)
    val draft: StateFlow<ThemePreferences?> = _draft

    fun set(prefs: ThemePreferences) { _draft.value = prefs }
    fun clear() { _draft.value = null }
}
