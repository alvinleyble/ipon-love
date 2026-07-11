package com.iponlove.app.feature.settings.domain.usecase

import com.iponlove.app.feature.settings.domain.repository.PrivacyModeRepository
import javax.inject.Inject

class SetPrivacyModeUseCase @Inject constructor(
    private val repository: PrivacyModeRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setEnabled(enabled)
}
