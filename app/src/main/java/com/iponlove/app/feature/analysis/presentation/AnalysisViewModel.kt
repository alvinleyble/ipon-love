package com.iponlove.app.feature.analysis.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import com.iponlove.app.feature.analysis.domain.model.AnalysisWindow
import com.iponlove.app.feature.analysis.domain.usecase.AnalysisCalculator
import com.iponlove.app.feature.analysis.domain.usecase.AnalysisPeriodRange
import com.iponlove.app.feature.analysis.domain.usecase.DailyNetCalculator
import com.iponlove.app.feature.analysis.domain.usecase.ExpenseFlowCalculator
import com.iponlove.app.feature.analysis.domain.usecase.FlowMetricsCalculator
import com.iponlove.app.feature.budgets.domain.usecase.ObserveBudgetsUseCase
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import com.iponlove.app.feature.onboarding.domain.repository.OnboardingRepository
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
    observeBudgets: ObserveBudgetsUseCase,
    observeCurrentUser: ObserveCurrentUserUseCase,
    observePairingState: ObservePairingStateUseCase,
    private val onboardingRepository: OnboardingRepository,
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

    val uiState: StateFlow<AnalysisUiState> =
        combine(
            observeTransactions(),
            // Include archived so historical transactions under a since-archived category
            // still show its name in the breakdown.
            observeCategories(includeArchived = true),
            observeBudgets(),
            anchor,
            period,
        ) { transactions, categories, budgets, anchorDate, selectedPeriod ->
            val zone = ZoneId.systemDefault()
            val window = AnalysisPeriodRange.windowFor(anchorDate, selectedPeriod, zone)
            val result = AnalysisCalculator.analyze(transactions, window)

            val names = categories.associateBy({ it.id }, { it.name })
            val colors = categories.associateBy({ it.id }, { it.color })

            val expenseFlow: ExpenseFlowUi?
            val flowMetrics: FlowMetricsUi?
            val calendarNet: CalendarNetUi?
            val calendarBiggestSpendDay: Int?
            val calendarNoSpendDayCount: Int
            val lastMonthIncome: BigDecimal?
            if (selectedPeriod == AnalysisPeriod.MONTH) {
                val startDate = window.startInclusive.atZone(zone).toLocalDate()
                val yearMonthStr = YearMonth.of(startDate.year, startDate.month).toString()
                // Context stat only — never subtracted into Net, which stays same-period
                // (income − expense both from `window`). This is the fix for "income reads
                // ₱0 before payday": show last month's actual income alongside it instead.
                val previousMonthAnchor = AnalysisPeriodRange.step(startDate, AnalysisPeriod.MONTH, forward = false)
                val previousWindow = AnalysisPeriodRange.windowFor(previousMonthAnchor, AnalysisPeriod.MONTH, zone)
                lastMonthIncome = AnalysisCalculator.analyze(transactions, previousWindow).totalIncome
                val budgetTotal = budgets
                    .filter { it.yearMonth == yearMonthStr }
                    .fold(BigDecimal.ZERO) { acc, b -> acc + b.amount }
                val flowData = ExpenseFlowCalculator.calculate(transactions, window, zone)
                expenseFlow = ExpenseFlowUi(
                    cumulativeByDay = flowData.cumulativeByDay.map { it.toFloat() },
                    budgetTotal = budgetTotal.toFloat(),
                    daysInMonth = flowData.daysInMonth,
                    todayDayOfMonth = flowData.todayDayOfMonth,
                )
                val daysElapsed = flowData.todayDayOfMonth ?: flowData.daysInMonth
                val fm = FlowMetricsCalculator.calculate(
                    totalExpense = result.totalExpense,
                    daysElapsed = daysElapsed,
                    daysInMonth = flowData.daysInMonth,
                    isCurrentMonth = flowData.todayDayOfMonth != null,
                    budgetTotal = budgetTotal,
                )
                flowMetrics = FlowMetricsUi(
                    avgDailySpend = fm.avgDailySpend,
                    projectedMonthEnd = fm.projectedMonthEnd,
                    budgetRemaining = fm.budgetRemaining,
                )
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
                val regDate = currentUser.value?.createdAt?.atZone(zone)?.toLocalDate()
                val firstEligibleDayOfMonth = when {
                    regDate == null -> 1
                    YearMonth.from(regDate) > windowYearMonth -> elapsedCount + 1
                    YearMonth.from(regDate) == windowYearMonth -> regDate.dayOfMonth
                    else -> 1
                }
                calendarNoSpendDayCount = DailyNetCalculator.noSpendDayCount(
                    incomeByDay = dailyNet.incomeByDay,
                    expenseByDay = dailyNet.expenseByDay,
                    elapsedCount = elapsedCount,
                    firstEligibleDayOfMonth = firstEligibleDayOfMonth,
                )
            } else {
                expenseFlow = null
                flowMetrics = null
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
                calendarNet = calendarNet,
                calendarBiggestSpendDay = calendarBiggestSpendDay,
                calendarNoSpendDayCount = calendarNoSpendDayCount,
                lastMonthIncome = lastMonthIncome,
            )
        }.combine(showPairingCard) { state, showCard -> state.copy(showPairingCard = showCard) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = AnalysisUiState(),
            )

    fun dismissPairingCard() {
        viewModelScope.launch { onboardingRepository.dismissPairingCard() }
    }

    /** Switch granularity, keeping the same anchor date (the window snaps around it). */
    fun selectPeriod(newPeriod: AnalysisPeriod) {
        period.value = newPeriod
    }

    fun previous() {
        anchor.value = AnalysisPeriodRange.step(anchor.value, period.value, forward = false)
    }

    fun next() {
        anchor.value = AnalysisPeriodRange.step(anchor.value, period.value, forward = true)
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
    }
}
