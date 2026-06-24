package com.iponlove.app.feature.budgets.domain.usecase

import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveBudgetsUseCase @Inject constructor(
    private val repository: BudgetRepository,
) {
    operator fun invoke(): Flow<List<Budget>> = repository.observeBudgets()
}
