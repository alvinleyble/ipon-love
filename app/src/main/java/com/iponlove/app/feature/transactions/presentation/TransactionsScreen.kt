package com.iponlove.app.feature.transactions.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.MonthStepperRow
import com.iponlove.app.core.ui.StartTourOnFirstVisit
import com.iponlove.app.core.ui.coachMarkTarget
import com.iponlove.app.core.ui.money
import com.iponlove.app.core.ui.formatShortDate
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.presentation.TutorialTargets

private val IncomeColor = Color(0xFF2E7D32)

@Composable
fun TransactionsScreen(
    onOpenRecurring: () -> Unit,
    onAddTransaction: () -> Unit,
    onEditTransaction: (String) -> Unit,
    onOpenPremium: (source: String) -> Unit = {},
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    TransactionsContent(
        state = state,
        onOpenRecurring = onOpenRecurring,
        onSync = viewModel::sync,
        onAdd = onAddTransaction,
        onEdit = onEditTransaction,
        onDelete = viewModel::delete,
        onPreviousMonth = viewModel::previousMonth,
        onNextMonth = viewModel::nextMonth,
        // Locked ← at the DEEP_HISTORY −12mo wall: log the touchpoint, then route to the paywall.
        onDeepHistoryUpsell = { onOpenPremium(viewModel.onDeepHistoryUpsell()) },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TransactionsContent(
    state: TransactionsUiState,
    onOpenRecurring: () -> Unit,
    onSync: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDeepHistoryUpsell: () -> Unit = {},
) {
    StartTourOnFirstVisit(TutorialTours.RECORDS)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Records") },
                actions = {
                    IconButton(
                        onClick = onOpenRecurring,
                        modifier = Modifier.coachMarkTarget(TutorialTargets.RECORDS_RECURRING),
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Recurring rules")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.canAdd) {
                FloatingActionButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = "Add transaction")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.canAdd) {
                MonthStepperRow(
                    label = state.monthLabel,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                    canGoNext = state.canGoToNextMonth,
                    canGoPrevious = state.canGoToPreviousMonth,
                    onPreviousLocked = onDeepHistoryUpsell,
                )
            }
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onSync,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                when {
                    state.isLoading ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))

                    !state.canAdd ->
                        EmptyState(
                            title = "Create an account first",
                            body = "Transactions need an account. Add one on the Accounts tab.",
                            modifier = Modifier.align(Alignment.Center),
                        )

                    state.dayGroups.isEmpty() && !state.hasAnyTransactionEver ->
                        EmptyState(
                            title = "No transactions yet",
                            body = "Tap + to record income, an expense, or a transfer.",
                            modifier = Modifier.align(Alignment.Center),
                        )

                    state.dayGroups.isEmpty() ->
                        EmptyState(
                            title = "No transactions this month",
                            body = "Nothing recorded yet for ${state.monthLabel}.",
                            modifier = Modifier.align(Alignment.Center),
                        )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.dayGroups.forEach { group ->
                            stickyHeader(key = group.label) { DayHeader(group.label) }
                            items(group.items, key = { it.id }) { item ->
                                TransactionRow(
                                    item = item,
                                    onClick = { onEdit(item.id) },
                                    onDelete = { onDelete(item.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(label: String) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun TransactionRow(
    item: TransactionListItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatShortDate(item.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = item.signedAmount(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = item.amountColor(),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TransactionListItem.signedAmount(): String {
    val prefix = when (type) {
        TransactionType.INCOME -> "+"
        TransactionType.EXPENSE -> "−"
        TransactionType.TRANSFER -> ""
    }
    return prefix + money(amount)
}

@Composable
private fun TransactionListItem.amountColor(): Color = when (type) {
    TransactionType.INCOME -> IncomeColor
    TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
    TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
}
