package com.iponlove.app.feature.budgets.domain.usecase

import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import javax.inject.Inject

/** Create or edit a budget; rejects a non-positive limit. */
class UpsertBudgetUseCase @Inject constructor(
    private val repository: BudgetRepository,
) {
    suspend operator fun invoke(budget: Budget) {
        require(budget.amount.signum() > 0) { "Budget amount must be greater than zero" }
        require(budget.yearMonth.isNotBlank()) { "Budget must target a month" }
        repository.upsertBudget(budget)
    }
}
