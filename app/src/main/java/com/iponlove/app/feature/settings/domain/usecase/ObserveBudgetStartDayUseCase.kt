package com.iponlove.app.feature.settings.domain.usecase

import com.iponlove.app.feature.settings.domain.repository.BudgetStartDayRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveBudgetStartDayUseCase @Inject constructor(
    private val repository: BudgetStartDayRepository,
) {
    operator fun invoke(): Flow<Int> = repository.observe()
}
