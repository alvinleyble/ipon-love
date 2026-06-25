package com.iponlove.app.feature.settings.domain.usecase

import com.iponlove.app.feature.settings.domain.model.ThemePreferences
import com.iponlove.app.feature.settings.domain.repository.ThemeRepository
import javax.inject.Inject

class SaveThemePreferencesUseCase @Inject constructor(
    private val repository: ThemeRepository,
) {
    suspend operator fun invoke(prefs: ThemePreferences) = repository.save(prefs)
}
