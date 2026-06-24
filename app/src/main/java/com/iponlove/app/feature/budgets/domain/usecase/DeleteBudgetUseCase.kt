package com.iponlove.app.feature.budgets.domain.usecase

import com.iponlove.app.feature.budgets.domain.repository.BudgetRepository
import javax.inject.Inject

/** Soft delete (ADR-0010) — the row is tombstoned and the delete syncs like any edit. */
class DeleteBudgetUseCase @Inject constructor(
    private val repository: BudgetRepository,
) {
    suspend operator fun invoke(id: String) = repository.deleteBudget(id)
}
