package com.iponlove.app.feature.settings.domain.usecase

import com.iponlove.app.feature.settings.domain.repository.ReceiptPreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveReceiptGalleryCopyEnabledUseCase @Inject constructor(
    private val repository: ReceiptPreferencesRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeGalleryCopyEnabled()
}
