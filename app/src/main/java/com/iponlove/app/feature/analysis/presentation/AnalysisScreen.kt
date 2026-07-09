package com.iponlove.app.feature.analysis.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.StartTourOnFirstVisit
import com.iponlove.app.core.ui.coachMarkTarget
import com.iponlove.app.core.ui.formatPhp
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.presentation.TutorialTargets
import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import com.iponlove.app.feature.analysis.presentation.components.DailyNetCalendarChart
import com.iponlove.app.feature.analysis.presentation.components.DonutChart
import com.iponlove.app.feature.analysis.presentation.components.DonutSlice
import com.iponlove.app.feature.analysis.presentation.components.ExpenseFlowChart
import com.iponlove.app.feature.analysis.presentation.components.sliceColor
import kotlinx.coroutines.launch
import java.math.BigDecimal

private val IncomeColor = Color(0xFF2E7D32)

@Composable
fun AnalysisScreen(
    onOpenCouple: () -> Unit = {},
    viewModel: AnalysisViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    AnalysisContent(
        state = state,
        onSelectPeriod = viewModel::selectPeriod,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onOpenCouple = onOpenCouple,
        onDismissPairingCard = viewModel::dismissPairingCard,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalysisContent(
    state: AnalysisUiState,
    onSelectPeriod: (AnalysisPeriod) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenCouple: () -> Unit,
    onDismissPairingCard: () -> Unit,
) {
    StartTourOnFirstVisit(TutorialTours.ANALYSIS)
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    // Calendar ⟺ 1M coupling (Item 3B): the Calendar day-grid is inherently monthly, so it only
    // ever runs under 1M. Landing on Calendar under any other range auto-snaps to 1M (covers both
    // a tab tap and a swipe); tapping a non-1M range while on Calendar bounces back to Donut.
    LaunchedEffect(pagerState.currentPage, state.period) {
        if (pagerState.currentPage == CALENDAR_TAB && state.period != AnalysisPeriod.MONTH) {
            onSelectPeriod(AnalysisPeriod.MONTH)
        }
    }
    val onSelectPeriodCoupled: (AnalysisPeriod) -> Unit = { period ->
        if (pagerState.currentPage == CALENDAR_TAB && period != AnalysisPeriod.MONTH) {
            scope.launch { pagerState.animateScrollToPage(DONUT_TAB) }
        }
        onSelectPeriod(period)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Analysis") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // Persistent header — always visible regardless of period or tab.
            PeriodSelector(selected = state.period, onSelect = onSelectPeriodCoupled)
            PeriodStepper(
                label = state.periodLabel,
                onPrevious = onPrevious,
                onNext = onNext,
                // Backward is always allowed (the −12mo floor is a later paywall gate); forward is
                // capped at the current period for the free 1D/1W/1M ranges (Item 3B). ALL_TIME
                // has nothing to step to in either direction.
                canPrevious = state.period != AnalysisPeriod.ALL_TIME,
                canNext = state.canStepForward,
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            // The one activation-event entry point for unpaired users now that pairing has
            // moved out of the main nav into Settings → Couple (ADR-0024).
            if (state.showPairingCard) {
                PairingNudgeCard(onOpen = onOpenCouple, onDismiss = onDismissPairingCard)
            }

            SummaryCard(
                income = state.totalIncome,
                expense = state.totalExpense,
                net = state.net,
                lastMonthIncome = state.lastMonthIncome,
            )

            AnalysisTabLayout(
                state = state,
                pagerState = pagerState,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ─── Tab layout (all ranges) ─────────────────────────────────────────────────

private const val DONUT_TAB = 0
private const val FLOW_TAB = 1
private const val CALENDAR_TAB = 2

@Composable
private fun AnalysisTabLayout(
    state: AnalysisUiState,
    pagerState: androidx.compose.foundation.pager.PagerState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val tabLabels = listOf("Donut", "Flow", "Calendar")

    Column(modifier = modifier) {
        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier.coachMarkTarget(TutorialTargets.ANALYSIS_TABS),
        ) {
            tabLabels.forEachIndexed { index, label ->
                Tab(
                    selected = pagerState.currentPage == index,
                    // Tapping Calendar snaps to it; the LaunchedEffect above forces 1M.
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(label) },
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            when (page) {
                DONUT_TAB -> DonutTab(state)
                FLOW_TAB -> FlowTab(state)
                CALENDAR_TAB -> CalendarTab(state)
                else -> {}
            }
        }
    }
}

@Composable
private fun DonutTab(state: AnalysisUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (state.hasExpenses) BreakdownSection(state) else EmptyState()
    }
}

@Composable
private fun FlowTab(state: AnalysisUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        state.expenseFlow?.let { flow ->
            // A 1-bucket range (1D) can't draw a curve — show a short-range note instead.
            if (flow.isChartable) ExpenseFlowSection(flow) else ShortRangeFlowCard()
        }
        state.flowMetrics?.let { metrics -> FlowMetricsSection(metrics) }
    }
}

@Composable
private fun CalendarTab(state: AnalysisUiState) {
    var selectedDay by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        state.calendarNet?.let { cal ->
            CalendarInsightsCard(
                biggestSpendDay = state.calendarBiggestSpendDay,
                noSpendDayCount = state.calendarNoSpendDayCount,
            )
            CalendarNetSection(
                calendarNet = cal,
                selectedDay = selectedDay,
                onDayClick = { day -> selectedDay = if (selectedDay == day) null else day },
            )
            selectedDay?.let { day ->
                val dayUi = cal.days.find { it.dayOfMonth == day }
                if (dayUi != null) DayDetailCard(day = day, dayUi = dayUi)
            }
        }
    }
}

// ─── Persistent header composables ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(selected: AnalysisPeriod, onSelect: (AnalysisPeriod) -> Unit) {
    val periods = AnalysisPeriod.entries
    ScrollableTabRow(
        selectedTabIndex = periods.indexOf(selected),
        edgePadding = 16.dp,
        divider = {},
        modifier = Modifier.coachMarkTarget(TutorialTargets.ANALYSIS_PERIOD),
    ) {
        periods.forEach { period ->
            Tab(
                selected = selected == period,
                onClick = { onSelect(period) },
                text = { Text(period.shortLabel(), style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

@Composable
private fun PeriodStepper(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    canPrevious: Boolean = true,
    canNext: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrevious, enabled = canPrevious) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous period")
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        IconButton(onClick = onNext, enabled = canNext) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next period")
        }
    }
}

@Composable
private fun PairingNudgeCard(onOpen: () -> Unit, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(
                    "Track money together",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "Pair with your partner for a combined view, shared budgets & IOUs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    income: BigDecimal,
    expense: BigDecimal,
    net: BigDecimal,
    lastMonthIncome: BigDecimal? = null,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem("Income", income, IncomeColor, Modifier.weight(1f))
                SummaryItem("Expense", expense, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                SummaryItem(
                    label = "Net",
                    amount = net,
                    color = if (net.signum() < 0) MaterialTheme.colorScheme.error else IncomeColor,
                    modifier = Modifier.weight(1f),
                )
            }
            if (lastMonthIncome != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Last month income: ${formatPhp(lastMonthIncome)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(label: String, amount: BigDecimal, color: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatPhp(amount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

// ─── Tab content composables ─────────────────────────────────────────────────

@Composable
private fun BreakdownSection(state: AnalysisUiState) {
    val donutSlices = state.slices.mapIndexed { index, slice ->
        DonutSlice(fraction = slice.fraction, color = sliceColor(slice.colorHex, index))
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DonutChart(slices = donutSlices) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Spent",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatPhp(state.totalExpense),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        state.slices.forEachIndexed { index, slice ->
            LegendRow(
                color = sliceColor(slice.colorHex, index),
                name = slice.name,
                amount = slice.amount,
                percentLabel = slice.percentLabel,
            )
        }
    }
}

@Composable
private fun LegendRow(color: Color, name: String, amount: BigDecimal, percentLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.size(14.dp)) {}
        Spacer(Modifier.width(12.dp))
        Text(text = name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = percentLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = formatPhp(amount),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No spending to analyze", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Add some expenses for this period to see the breakdown.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ExpenseFlowSection(flow: ExpenseFlowUi) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Expense Flow", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            ExpenseFlowChart(flow = flow)
        }
    }
}

@Composable
private fun ShortRangeFlowCard() {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Range too short to chart", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "A single day can't show a spending curve. See the daily average below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FlowMetricsSection(metrics: FlowMetricsUi) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MetricItem(
                label = if (metrics.perMonth) "Avg/month" else "Avg/day",
                amount = metrics.avg,
                modifier = Modifier.weight(1f),
            )
            metrics.projected?.let {
                MetricItem(label = "Projected", amount = it, modifier = Modifier.weight(1f))
            }
            metrics.comparison?.let {
                ComparisonMetricItem(comparison = it, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ComparisonMetricItem(comparison: FlowComparisonUi, modifier: Modifier = Modifier) {
    // More spend than last period reads red (error); less reads green; flat is neutral.
    val color = when {
        comparison.deltaSign > 0 -> MaterialTheme.colorScheme.error
        comparison.deltaSign < 0 -> IncomeColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val value = when {
        comparison.percentChange == null -> "New" // no prior spending to compare against
        comparison.deltaSign == 0 -> "0%"
        else -> {
            val arrow = if (comparison.deltaSign > 0) "▲" else "▼"
            "$arrow ${kotlin.math.abs(comparison.percentChange)}%"
        }
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = comparison.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MetricItem(
    label: String,
    amount: BigDecimal,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = formatPhp(amount),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CalendarInsightsCard(biggestSpendDay: Int?, noSpendDayCount: Int) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "No-spend days",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$noSpendDayCount",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (biggestSpendDay != null) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Biggest spend",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Day $biggestSpendDay",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarNetSection(
    calendarNet: CalendarNetUi,
    selectedDay: Int?,
    onDayClick: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Daily Net", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            DailyNetCalendarChart(
                calendarNet = calendarNet,
                selectedDay = selectedDay,
                onDayClick = onDayClick,
            )
        }
    }
}

@Composable
private fun DayDetailCard(day: Int, dayUi: CalendarDayUi) {
    val income = BigDecimal.valueOf(dayUi.incomeFloat.toDouble())
    val expense = BigDecimal.valueOf(dayUi.expenseFloat.toDouble())
    val net = income - expense
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Day $day",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                SummaryItem("Income", income, IncomeColor, Modifier.weight(1f))
                SummaryItem("Expense", expense, MaterialTheme.colorScheme.error, Modifier.weight(1f))
                SummaryItem(
                    label = "Net",
                    amount = net,
                    color = if (net.signum() < 0) MaterialTheme.colorScheme.error else IncomeColor,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun AnalysisPeriod.shortLabel(): String = when (this) {
    AnalysisPeriod.DAY -> "1D"
    AnalysisPeriod.WEEK -> "1W"
    AnalysisPeriod.MONTH -> "1M"
    AnalysisPeriod.QUARTER -> "3M"
    AnalysisPeriod.SEMI_ANNUAL -> "6M"
    AnalysisPeriod.ANNUAL -> "12M"
    AnalysisPeriod.ALL_TIME -> "ALL"
}
