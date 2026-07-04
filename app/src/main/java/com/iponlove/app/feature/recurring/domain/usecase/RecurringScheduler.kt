package com.iponlove.app.feature.recurring.domain.usecase

import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.recurring.domain.model.RecurringRule
import java.time.LocalDate

/**
 * Pure recurrence date math — the high-bug-risk logic, unit-tested.
 *
 * Stepping rule: each occurrence is computed from the *previous* occurrence via
 * `plusDays/Weeks/Months(interval)`. `plusMonths` clamps month-ends (Jan 31 + 1mo →
 * Feb 28), so a rule pinned to the 29th–31st can drift to month-end and stay there; days
 * 1–28 are stable. This is the documented V1 behaviour.
 */
object RecurringScheduler {

    /**
     * Runaway guard: at most this many occurrences are materialized in a single pass. A
     * rule far behind (e.g. a daily rule untouched for years) catches up in chunks across
     * passes rather than generating an unbounded batch at once.
     */
    const val MAX_OCCURRENCES_PER_PASS = 1_000

    /** Occurrences due on/before [asOf] plus the rule's advanced next date. */
    data class Run(val occurrences: List<LocalDate>, val nextDate: LocalDate)

    /**
     * Compute every occurrence due as of [asOf] (inclusive) starting from [RecurringRule.nextDate],
     * not past [RecurringRule.endDate], capped at [MAX_OCCURRENCES_PER_PASS]. [Run.nextDate]
     * is the first not-yet-materialized date — what the rule's cursor advances to.
     */
    fun run(rule: RecurringRule, asOf: LocalDate): Run {
        val occurrences = ArrayList<LocalDate>()
        var date = rule.nextDate
        while (!date.isAfter(asOf) &&
            (rule.endDate == null || !date.isAfter(rule.endDate)) &&
            occurrences.size < MAX_OCCURRENCES_PER_PASS
        ) {
            occurrences += date
            date = advance(date, rule.frequency, rule.interval)
        }
        return Run(occurrences, date)
    }

    /**
     * All dates in [from]..[to] (inclusive) on which [rule] fires, projected forward from
     * [RecurringRule.nextDate]. Dates before [nextDate] are already-materialized occurrences
     * and are not recoverable here — only present/future dates are returned.
     * Pure read — does not advance the rule's cursor.
     */
    fun occurrencesBetween(rule: RecurringRule, from: LocalDate, to: LocalDate): List<LocalDate> {
        require(!from.isAfter(to)) { "from must be <= to" }
        if (rule.nextDate.isAfter(to)) return emptyList()
        val results = mutableListOf<LocalDate>()
        var date = rule.nextDate
        // Skip dates before the window without collecting them.
        while (date.isBefore(from)) {
            date = advance(date, rule.frequency, rule.interval)
            if (date.isAfter(to)) return emptyList()
        }
        while (!date.isAfter(to) && (rule.endDate == null || !date.isAfter(rule.endDate))) {
            results += date
            date = advance(date, rule.frequency, rule.interval)
        }
        return results
    }

    internal fun advance(date: LocalDate, frequency: RecurringFrequency, interval: Int): LocalDate {
        val step = interval.toLong().coerceAtLeast(1L)
        return when (frequency) {
            RecurringFrequency.DAILY -> date.plusDays(step)
            RecurringFrequency.WEEKLY -> date.plusWeeks(step)
            RecurringFrequency.MONTHLY -> date.plusMonths(step)
            RecurringFrequency.YEARLY -> date.plusYears(step)
        }
    }
}
