package com.iponlove.app.feature.settings.domain.usecase

import com.iponlove.app.feature.settings.domain.repository.NotificationPreferencesRepository
import javax.inject.Inject

class SetBudgetLineMutedUseCase @Inject constructor(
    private val repository: NotificationPreferencesRepository,
) {
    suspend operator fun invoke(lineId: String, muted: Boolean) =
        repository.setBudgetLineMuted(lineId, muted)
}
