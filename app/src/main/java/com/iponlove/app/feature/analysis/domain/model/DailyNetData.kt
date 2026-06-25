package com.iponlove.app.feature.analysis.domain.model

import java.math.BigDecimal

/**
 * Per-day net (income − expense) for a single calendar month. Produced by
 * [com.iponlove.app.feature.analysis.domain.usecase.DailyNetCalculator]; never stored or synced.
 *
 * [netByDay]: index 0 = day 1. Positive = net income; negative = net expense; zero = no activity.
 * [firstWeekdayOffset]: 0=Monday … 6=Sunday (ISO); used to compute the grid offset in the Calendar view.
 */
data class DailyNetData(
    val netByDay: List<BigDecimal>,
    val daysInMonth: Int,
    val todayDayOfMonth: Int?,
    val firstWeekdayOffset: Int,
)
