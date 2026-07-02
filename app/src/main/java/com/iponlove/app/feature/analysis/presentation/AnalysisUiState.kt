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
    /** Pace metrics derived from the expense flow for the Flow tab; null for DAY/WEEK. */
    val flowMetrics: FlowMetricsUi? = null,
    /** Daily-net calendar grid for MONTH view; null for DAY/WEEK. */
    val calendarNet: CalendarNetUi? = null,
    /** 1-based day-of-month with the highest expense; null when no expenses. MONTH only. */
    val calendarBiggestSpendDay: Int? = null,
    /** Count of elapsed days with neither income nor expense. MONTH only. */
    val calendarNoSpendDayCount: Int = 0,
    /**
     * Total income for the calendar month before this one — a context stat, never subtracted
     * into [net]. Net stays strictly same-period (this month's income minus this month's
     * expense), even when that reads negative before payday; this field is the actual fix for
     * "income reads ₱0" pre-payday. MONTH only; null for DAY/WEEK.
     */
    val lastMonthIncome: BigDecimal? = null,
    /** Unpaired + not-yet-dismissed — shows the pairing nudge card (ADR-0024). */
    val showPairingCard: Boolean = false,
) {
    val hasExpenses: Boolean get() = totalExpense.signum() > 0
}

/**
 * Derived spending-pace metrics for the Flow tab. All values are pre-computed BigDecimal so
 * the UI never repeats the arithmetic.
 *
 * [projectedMonthEnd] is null when viewing a completed (past) month — extrapolation is
 * meaningless once the month is over. [budgetRemaining] is null when no budgets are set.
 */
data class FlowMetricsUi(
    val avgDailySpend: BigDecimal,
    val projectedMonthEnd: BigDecimal?,
    val budgetRemaining: BigDecimal?,
)

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

/** One day cell in the calendar. Both [incomeFloat] and [expenseFloat] are non-negative. */
data class CalendarDayUi(
    val dayOfMonth: Int,
    val incomeFloat: Float,
    val expenseFloat: Float,
    val isToday: Boolean,
)
