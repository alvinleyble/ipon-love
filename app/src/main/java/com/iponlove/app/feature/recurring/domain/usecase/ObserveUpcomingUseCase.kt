package com.iponlove.app.feature.recurring.domain.usecase

import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.recurring.domain.model.UpcomingOccurrence
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * The forward view of scheduled money — future occurrences of recurring rules in
 * `(today, windowEnd]` (Item 37, Slice 2, the premium forecast layer). Nothing is stored: it
 * derives them live from each rule's schedule, so it never touches balance/budget/Analysis math.
 *
 * Unlike [ObservePendingConfirmationsUseCase] this:
 *  - looks **forward** (dates strictly after today), so it never overlaps the ledger (auto-post
 *    rules materialize on/before today) or the "To confirm" list (due-up-to-today);
 *  - covers **all** non-paused rules — `auto_post` true *and* false — since a forecast of
 *    upcoming income/bills doesn't care whether the eventual record is silent or confirmed;
 *  - needs **no materialized-id exclusion** — a future occurrence is never materialized yet, so
 *    there's nothing to subtract.
 *
 * A rule whose template category is missing/deleted is skipped (its type can't be classified),
 * mirroring [ObservePendingConfirmationsUseCase]/[MaterializeRecurringRulesUseCase]. Emitted
 * oldest-first across all rules.
 */
class ObserveUpcomingUseCase @Inject constructor(
    private val observeRules: ObserveRecurringRulesUseCase,
    private val observeCategories: ObserveCategoriesUseCase,
) {
    /**
     * @param windowEnd inclusive end of the forecast window, re-read per emission (so a day
     *   boundary or a month roll is picked up when the underlying data next changes). Callers pick
     *   the horizon: the Records "Coming up" preview uses `today + N days`, the Analysis projected
     *   Net uses the end of the current calendar month.
     * @param today injectable clock for tests (Dagger ignores Kotlin constructor defaults).
     */
    operator fun invoke(
        windowEnd: (LocalDate) -> LocalDate,
        today: () -> LocalDate = { LocalDate.now(ZoneId.systemDefault()) },
    ): Flow<List<UpcomingOccurrence>> =
        combine(
            observeRules(),
            observeCategories(includeArchived = true),
        ) { rules, categories ->
            val now = today()
            val from = now.plusDays(1)
            val to = windowEnd(now)
            if (to.isBefore(from)) return@combine emptyList()
            val categoryById = categories.associateBy { it.id }

            rules.asSequence()
                .filter { !it.isPaused }
                .flatMap { rule ->
                    val category = categoryById[rule.template.categoryId]
                        ?: return@flatMap emptySequence<UpcomingOccurrence>()
                    val type = category.type.toTransactionType()
                    RecurringScheduler.occurrencesBetween(rule, from, to).asSequence()
                        .map { date ->
                            UpcomingOccurrence(
                                ruleId = rule.id,
                                date = date,
                                amount = rule.template.amount,
                                type = type,
                                categoryId = rule.template.categoryId,
                                categoryName = category.name,
                                note = rule.template.note,
                            )
                        }
                }
                .sortedBy { it.date }
                .toList()
        }
}
