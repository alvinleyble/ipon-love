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
    /** Cumulative expense curve for the Flow tab — computed for every range (Item 3A). */
    val expenseFlow: ExpenseFlowUi? = null,
    /** Pace metrics derived from the expense flow for the Flow tab. */
    val flowMetrics: FlowMetricsUi? = null,
    /** False disables the stepper's "next" button — forward cap for 1D/1W/1M (Item 3B). */
    val canStepForward: Boolean = false,
    /**
     * False only when a free-range (1D/1W/1M) back-step would cross the DEEP_HISTORY −12mo wall
     * while locked (S10) — drives the lock affordance on the stepper's ←. Always true while
     * dormant, for premium users, and for the extended ranges (which are gated separately).
     */
    val canStepBackward: Boolean = true,
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
    /**
     * Total balance across active accounts (Item 14) — the same figure Accounts shows, mirrored
     * read-only in the Analysis top bar. Period-independent: unaffected by [period]/[periodLabel].
     */
    val netAssets: BigDecimal = BigDecimal.ZERO,
    /** Global Privacy mode (Item 15) — drives the Net-assets header's eye icon. */
    val privacyModeEnabled: Boolean = false,
    /** Unpaired + not-yet-dismissed — shows the pairing nudge card (ADR-0024). */
    val showPairingCard: Boolean = false,
    /**
     * True when the extended ranges (3M/6M/12M/ALL) are premium-locked for this user
     * (`ANALYSIS_EXTENDED_RANGES`, S10 — enforcement ON + no access, individual scope). Drives the
     * lock badge on those tabs and routes a locked tap to the paywall. Always false while dormant.
     */
    val extendedRangesLocked: Boolean = false,
    /**
     * Schedule-based month-end forecast (Item 37 Slice 2, premium `RECURRING_FORECAST`). Non-null
     * only for the **current in-progress month** when a schedule exists **and** the forecast is
     * unlocked (dormant/premium) — the Summary then shows it labeled as a forecast, in place of
     * [lastMonthIncome]. Null in every other case (past months, no schedule, or locked).
     */
    val projectedNet: BigDecimal? = null,
    /**
     * True when a month-end forecast *is* available for the current month but the user is
     * `RECURRING_FORECAST`-locked (enforcement ON + no access) — drives the Summary's locked
     * forecast teaser (routes to the paywall). Always false while dormant.
     */
    val showForecastUpsell: Boolean = false,
) {
    val hasExpenses: Boolean get() = totalExpense.signum() > 0
}

/**
 * Derived spending-pace metrics for the Flow tab. All values are pre-computed BigDecimal so
 * the UI never repeats the arithmetic.
 *
 * [perMonth] picks the label + unit: true → "Avg/month" (6M/12M/ALL), false → "Avg/day".
 * [projected] is null on a completed (past) period, ALL_TIME, or a 1-bucket range —
 * extrapolation is meaningless there.
 */
data class FlowMetricsUi(
    val avg: BigDecimal,
    val perMonth: Boolean,
    val projected: BigDecimal?,
    /** Look-back vs the prior same-length window; null for ALL_TIME or two empty windows. */
    val comparison: FlowComparisonUi? = null,
)

/**
 * "vs last month/quarter/…" look-back for the Flow tab. [percentChange] is null when there's no
 * prior spending to divide by (the UI shows "New"); [deltaSign] is -1 (spent less), 0, or +1.
 */
data class FlowComparisonUi(
    val label: String,
    val percentChange: Int?,
    val deltaSign: Int,
)

/**
 * Chart data for the Expense Flow composable (Item 3A — generalized to any range). Values are
 * pre-converted to Float so the Canvas drawing code never touches BigDecimal.
 *
 * [cumulativeByBucket]: index 0 = first bucket. Running expense total per bucket (day or month).
 * [currentBucketIndex]: bucket containing today, or null on a past period (drives the marker).
 * [axisLabels]: pre-computed (bucketIndex → label) ticks for the x-axis, so the chart stays
 * agnostic about whether buckets are days or months.
 */
data class ExpenseFlowUi(
    val cumulativeByBucket: List<Float>,
    val currentBucketIndex: Int?,
    val axisLabels: List<FlowAxisLabel>,
) {
    /** A single-point curve (1D) can't be drawn as a line — the UI shows a short-range state. */
    val isChartable: Boolean get() = cumulativeByBucket.size > 1
}

/** One x-axis tick for the Flow chart: which bucket it sits on and its short label. */
data class FlowAxisLabel(val bucketIndex: Int, val text: String)

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
