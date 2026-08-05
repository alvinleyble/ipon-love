package com.iponlove.app.feature.settings.domain.usecase

import com.iponlove.app.feature.settings.domain.repository.ReceiptPreferencesRepository
import javax.inject.Inject

class SetReceiptGalleryCopyEnabledUseCase @Inject constructor(
    private val repository: ReceiptPreferencesRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setGalleryCopyEnabled(enabled)
}
