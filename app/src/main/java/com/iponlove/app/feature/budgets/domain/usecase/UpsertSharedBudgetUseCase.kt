package com.iponlove.app.feature.budgets.domain.usecase

import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import javax.inject.Inject

/**
 * Create or edit the couple's joint budget under [coupleId]; rejects a non-positive limit.
 * The V1 shared budget is overall-only (no category) — the combined month's spending counts.
 */
class UpsertSharedBudgetUseCase @Inject constructor(
    private val repository: BudgetRepository,
) {
    suspend operator fun invoke(budget: Budget, coupleId: String) {
        require(budget.amount.signum() > 0) { "Budget amount must be greater than zero" }
        require(budget.yearMonth.isNotBlank()) { "Budget must target a month" }
        repository.upsertSharedBudget(budget, coupleId)
    }
}
