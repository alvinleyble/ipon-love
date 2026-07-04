package com.iponlove.app.feature.recurring.domain.usecase

import com.iponlove.app.feature.recurring.domain.repository.RecurringRuleRepository
import javax.inject.Inject

/**
 * Skip the next scheduled occurrence of a rule by advancing [nextDate] one interval forward.
 * The skipped occurrence is never materialized (the cursor moved past it) and the change is
 * immediately visible — the rule's "Next:" date jumps to the occurrence after the skipped one.
 */
class SkipNextRecurringOccurrenceUseCase @Inject constructor(
    private val ruleRepository: RecurringRuleRepository,
) {
    suspend operator fun invoke(ruleId: String) {
        val rule = ruleRepository.activeRules().firstOrNull { it.id == ruleId } ?: return
        val advanced = RecurringScheduler.advance(rule.nextDate, rule.frequency, rule.interval)
        ruleRepository.upsertRule(rule.copy(nextDate = advanced))
    }
}
