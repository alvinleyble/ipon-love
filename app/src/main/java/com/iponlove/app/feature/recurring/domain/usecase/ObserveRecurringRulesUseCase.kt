package com.iponlove.app.feature.recurring.domain.usecase

import com.iponlove.app.feature.recurring.domain.model.RecurringRule
import com.iponlove.app.feature.recurring.domain.repository.RecurringRuleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveRecurringRulesUseCase @Inject constructor(
    private val repository: RecurringRuleRepository,
) {
    operator fun invoke(): Flow<List<RecurringRule>> = repository.observeRules()
}
