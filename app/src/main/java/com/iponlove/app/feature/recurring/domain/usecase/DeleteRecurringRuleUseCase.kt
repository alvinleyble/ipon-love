package com.iponlove.app.feature.recurring.domain.usecase

import com.iponlove.app.feature.recurring.domain.repository.RecurringRuleRepository
import javax.inject.Inject

/**
 * Soft delete (ADR-0010) — the rule is tombstoned and stops generating. Transactions it
 * already materialized are kept (they're ordinary transactions now).
 */
class DeleteRecurringRuleUseCase @Inject constructor(
    private val repository: RecurringRuleRepository,
) {
    suspend operator fun invoke(id: String) = repository.deleteRule(id)
}
