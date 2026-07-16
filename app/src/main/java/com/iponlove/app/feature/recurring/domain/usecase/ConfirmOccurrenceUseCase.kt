package com.iponlove.app.feature.recurring.domain.usecase

import com.iponlove.app.core.util.DeterministicUuid
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.recurring.domain.repository.RecurringRuleRepository
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Records a single pending occurrence of a confirm-on-arrival rule (Item 37) — literally the
 * per-occurrence, user-triggered version of [MaterializeRecurringRulesUseCase]:
 *
 *  - Materializes exactly one transaction on the rule's deterministic `ruleId:date` id, so a
 *    double-confirm (a second tap, or another device) is idempotent and never duplicates
 *    ([TransactionRepository.materializeTransaction] is insert-if-absent, active or tombstoned).
 *  - [amountOverride] (optional) overrides the amount for **this occurrence only** — the rule's
 *    template amount is untouched, so next month's occurrence pre-fills the original figure.
 *    A permanent change is a deliberate rule edit, not a per-occurrence tweak.
 *
 * The rule cursor is deliberately **not** advanced here: [ObservePendingConfirmationsUseCase]
 * derives "pending" by subtracting the already-materialized set, so a confirmed occurrence
 * drops off immediately — correct even when occurrences are confirmed out of order. (Skip is
 * the operation that moves the cursor; see [SkipPendingOccurrenceUseCase].)
 */
class ConfirmOccurrenceUseCase @Inject constructor(
    private val ruleRepository: RecurringRuleRepository,
    private val transactionRepository: TransactionRepository,
    private val observeCategories: ObserveCategoriesUseCase,
) {
    suspend operator fun invoke(
        ruleId: String,
        date: LocalDate,
        amountOverride: BigDecimal? = null,
    ) {
        val rule = ruleRepository.getRule(ruleId) ?: return
        val type = observeCategories(includeArchived = true).first()
            .firstOrNull { it.id == rule.template.categoryId }
            ?.type?.toTransactionType() ?: return

        val id = DeterministicUuid.v5("$ruleId:$date").toString()
        transactionRepository.materializeTransaction(
            transaction = Transaction(
                id = id,
                type = type,
                amount = amountOverride ?: rule.template.amount,
                accountId = rule.template.accountId,
                categoryId = rule.template.categoryId,
                note = rule.template.note,
                date = date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
            ),
            recurringRuleId = ruleId,
        )
    }
}
