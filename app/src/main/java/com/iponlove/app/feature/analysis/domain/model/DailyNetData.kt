package com.iponlove.app.feature.analysis.domain.model

import java.math.BigDecimal

/**
 * Per-day income and expense for a single calendar month. Produced by
 * [com.iponlove.app.feature.analysis.domain.usecase.DailyNetCalculator]; never stored or synced.
 *
 * [incomeByDay] / [expenseByDay]: index 0 = day 1; both are always non-negative.
 * [firstWeekdayOffset]: Sun-first (0=Sunday … 6=Saturday); used for the calendar grid offset.
 */
data class DailyNetData(
    val incomeByDay: List<BigDecimal>,
    val expenseByDay: List<BigDecimal>,
    val daysInMonth: Int,
    val todayDayOfMonth: Int?,
    val firstWeekdayOffset: Int,
)
