package com.iponlove.app.feature.settings.domain.usecase

import com.iponlove.app.feature.settings.domain.model.ThemePreferences
import com.iponlove.app.feature.settings.domain.repository.ThemeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveThemePreferencesUseCase @Inject constructor(
    private val repository: ThemeRepository,
) {
    operator fun invoke(): Flow<ThemePreferences> = repository.observe()
}
