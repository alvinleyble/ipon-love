package com.iponlove.app.feature.analysis.domain.model

import java.math.BigDecimal

/**
 * Day-by-day cumulative expense for one calendar month. Pure domain model — derived from
 * the transaction ledger on the fly; never stored or synced (ADR-0007).
 *
 * [cumulativeByDay]: index 0 = day 1, index N-1 = last day of month. Each value is the
 * running total of expenses from day 1 through that day.
 * [todayDayOfMonth]: null when the selected window is not the current month, used by the
 * chart to draw the "today" marker.
 */
data class ExpenseFlowData(
    val cumulativeByDay: List<BigDecimal>,
    val daysInMonth: Int,
    val todayDayOfMonth: Int?,
)
