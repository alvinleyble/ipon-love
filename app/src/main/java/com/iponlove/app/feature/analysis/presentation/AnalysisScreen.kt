package com.iponlove.app.feature.analysis.presentation

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.formatPhp
import com.iponlove.app.feature.analysis.domain.model.AnalysisPeriod
import com.iponlove.app.feature.analysis.presentation.components.DonutChart
import com.iponlove.app.feature.analysis.presentation.components.DonutSlice
import com.iponlove.app.feature.analysis.presentation.components.ExpenseFlowChart
import com.iponlove.app.feature.analysis.presentation.components.sliceColor
import java.math.BigDecimal

private val IncomeColor = Color(0xFF2E7D32)

@Composable
fun AnalysisScreen(viewModel: AnalysisViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    AnalysisContent(
        state = state,
        onSelectPeriod = viewModel::selectPeriod,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalysisContent(
    state: AnalysisUiState,
    onSelectPeriod: (AnalysisPeriod) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Analysis") }) }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            PeriodSelector(selected = state.period, onSelect = onSelectPeriod)
            PeriodStepper(label = state.periodLabel, onPrevious = onPrevious, onNext = onNext)

            if (state.isLoading) {
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            SummaryCard(income = state.totalIncome, expense = state.totalExpense, net = state.net)

            state.expenseFlow?.let { flow ->
                ExpenseFlowSection(flow)
            }

            if (state.hasExpenses) {
                BreakdownSection(state)
            } else {
                EmptyState()
            }
        }
    }
}

@Composable
private fun PeriodSelector(selected: AnalysisPeriod, onSelect: (AnalysisPeriod) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnalysisPeriod.entries.forEach { period ->
            FilterChip(
                selected = selected == period,
                onClick = { onSelect(period) },
                label = { Text(period.label()) },
            )
        }
    }
}

@Composable
private fun PeriodStepper(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous period")
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next period")
        }
    }
}

@Composable
private fun SummaryCard(income: BigDecimal, expense: BigDecimal, net: BigDecimal) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
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

private fun AnalysisPeriod.label(): String = when (this) {
    AnalysisPeriod.DAY -> "Day"
    AnalysisPeriod.WEEK -> "Week"
    AnalysisPeriod.MONTH -> "Month"
}
