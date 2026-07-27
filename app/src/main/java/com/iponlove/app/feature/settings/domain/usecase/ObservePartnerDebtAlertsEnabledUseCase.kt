package com.iponlove.app.feature.settings.domain.usecase

import com.iponlove.app.feature.settings.domain.repository.NotificationPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePartnerDebtAlertsEnabledUseCase @Inject constructor(
    private val repository: NotificationPreferencesRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observePartnerDebtAlertsEnabled()
}
