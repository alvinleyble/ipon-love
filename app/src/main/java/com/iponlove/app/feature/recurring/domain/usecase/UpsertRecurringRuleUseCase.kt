package com.iponlove.app.feature.recurring.domain.usecase

import com.iponlove.app.feature.recurring.domain.model.RecurringRule
import com.iponlove.app.feature.recurring.domain.repository.RecurringRuleRepository
import javax.inject.Inject

/** Create or edit a recurring rule; rejects invalid input ([RecurringValidator]). */
class UpsertRecurringRuleUseCase @Inject constructor(
    private val repository: RecurringRuleRepository,
) {
    suspend operator fun invoke(rule: RecurringRule) {
        val errors = RecurringValidator.validate(rule)
        require(errors.isEmpty()) { "Invalid recurring rule: $errors" }
        repository.upsertRule(rule)
    }
}
