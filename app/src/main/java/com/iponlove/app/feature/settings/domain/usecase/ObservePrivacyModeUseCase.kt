package com.iponlove.app.feature.settings.domain.usecase

import com.iponlove.app.feature.settings.domain.repository.PrivacyModeRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePrivacyModeUseCase @Inject constructor(
    private val repository: PrivacyModeRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observe()
}
