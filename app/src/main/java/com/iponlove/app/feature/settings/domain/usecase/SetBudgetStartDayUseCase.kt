package com.iponlove.app.feature.settings.domain.usecase

import com.iponlove.app.feature.settings.domain.repository.BudgetStartDayRepository
import javax.inject.Inject

class SetBudgetStartDayUseCase @Inject constructor(
    private val repository: BudgetStartDayRepository,
) {
    suspend operator fun invoke(day: Int) = repository.setStartDay(day)
}
