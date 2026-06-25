package com.iponlove.app.feature.analysis.presentation

import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import java.math.BigDecimal

/** Screen state for the Analysis tab — one [period] window at a time. */
data class AnalysisUiState(
    val isLoading: Boolean = true,
    val period: AnalysisPeriod = AnalysisPeriod.MONTH,
    /** Human label for the current window, e.g. "June 2026" or "Jun 22 – Jun 28". */
    val periodLabel: String = "",
    val totalIncome: BigDecimal = BigDecimal.ZERO,
    val totalExpense: BigDecimal = BigDecimal.ZERO,
    val net: BigDecimal = BigDecimal.ZERO,
    /** Expense breakdown, largest first; drives both the donut and its legend. */
    val slices: List<CategorySliceUi> = emptyList(),
    /** Cumulative expense curve for MONTH view; null for DAY/WEEK. */
    val expenseFlow: ExpenseFlowUi? = null,
    /** Daily-net calendar grid for MONTH view; null for DAY/WEEK. */
    val calendarNet: CalendarNetUi? = null,
) {
    val hasExpenses: Boolean get() = totalExpense.signum() > 0
}

/**
 * Chart data for the Expense Flow composable. Values are pre-converted to Float so the
 * Canvas drawing code never touches BigDecimal.
 *
 * [cumulativeByDay]: index 0 = day 1, length = [daysInMonth]. Running expense total each day.
 * [budgetTotal]: sum of personal monthly budgets for this month; 0f if none are set.
 * [todayDayOfMonth]: null when viewing a past or future month.
 */
data class ExpenseFlowUi(
    val cumulativeByDay: List<Float>,
    val budgetTotal: Float,
    val daysInMonth: Int,
    val todayDayOfMonth: Int?,
)

/**
 * One expense slice for display. [colorHex] is the category's stored color (may be null —
 * the screen falls back to a generated palette by position); [fraction] is 0f..1f.
 */
data class CategorySliceUi(
    val categoryId: String?,
    val name: String,
    val colorHex: String?,
    val amount: BigDecimal,
    val fraction: Float,
    val percentLabel: String,
)

/**
 * Calendar grid data for the daily-net view (MONTH only). Pre-converted to Float so Canvas
 * drawing never touches BigDecimal.
 *
 * [firstWeekdayOffset]: 0=Monday … 6=Sunday. Cells before day 1 are rendered empty.
 */
data class CalendarNetUi(
    val days: List<CalendarDayUi>,
    val daysInMonth: Int,
    val firstWeekdayOffset: Int,
    val todayDayOfMonth: Int?,
)

/** One day cell in the calendar. [netFloat] positive = net income; negative = net expense. */
data class CalendarDayUi(
    val dayOfMonth: Int,
    val netFloat: Float,
    val isToday: Boolean,
)
