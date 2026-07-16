package com.iponlove.app.feature.recurring.domain.usecase

import com.iponlove.app.core.util.DeterministicUuid
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.recurring.domain.model.PendingConfirmation
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * The live "To confirm" list for confirm-on-arrival recurring rules (Item 37) — occurrences
 * that have come due but the user hasn't yet recorded. Nothing is stored: this derives them
 * on every emission, so pending money never touches balance/budget/Analysis.
 *
 * For each rule with [com.iponlove.app.feature.recurring.domain.model.RecurringRule.autoPost]
 * `= false` (not paused), it enumerates the schedule's occurrences within the floor window
 * `[today - `[PENDING_WINDOW_MONTHS]`, today]` and drops any whose deterministic occurrence id
 * is **already materialized** (active or tombstoned). Two things fall out of that:
 *
 *  - **Floor auto-skip** — occurrences older than the window are never surfaced, so a long-
 *    neglected daily rule can't spawn hundreds of prompts (the cursor is left parked; the
 *    window, not the cursor, bounds the list).
 *  - **Order-independent** — subtracting the materialized set means a confirmed occurrence
 *    disappears immediately even when confirmed out of order (a later card before an earlier
 *    one), without the cursor having to advance past it.
 *
 * A rule whose template category is missing/deleted is skipped (its type can't be classified),
 * mirroring [MaterializeRecurringRulesUseCase]. Emitted oldest-first across all rules.
 */
class ObservePendingConfirmationsUseCase @Inject constructor(
    private val observeRules: ObserveRecurringRulesUseCase,
    private val observeCategories: ObserveCategoriesUseCase,
    private val transactionRepository: TransactionRepository,
) {
    // `today` is a parameter (not an injected dependency) so tests can pin it — Dagger doesn't
    // honour Kotlin constructor defaults, and it's re-read per emission so a day boundary is picked
    // up when the underlying data next changes.
    operator fun invoke(
        today: () -> LocalDate = { LocalDate.now(ZoneId.systemDefault()) },
    ): Flow<List<PendingConfirmation>> =
        combine(
            observeRules(),
            observeCategories(includeArchived = true),
            transactionRepository.observeMaterializedRecurringIds(),
        ) { rules, categories, materialized ->
            val now = today()
            val floor = now.minusMonths(PENDING_WINDOW_MONTHS)
            val categoryById = categories.associateBy { it.id }

            rules.asSequence()
                .filter { !it.autoPost && !it.isPaused }
                .flatMap { rule ->
                    val category = categoryById[rule.template.categoryId]
                        ?: return@flatMap emptySequence<PendingConfirmation>()
                    val type = category.type.toTransactionType()
                    RecurringScheduler.occurrencesBetween(rule, floor, now).asSequence()
                        .mapNotNull { date ->
                            val id = DeterministicUuid.v5("${rule.id}:$date").toString()
                            if (id in materialized) return@mapNotNull null
                            PendingConfirmation(
                                ruleId = rule.id,
                                occurrenceId = id,
                                date = date,
                                amount = rule.template.amount,
                                type = type,
                                categoryId = rule.template.categoryId,
                                categoryName = category.name,
                                accountId = rule.template.accountId,
                                note = rule.template.note,
                            )
                        }
                }
                .sortedBy { it.date }
                .toList()
        }

    companion object {
        /** How far back due-but-unconfirmed occurrences are still surfaced. Older ones are
         *  auto-skipped so a neglected high-frequency rule can't pile up unbounded prompts. */
        const val PENDING_WINDOW_MONTHS = 3L
    }
}
