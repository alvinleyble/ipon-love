package com.iponlove.app.feature.recurring.domain.usecase

import com.iponlove.app.feature.recurring.domain.repository.RecurringRuleRepository
import javax.inject.Inject

class PauseRecurringRuleUseCase @Inject constructor(
    private val ruleRepository: RecurringRuleRepository,
) {
    suspend operator fun invoke(ruleId: String) {
        val rule = ruleRepository.activeRules().firstOrNull { it.id == ruleId } ?: return
        ruleRepository.upsertRule(rule.copy(isPaused = true))
    }
}
