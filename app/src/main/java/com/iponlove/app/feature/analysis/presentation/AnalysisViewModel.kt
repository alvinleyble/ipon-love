package com.iponlove.app.feature.analysis.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import com.iponlove.app.feature.accounts.domain.usecase.ObserveNetAssetsUseCase
import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import com.iponlove.app.feature.analysis.domain.model.AnalysisWindow
import com.iponlove.app.feature.analysis.domain.model.ExpenseFlowData
import com.iponlove.app.feature.analysis.domain.model.FlowBucketMode
import com.iponlove.app.feature.analysis.domain.usecase.AnalysisCalculator
import com.iponlove.app.feature.analysis.domain.usecase.AnalysisPeriodRange
import com.iponlove.app.feature.analysis.domain.usecase.DailyNetCalculator
import com.iponlove.app.feature.analysis.domain.usecase.ExpenseFlowCalculator
import com.iponlove.app.feature.analysis.domain.usecase.FlowComparisonCalculator
import com.iponlove.app.feature.analysis.domain.usecase.FlowMetricsCalculator
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import com.iponlove.app.feature.onboarding.domain.repository.OnboardingRepository
import com.iponlove.app.feature.settings.domain.usecase.ObservePrivacyModeUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetPrivacyModeUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ObserveTransactionsUseCase
import com.iponlove.app.feature.user.domain.model.User
import com.iponlove.app.feature.user.domain.usecase.ObserveCurrentUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Drives the Analysis tab. Holds only the view selection (anchor date + period); the
 * numbers are derived on the fly from the live transaction + category streams via
 * [AnalysisCalculator], so Analysis stays a pure read with no entity or sync of its own.
 */
@HiltViewModel
class AnalysisViewModel @Inject constructor(
    observeTransactions: ObserveTransactionsUseCase,
    observeCategories: ObserveCategoriesUseCase,
    observeCurrentUser: ObserveCurrentUserUseCase,
    observePairingState: ObservePairingStateUseCase,
    observeNetAssets: ObserveNetAssetsUseCase,
    observePrivacyMode: ObservePrivacyModeUseCase,
    private val setPrivacyMode: SetPrivacyModeUseCase,
    private val onboardingRepository: OnboardingRepository,
    private val premiumGate: PremiumGate,
    private val analytics: Analytics,
) : ViewModel() {

    private val anchor = MutableStateFlow(LocalDate.now())
    private val period = MutableStateFlow(AnalysisPeriod.MONTH)

    // createdAt is effectively immutable — reading .value as a snapshot in the combine is safe.
    private val currentUser: StateFlow<User?> = observeCurrentUser()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Unpaired-nudge card visibility (ADR-0024) — kept as its own combine so the main state
     *  combine below doesn't have to grow past Kotlin's typed 5-flow [combine] overload. */
    private val showPairingCard: Flow<Boolean> = combine(
        observePairingState(),
        onboardingRepository.observePairingCardDismissed(),
    ) { pairing, dismissed -> pairing !is PairingState.Paired && !dismissed }

    /** The single individual-scope lock driving both S10 Analysis gates: the extended-range
     *  soft-gate (sub-gate 2) and the DEEP_HISTORY back-wall (sub-gate 3). Held as a StateFlow so
     *  [previous] can read it synchronously as a backstop. Always false while dormant. */
    private val individualLocked: StateFlow<Boolean> =
        premiumGate.observeLocked(Scope.INDIVIDUAL)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val uiState: StateFlow<AnalysisUiState> =
        combine(
            observeTransactions(),
            // Include archived so historical transactions under a since-archived category
            // still show its name in the breakdown.
            observeCategories(includeArchived = true),
            anchor,
            period,
        ) { transactions, categories, anchorDate, selectedPeriod ->
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val registrationDate = currentUser.value?.createdAt?.atZone(zone)?.toLocalDate()
            val window = AnalysisPeriodRange.windowFor(anchorDate, selectedPeriod, zone)
            val result = AnalysisCalculator.analyze(transactions, window)

            val names = categories.associateBy({ it.id }, { it.name })
            val colors = categories.associateBy({ it.id }, { it.color })

            // Flow — computed for every range now (Item 3A). Budget line/remaining dropped.
            val flowData = ExpenseFlowCalculator.calculate(transactions, window, zone, registrationDate, today)
            val expenseFlow = ExpenseFlowUi(
                cumulativeByBucket = flowData.cumulativeByBucket.map { it.toFloat() },
                currentBucketIndex = flowData.currentBucketIndex,
                axisLabels = flowAxisLabels(flowData),
            )
            val fm = FlowMetricsCalculator.calculate(
                totalExpense = result.totalExpense,
                bucketMode = flowData.bucketMode,
                bucketCount = flowData.cumulativeByBucket.size,
                currentBucketIndex = flowData.currentBucketIndex,
                allowProjection = selectedPeriod != AnalysisPeriod.ALL_TIME,
            )
            // vs previous same-length window (Item 3A look-back) — a pure live re-read, no storage.
            // ALL_TIME has no prior period to compare against.
            val comparison: FlowComparisonUi? = if (selectedPeriod != AnalysisPeriod.ALL_TIME) {
                val prevAnchor = AnalysisPeriodRange.step(anchorDate, selectedPeriod, forward = false)
                val prevWindow = AnalysisPeriodRange.windowFor(prevAnchor, selectedPeriod, zone)
                val prevExpense = AnalysisCalculator.analyze(transactions, prevWindow).totalExpense
                FlowComparisonCalculator.calculate(result.totalExpense, prevExpense)?.let { cmp ->
                    FlowComparisonUi(
                        label = comparisonLabel(selectedPeriod),
                        percentChange = cmp.percentChange,
                        deltaSign = cmp.deltaSign,
                    )
                }
            } else null
            val flowMetrics = FlowMetricsUi(
                avg = fm.avg,
                perMonth = fm.perMonth,
                projected = fm.projected,
                comparison = comparison,
            )

            // Calendar + last-month income stay MONTH-only (the Calendar tab only runs under 1M).
            val calendarNet: CalendarNetUi?
            val calendarBiggestSpendDay: Int?
            val calendarNoSpendDayCount: Int
            val lastMonthIncome: BigDecimal?
            if (selectedPeriod == AnalysisPeriod.MONTH) {
                val startDate = window.startInclusive.atZone(zone).toLocalDate()
                // Context stat only — never subtracted into Net, which stays same-period
                // (income − expense both from `window`). This is the fix for "income reads
                // ₱0 before payday": show last month's actual income alongside it instead.
                val previousMonthAnchor = AnalysisPeriodRange.step(startDate, AnalysisPeriod.MONTH, forward = false)
                val previousWindow = AnalysisPeriodRange.windowFor(previousMonthAnchor, AnalysisPeriod.MONTH, zone)
                lastMonthIncome = AnalysisCalculator.analyze(transactions, previousWindow).totalIncome
                val dailyNet = DailyNetCalculator.calculate(transactions, window, zone)
                calendarNet = CalendarNetUi(
                    days = (0 until dailyNet.daysInMonth).map { idx ->
                        CalendarDayUi(
                            dayOfMonth = idx + 1,
                            incomeFloat = dailyNet.incomeByDay[idx].toFloat(),
                            expenseFloat = dailyNet.expenseByDay[idx].toFloat(),
                            isToday = dailyNet.todayDayOfMonth == idx + 1,
                        )
                    },
                    daysInMonth = dailyNet.daysInMonth,
                    firstWeekdayOffset = dailyNet.firstWeekdayOffset,
                    todayDayOfMonth = dailyNet.todayDayOfMonth,
                )
                // Calendar metrics: operate over elapsed days only.
                val elapsedCount = dailyNet.todayDayOfMonth ?: dailyNet.daysInMonth
                calendarBiggestSpendDay = (0 until elapsedCount)
                    .filter { dailyNet.expenseByDay[it].signum() > 0 }
                    .maxByOrNull { dailyNet.expenseByDay[it] }
                    ?.let { it + 1 }
                val windowYearMonth = YearMonth.of(startDate.year, startDate.month)
                val firstEligibleDayOfMonth = when {
                    registrationDate == null -> 1
                    YearMonth.from(registrationDate) > windowYearMonth -> elapsedCount + 1
                    YearMonth.from(registrationDate) == windowYearMonth -> registrationDate.dayOfMonth
                    else -> 1
                }
                calendarNoSpendDayCount = DailyNetCalculator.noSpendDayCount(
                    incomeByDay = dailyNet.incomeByDay,
                    expenseByDay = dailyNet.expenseByDay,
                    elapsedCount = elapsedCount,
                    firstEligibleDayOfMonth = firstEligibleDayOfMonth,
                )
            } else {
                calendarNet = null
                calendarBiggestSpendDay = null
                calendarNoSpendDayCount = 0
                lastMonthIncome = null
            }

            AnalysisUiState(
                isLoading = false,
                period = selectedPeriod,
                periodLabel = labelFor(window, zone),
                totalIncome = result.totalIncome,
                totalExpense = result.totalExpense,
                net = result.net,
                slices = result.expenseByCategory.map { slice ->
                    CategorySliceUi(
                        categoryId = slice.categoryId,
                        name = slice.categoryId?.let { names[it] } ?: "Uncategorized",
                        colorHex = slice.categoryId?.let { colors[it] },
                        amount = slice.amount,
                        fraction = slice.fraction,
                        percentLabel = "${(slice.fraction * 100).roundToInt()}%",
                    )
                },
                expenseFlow = expenseFlow,
                flowMetrics = flowMetrics,
                canStepForward = AnalysisPeriodRange.canStepForward(anchorDate, selectedPeriod, today, zone),
                calendarNet = calendarNet,
                calendarBiggestSpendDay = calendarBiggestSpendDay,
                calendarNoSpendDayCount = calendarNoSpendDayCount,
                lastMonthIncome = lastMonthIncome,
            )
        }.combine(showPairingCard) { state, showCard -> state.copy(showPairingCard = showCard) }
            // S10 Analysis gates (individual scope, same seam as the S9 boolean gates). Both fold
            // in the same lock; always false while dormant, so tabs + back-stepping stay open
            // pre-flip. `anchor`/`period` are read fresh here (same values that produced `state`).
            .combine(individualLocked) { state, locked ->
                state.copy(
                    extendedRangesLocked = locked,
                    canStepBackward = AnalysisPeriodRange.canStepBack(
                        anchor.value, period.value, LocalDate.now(ZoneId.systemDefault()), locked,
                    ),
                )
            }
            // Net assets (Item 14) — period-independent, so it rides its own outer combine
            // rather than growing the per-period combine above.
            .combine(observeNetAssets()) { state, netAssets -> state.copy(netAssets = netAssets) }
            .combine(observePrivacyMode()) { state, privacyModeOn ->
                state.copy(privacyModeEnabled = privacyModeOn)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = AnalysisUiState(),
            )

    fun dismissPairingCard() {
        viewModelScope.launch { onboardingRepository.dismissPairingCard() }
    }

    /** The Net-assets header's eye icon — flips the same global Privacy mode (Item 15) as every
     *  other entry point (Settings switch, Accounts eye icon); all write through to one flag. */
    fun togglePrivacyMode() {
        viewModelScope.launch { setPrivacyMode(!uiState.value.privacyModeEnabled) }
    }

    /** Switch granularity, keeping the same anchor date (the window snaps around it). */
    fun selectPeriod(newPeriod: AnalysisPeriod) {
        period.value = newPeriod
    }

    /** A tap on a premium-locked extended range (3M/6M/12M/ALL) — logs the §10.10 funnel
     *  touchpoint before the screen routes to the paywall; the range itself does not change. */
    fun onExtendedRangeUpsell(): String {
        val source = "analysis_extended_ranges"
        analytics.log("upsell_tap", source = source)
        return source
    }

    /** A tap on the locked ← at the DEEP_HISTORY −12mo wall — logs the §10.10 touchpoint before
     *  the screen routes to the paywall; the anchor does not move. Returns the paywall entry source. */
    fun onDeepHistoryUpsell(): String {
        val source = "deep_history"
        analytics.log("upsell_tap", source = source)
        return source
    }

    fun previous() {
        // Backstop for the DEEP_HISTORY back-wall (the ← is also lock-affordanced in the UI).
        val zone = ZoneId.systemDefault()
        if (!AnalysisPeriodRange.canStepBack(anchor.value, period.value, LocalDate.now(zone), individualLocked.value)) return
        anchor.value = AnalysisPeriodRange.step(anchor.value, period.value, forward = false)
    }

    fun next() {
        // Backstop for the forward cap (Item 3B) — the → button is also disabled in the UI.
        val zone = ZoneId.systemDefault()
        if (!AnalysisPeriodRange.canStepForward(anchor.value, period.value, LocalDate.now(zone), zone)) return
        anchor.value = AnalysisPeriodRange.step(anchor.value, period.value, forward = true)
    }

    /**
     * Pre-computes evenly-spaced x-axis ticks for the Flow chart from the bucket start dates,
     * so the chart stays agnostic about day-vs-month buckets. Daily single-month ranges label by
     * day number (1, 5, 10…); daily multi-month ranges (3M) by "MMM d"; monthly ranges by month
     * abbreviation (plus year when the range spans a year boundary).
     */
    private fun flowAxisLabels(data: ExpenseFlowData): List<FlowAxisLabel> {
        val dates = data.bucketStartDates
        if (dates.size <= 1) return emptyList()
        val n = dates.size
        val multiYear = dates.first().year != dates.last().year
        val multiMonth = YearMonth.from(dates.first()) != YearMonth.from(dates.last())
        val indices: List<Int> = when {
            data.bucketMode == FlowBucketMode.MONTHLY ->
                if (n <= 7) dates.indices.toList() else evenlySpacedIndices(n, 6)
            n <= 31 -> buildList {
                add(0)
                listOf(4, 9, 14, 19, 24).forEach { if (it < n - 1) add(it) }
                add(n - 1)
            }.distinct()
            else -> evenlySpacedIndices(n, 6)
        }
        return indices.map { i ->
            val d = dates[i]
            val text = when {
                data.bucketMode == FlowBucketMode.MONTHLY && multiYear -> d.format(MONTH_YY_FORMAT)
                data.bucketMode == FlowBucketMode.MONTHLY -> d.format(MONTH_ABBR_FORMAT)
                multiMonth -> d.format(MONTH_DAY_FORMAT)
                else -> d.dayOfMonth.toString()
            }
            FlowAxisLabel(bucketIndex = i, text = text)
        }
    }

    private fun evenlySpacedIndices(n: Int, count: Int): List<Int> =
        (0 until count).map { it * (n - 1) / (count - 1) }.distinct()

    private fun comparisonLabel(period: AnalysisPeriod): String = when (period) {
        AnalysisPeriod.DAY -> "vs yesterday"
        AnalysisPeriod.WEEK -> "vs last week"
        AnalysisPeriod.MONTH -> "vs last month"
        AnalysisPeriod.QUARTER -> "vs last quarter"
        AnalysisPeriod.SEMI_ANNUAL -> "vs last half"
        AnalysisPeriod.ANNUAL -> "vs last year"
        AnalysisPeriod.ALL_TIME -> "" // never shown — ALL_TIME skips the comparison
    }

    private fun labelFor(window: AnalysisWindow, zone: ZoneId): String {
        if (window.period == AnalysisPeriod.ALL_TIME) return "All Time"
        val start = window.startInclusive.atZone(zone).toLocalDate()
        return when (window.period) {
            AnalysisPeriod.DAY -> start.format(DAY_FORMAT)
            AnalysisPeriod.MONTH -> start.format(MONTH_FORMAT)
            AnalysisPeriod.WEEK -> {
                val lastDay = window.endExclusive.atZone(zone).toLocalDate().minusDays(1)
                "${start.format(WEEK_FORMAT)} – ${lastDay.format(WEEK_FORMAT)}"
            }
            AnalysisPeriod.QUARTER -> "Q${(start.monthValue - 1) / 3 + 1} ${start.year}"
            AnalysisPeriod.SEMI_ANNUAL -> "${if (start.monthValue == 1) "H1" else "H2"} ${start.year}"
            AnalysisPeriod.ANNUAL -> "${start.year}"
            AnalysisPeriod.ALL_TIME -> "All Time"
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
        val WEEK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

        // Flow x-axis tick labels (Item 3A).
        val MONTH_ABBR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM")
        val MONTH_YY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM ''yy")
        val MONTH_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
    }
}
