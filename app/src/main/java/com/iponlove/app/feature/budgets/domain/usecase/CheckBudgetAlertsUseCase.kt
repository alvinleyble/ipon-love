package com.iponlove.app.feature.budgets.domain.usecase

import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.transactions.domain.model.Transaction
import java.math.BigDecimal
import java.time.ZoneId
import javax.inject.Inject

/**
 * The rungs a budget can raise, at most once each per budget per month.
 *
 * Alerts are deduped by rung **name**, never by the numeric percentage (ADR-0054 decision 3):
 * the thresholds become user-configurable and per-device in Items 2-4, so a numeric key would
 * re-fire or orphan the moment a slider moved, and two devices set to different percentages
 * would raise two rows for the same event instead of merging into one.
 *
 * [key] is embedded in synced inbox ids — **never change a shipped key**.
 */
enum class BudgetAlertSlot(val key: String) {
    WARN("warn"),
    LIMIT("limit"),
    OVER("over"),
}

/** One triggered alert: the budget, the rung it just crossed, and the inbox id it will occupy. */
data class BudgetAlertResult(
    val budget: Budget,
    val slot: BudgetAlertSlot,
    val threshold: Int,
    val spentPercent: Int,
    val notificationId: String,
)

/**
 * Pure domain use case. Given a snapshot of budgets and transactions, returns the alerts that
 * have crossed a rung and have NOT already been raised this month (as recorded in
 * [alreadyRaisedIds] — which the caller reads straight off the notification inbox, since an
 * inbox row's existence *is* the dedup record now that `BudgetAlertStore` is retired, ADR-0053).
 *
 * Both partners are notified independently on their own devices (shared budgets included).
 */
class CheckBudgetAlertsUseCase @Inject constructor() {

    operator fun invoke(
        budgets: List<Budget>,
        transactions: List<Transaction>,
        alreadyRaisedIds: Set<String>,
        currentMonth: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<BudgetAlertResult> {
        val results = mutableListOf<BudgetAlertResult>()
        for (budget in budgets) {
            if (budget.yearMonth != currentMonth) continue
            if (budget.amount <= BigDecimal.ZERO) continue
            val spent = BudgetProgressCalculator.spent(budget, transactions, zone)
            val percent = (spent.divide(budget.amount, 4, java.math.RoundingMode.HALF_UP) * BigDecimal(100)).toInt()
            for ((slot, threshold) in RUNGS) {
                if (percent >= threshold) {
                    val id = notificationId(budget.id, currentMonth, slot)
                    if (id !in alreadyRaisedIds) {
                        results += BudgetAlertResult(budget, slot, threshold, percent, id)
                    }
                }
            }
        }
        return results
    }

    companion object {
        /**
         * The rungs currently armed, warn-before-limit so a budget crossing straight to 100 %
         * raises both in a sensible order. The percentages stay hardcoded here; making warn
         * user-chosen and adding the opt-in `over` rung is Items 2-4 (ADR-0054), which only
         * has to change this map — the id shape below is already final.
         */
        val RUNGS: List<Pair<BudgetAlertSlot, Int>> = listOf(
            BudgetAlertSlot.WARN to 80,
            BudgetAlertSlot.LIMIT to 100,
        )

        /** Prefix every budget alert id shares — the inbox query filter for this category. */
        const val ID_PREFIX = "budget:"

        /** Deterministic inbox id, so phone and web raising the same rung merge (ADR-0053). */
        fun notificationId(budgetId: String, month: String, slot: BudgetAlertSlot) =
            "$ID_PREFIX$budgetId:$month:${slot.key}"
    }
}
