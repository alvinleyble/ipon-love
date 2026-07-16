package com.iponlove.app.feature.recurring.domain.usecase

import com.iponlove.app.feature.recurring.domain.repository.RecurringRuleRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Dismisses a single pending occurrence of a confirm-on-arrival rule (Item 37) without
 * recording anything — the "not this time" action on the "To confirm" card.
 *
 * It advances the rule's cursor to the occurrence **after** [date] (date-aware, so it works
 * even when the parked cursor lags behind the floor window). Since the derived pending list
 * starts at the cursor, everything on or before [date] leaves the list. Occurrences are
 * presented oldest-first and skipped oldest-first (the per-row Skip and "Skip all" both walk
 * the list in order), so this only ever moves the cursor forward, never past an occurrence the
 * user still needs to see. Guarded against regressing the cursor.
 */
class SkipPendingOccurrenceUseCase @Inject constructor(
    private val ruleRepository: RecurringRuleRepository,
) {
    suspend operator fun invoke(ruleId: String, date: LocalDate) {
        val rule = ruleRepository.getRule(ruleId) ?: return
        val advanced = RecurringScheduler.advance(date, rule.frequency, rule.interval)
        if (advanced.isAfter(rule.nextDate)) {
            ruleRepository.upsertRule(rule.copy(nextDate = advanced))
        }
    }
}
