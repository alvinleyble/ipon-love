package com.iponlove.app.feature.budgets.domain.usecase

import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.transactions.domain.model.Transaction
import java.math.BigDecimal
import java.time.ZoneId
import javax.inject.Inject

/** One triggered alert: budget + the threshold it just crossed (80 or 100). */
data class BudgetAlertResult(
    val budget: Budget,
    val threshold: Int,
    val spentPercent: Int,
    val dedupeKey: String,
)

/**
 * Pure domain use case. Given a snapshot of budgets and transactions, returns the alerts
 * that have crossed a notification threshold (80 % or 100 %) and have NOT already fired
 * this month (as recorded in [alreadyFiredKeys]).
 *
 * Both partners are notified independently on their own devices (shared budgets included).
 * Deduplication across sessions is the caller's responsibility via [alreadyFiredKeys].
 */
class CheckBudgetAlertsUseCase @Inject constructor() {

    operator fun invoke(
        budgets: List<Budget>,
        transactions: List<Transaction>,
        alreadyFiredKeys: Set<String>,
        currentMonth: String,
        zone: ZoneId = ZoneId.systemDefault(),
        startDay: Int = 1,
    ): List<BudgetAlertResult> {
        val results = mutableListOf<BudgetAlertResult>()
        for (budget in budgets) {
            if (budget.yearMonth != currentMonth) continue
            if (budget.amount <= BigDecimal.ZERO) continue
            val spent = BudgetProgressCalculator.spent(budget, transactions, zone, startDay)
            val percent = (spent.divide(budget.amount, 4, java.math.RoundingMode.HALF_UP) * BigDecimal(100)).toInt()
            for (threshold in THRESHOLDS) {
                if (percent >= threshold) {
                    val key = dedupeKey(budget.id, currentMonth, threshold)
                    if (key !in alreadyFiredKeys) {
                        results += BudgetAlertResult(budget, threshold, percent, key)
                    }
                }
            }
        }
        return results
    }

    companion object {
        val THRESHOLDS = listOf(80, 100)

        fun dedupeKey(budgetId: String, month: String, threshold: Int) =
            "$budgetId:$month:$threshold"
    }
}
