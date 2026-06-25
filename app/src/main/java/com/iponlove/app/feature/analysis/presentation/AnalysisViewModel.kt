package com.iponlove.app.feature.analysis.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import com.iponlove.app.feature.analysis.domain.model.AnalysisWindow
import com.iponlove.app.feature.analysis.domain.usecase.AnalysisCalculator
import com.iponlove.app.feature.analysis.domain.usecase.AnalysisPeriodRange
import com.iponlove.app.feature.analysis.domain.usecase.DailyNetCalculator
import com.iponlove.app.feature.analysis.domain.usecase.ExpenseFlowCalculator
import com.iponlove.app.feature.budgets.domain.usecase.ObserveBudgetsUseCase
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ObserveTransactionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
) : ViewModel() {

    private val anchor = MutableStateFlow(LocalDate.now())
    private val period = MutableStateFlow(AnalysisPeriod.MONTH)

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
            val calendarNet: CalendarNetUi?
            if (selectedPeriod == AnalysisPeriod.MONTH) {
                val startDate = window.startInclusive.atZone(zone).toLocalDate()
                val yearMonthStr = YearMonth.of(startDate.year, startDate.month).toString()
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
                val dailyNet = DailyNetCalculator.calculate(transactions, window, zone)
                calendarNet = CalendarNetUi(
                    days = dailyNet.netByDay.mapIndexed { idx, net ->
                        CalendarDayUi(
                            dayOfMonth = idx + 1,
                            netFloat = net.toFloat(),
                            isToday = dailyNet.todayDayOfMonth == idx + 1,
                        )
                    },
                    daysInMonth = dailyNet.daysInMonth,
                    firstWeekdayOffset = dailyNet.firstWeekdayOffset,
                    todayDayOfMonth = dailyNet.todayDayOfMonth,
                )
            } else {
                expenseFlow = null
                calendarNet = null
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
                calendarNet = calendarNet,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AnalysisUiState(),
        )

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
        val start = window.startInclusive.atZone(zone).toLocalDate()
        return when (window.period) {
            AnalysisPeriod.DAY -> start.format(DAY_FORMAT)
            AnalysisPeriod.MONTH -> start.format(MONTH_FORMAT)
            AnalysisPeriod.WEEK -> {
                val lastDay = window.endExclusive.atZone(zone).toLocalDate().minusDays(1)
                "${start.format(WEEK_FORMAT)} – ${lastDay.format(WEEK_FORMAT)}"
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
        val WEEK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    }
}
