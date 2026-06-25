package com.iponlove.app.feature.analysis.domain.usecase

import com.iponlove.app.feature.analysis.domain.model.ExpenseFlowData
import com.iponlove.app.feature.analysis.domain.model.AnalysisWindow
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

/**
 * Builds the day-by-day cumulative expense curve for a single calendar month.
 *
 * Only EXPENSE transactions inside [window] count; INCOME and TRANSFER are ignored.
 * The result has one entry per day of the month (index 0 = day 1). Each entry is the
 * running sum of all expenses from day 1 through that day — i.e., entry[N] >= entry[N-1].
 *
 * [window] must be a MONTH window; calling with a DAY or WEEK window is not an error but
 * produces a single-day or partial-week result that the UI won't render.
 */
object ExpenseFlowCalculator {

    fun calculate(
        transactions: List<Transaction>,
        window: AnalysisWindow,
        zone: ZoneId,
    ): ExpenseFlowData {
        val startDate = window.startInclusive.atZone(zone).toLocalDate()
        val daysInMonth = startDate.lengthOfMonth()

        val today = LocalDate.now(zone)
        val todayDay: Int? =
            if (today.year == startDate.year && today.month == startDate.month) today.dayOfMonth
            else null

        val daily = Array(daysInMonth) { BigDecimal.ZERO }
        for (t in transactions) {
            if (t.type != TransactionType.EXPENSE) continue
            if (t.date < window.startInclusive || t.date >= window.endExclusive) continue
            val dayIdx = t.date.atZone(zone).toLocalDate().dayOfMonth - 1
            daily[dayIdx] = daily[dayIdx] + t.amount
        }

        val cumulative = ArrayList<BigDecimal>(daysInMonth)
        var running = BigDecimal.ZERO
        for (d in daily) {
            running += d
            cumulative += running
        }

        return ExpenseFlowData(
            cumulativeByDay = cumulative,
            daysInMonth = daysInMonth,
            todayDayOfMonth = todayDay,
        )
    }
}
