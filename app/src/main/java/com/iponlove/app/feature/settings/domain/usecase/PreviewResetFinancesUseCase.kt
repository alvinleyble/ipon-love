package com.iponlove.app.feature.settings.domain.usecase

import com.iponlove.app.feature.settings.domain.model.ResetFinancesCounts
import com.iponlove.app.feature.settings.domain.repository.ResetFinancesRepository
import javax.inject.Inject

class PreviewResetFinancesUseCase @Inject constructor(
    private val repository: ResetFinancesRepository,
) {
    suspend operator fun invoke(): ResetFinancesCounts = repository.previewCounts()
}
