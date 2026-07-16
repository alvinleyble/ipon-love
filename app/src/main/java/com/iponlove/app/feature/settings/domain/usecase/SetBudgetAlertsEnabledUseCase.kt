package com.iponlove.app.feature.settings.domain.usecase

import com.iponlove.app.feature.settings.domain.repository.NotificationPreferencesRepository
import javax.inject.Inject

class SetBudgetAlertsEnabledUseCase @Inject constructor(
    private val repository: NotificationPreferencesRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setBudgetAlertsEnabled(enabled)
}
