package com.iponlove.app.feature.analysis.domain.usecase

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Derives lightweight spending-pace metrics for the Analysis Flow tab.
 *
 * [daysElapsed] must be > 0 (caller guarantees: minimum is 1 on the first day of a month).
 */
object FlowMetricsCalculator {

    data class Result(
        val avgDailySpend: BigDecimal,
        /** Linear projection to month-end; null for completed (past) months. */
        val projectedMonthEnd: BigDecimal?,
        /** Budget ceiling minus total spend so far; null when no budget is set. */
        val budgetRemaining: BigDecimal?,
    )

    fun calculate(
        totalExpense: BigDecimal,
        daysElapsed: Int,
        daysInMonth: Int,
        isCurrentMonth: Boolean,
        budgetTotal: BigDecimal,
    ): Result {
        val avg = totalExpense.divide(BigDecimal(daysElapsed), 2, RoundingMode.HALF_UP)

        val projected: BigDecimal? = if (isCurrentMonth) {
            avg.multiply(BigDecimal(daysInMonth)).setScale(2, RoundingMode.HALF_UP)
        } else null

        val remaining: BigDecimal? = if (budgetTotal.signum() > 0) {
            (budgetTotal - totalExpense).max(BigDecimal.ZERO)
        } else null

        return Result(avg, projected, remaining)
    }
}
