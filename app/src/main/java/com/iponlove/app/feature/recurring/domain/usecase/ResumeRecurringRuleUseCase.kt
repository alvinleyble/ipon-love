package com.iponlove.app.feature.recurring.domain.usecase

import com.iponlove.app.feature.recurring.domain.repository.RecurringRuleRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Resume a paused rule. Advances nextDate to the next occurrence from today
 * (never backfills missed occurrences per ADR-0035).
 */
class ResumeRecurringRuleUseCase @Inject constructor(
    private val ruleRepository: RecurringRuleRepository,
) {
    suspend operator fun invoke(ruleId: String) {
        val rule = ruleRepository.activeRules().firstOrNull { it.id == ruleId } ?: return
        if (!rule.isPaused) return

        val today = LocalDate.now()
        var nextDate = rule.nextDate

        // Advance nextDate to the first occurrence from today onwards.
        while (!nextDate.isAfter(today)) {
            nextDate = RecurringScheduler.advance(nextDate, rule.frequency, rule.interval)
        }

        ruleRepository.upsertRule(rule.copy(isPaused = false, nextDate = nextDate))
    }
}
