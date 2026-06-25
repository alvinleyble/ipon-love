package com.iponlove.app.feature.settings.domain.repository

import com.iponlove.app.feature.settings.domain.model.ThemePreferences
import kotlinx.coroutines.flow.Flow

interface ThemeRepository {
    fun observe(): Flow<ThemePreferences>
    suspend fun save(prefs: ThemePreferences)
}
