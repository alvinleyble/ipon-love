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
import com.iponlove.app.core.ui.formatPhp
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
    Scaffold(topBar = { TopAppBar(title = { Text("Analysis") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            // Persistent header — always visible regardless of period or tab.
            PeriodSelector(selected = state.period, onSelect = onSelectPeriod)
            PeriodStepper(
                label = state.periodLabel,
                onPrevious = onPrevious,
                onNext = onNext,
                canStep = state.period != AnalysisPeriod.ALL_TIME,
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

            if (state.period == AnalysisPeriod.MONTH) {
                MonthTabLayout(state = state, modifier = Modifier.weight(1f))
            } else {
                // Day / Week: donut only — no dead tabs.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (state.hasExpenses) BreakdownSection(state) else EmptyState()
                }
            }
        }
    }
}

// ─── Month-only tab layout ──────────────────────────────────────────────────

@Composable
private fun MonthTabLayout(state: AnalysisUiState, modifier: Modifier = Modifier) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val tabLabels = listOf("Donut", "Flow", "Calendar")

    Column(modifier = modifier) {
        PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
            tabLabels.forEachIndexed { index, label ->
                Tab(
                    selected = pagerState.currentPage == index,
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
                0 -> DonutTab(state)
                1 -> FlowTab(state)
                2 -> CalendarTab(state)
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
        state.expenseFlow?.let { flow -> ExpenseFlowSection(flow) }
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
private fun PeriodStepper(label: String, onPrevious: () -> Unit, onNext: () -> Unit, canStep: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrevious, enabled = canStep) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous period")
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        IconButton(onClick = onNext, enabled = canStep) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Expense Flow", style = MaterialTheme.typography.titleSmall)
                if (flow.budgetTotal > 0f) {
                    Text(
                        text = "Budget ${formatPhp(flow.budgetTotal.toBigDecimal())}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ExpenseFlowChart(flow = flow)
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
            MetricItem(label = "Avg/day", amount = metrics.avgDailySpend, modifier = Modifier.weight(1f))
            metrics.projectedMonthEnd?.let {
                MetricItem(label = "Projected", amount = it, modifier = Modifier.weight(1f))
            }
            metrics.budgetRemaining?.let {
                val color = if (it.signum() == 0) MaterialTheme.colorScheme.error else IncomeColor
                MetricItem(label = "Left", amount = it, color = color, modifier = Modifier.weight(1f))
            }
        }
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
