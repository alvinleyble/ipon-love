package com.iponlove.app.feature.recurring.domain.usecase

import com.iponlove.app.core.util.DeterministicUuid
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.recurring.domain.repository.RecurringRuleRepository
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Turns due **auto-post** recurring rules into real transactions (the "catch-up" pass), then
 * advances each such rule's cursor. Idempotent and safe to run on every app start / screen open:
 *
 *  - Each occurrence's transaction id is [DeterministicUuid.v5] of `ruleId:occurrenceDate`,
 *    so re-running, or a second device running it, never duplicates and never resurrects
 *    a tombstoned occurrence ([TransactionRepository.materializeTransaction] is insert-if-absent).
 *  - The generated type is derived from the template category's [CategoryType]; a rule whose
 *    category is missing/deleted is skipped (we can't classify it) and retried next pass.
 *
 * Only rules with [RecurringRule.autoPost] `= true` are materialized here (Item 37). A
 * confirm-on-arrival rule (`autoPost = false`, the default) is deliberately left untouched:
 * it never writes early — its due occurrences are derived by [ObservePendingConfirmationsUseCase]
 * and posted one-by-one via [ConfirmOccurrenceUseCase]. Its cursor simply parks.
 *
 * Cross-feature by design (writes transactions, reads categories), like the budgets and
 * analysis calculators — recurring is the only place transactions are created with a
 * `recurring_rule_id`.
 */
class MaterializeRecurringRulesUseCase @Inject constructor(
    private val ruleRepository: RecurringRuleRepository,
    private val transactionRepository: TransactionRepository,
    private val observeCategories: ObserveCategoriesUseCase,
) {
    suspend operator fun invoke(asOf: LocalDate = LocalDate.now()) {
        val rules = ruleRepository.activeRules()
        if (rules.isEmpty()) return

        val categoryTypes = observeCategories(includeArchived = true).first()
            .associate { it.id to it.type }

        for (rule in rules) {
            // Confirm-on-arrival rules (the default) never auto-materialize; paused rules are
            // suspended. Only opt-in auto-post rules generate here.
            if (rule.isPaused || !rule.autoPost) continue

            val type = categoryTypes[rule.template.categoryId]?.toTransactionType() ?: continue
            val outcome = RecurringScheduler.run(rule, asOf)
            if (outcome.occurrences.isEmpty()) continue

            for (date in outcome.occurrences) {
                val id = DeterministicUuid.v5("${rule.id}:$date").toString()
                transactionRepository.materializeTransaction(
                    transaction = Transaction(
                        id = id,
                        type = type,
                        amount = rule.template.amount,
                        accountId = rule.template.accountId,
                        categoryId = rule.template.categoryId,
                        note = rule.template.note,
                        date = date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    ),
                    recurringRuleId = rule.id,
                )
            }
            ruleRepository.upsertRule(rule.copy(nextDate = outcome.nextDate))
        }
    }
}
