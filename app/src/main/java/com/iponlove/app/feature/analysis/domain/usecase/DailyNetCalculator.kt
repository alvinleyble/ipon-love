package com.iponlove.app.feature.analysis.domain.usecase

import com.iponlove.app.feature.analysis.domain.model.AnalysisWindow
import com.iponlove.app.feature.analysis.domain.model.DailyNetData
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/**
 * Computes per-day net (income − expense) for a single calendar month.
 *
 * TRANSFER transactions are excluded — they do not affect net income. Only transactions
 * whose [Transaction.date] falls inside [window] are counted; the window must be a MONTH
 * window (calling with DAY or WEEK produces a partial result the UI won't render).
 */
object DailyNetCalculator {

    fun calculate(
        transactions: List<Transaction>,
        window: AnalysisWindow,
        zone: ZoneId,
    ): DailyNetData {
        val startDate = window.startInclusive.atZone(zone).toLocalDate()
        val daysInMonth = startDate.lengthOfMonth()
        // ISO dayOfWeek: 1=Mon … 7=Sun → Sun-first offset: Sunday→0, Monday→1 … Saturday→6
        val firstWeekdayOffset = startDate.dayOfWeek.value % 7

        val today = LocalDate.now(zone)
        val todayDay: Int? =
            if (today.year == startDate.year && today.month == startDate.month) today.dayOfMonth
            else null

        val dailyIncome = Array(daysInMonth) { BigDecimal.ZERO }
        val dailyExpense = Array(daysInMonth) { BigDecimal.ZERO }

        for (t in transactions) {
            if (t.date < window.startInclusive || t.date >= window.endExclusive) continue
            val dayIdx = t.date.atZone(zone).toLocalDate().dayOfMonth - 1
            when (t.type) {
                TransactionType.INCOME -> dailyIncome[dayIdx] = dailyIncome[dayIdx] + t.amount
                TransactionType.EXPENSE -> dailyExpense[dayIdx] = dailyExpense[dayIdx] + t.amount
                TransactionType.TRANSFER -> Unit
            }
        }

        return DailyNetData(
            incomeByDay = dailyIncome.toList(),
            expenseByDay = dailyExpense.toList(),
            daysInMonth = daysInMonth,
            todayDayOfMonth = todayDay,
            firstWeekdayOffset = firstWeekdayOffset,
        )
    }
}
