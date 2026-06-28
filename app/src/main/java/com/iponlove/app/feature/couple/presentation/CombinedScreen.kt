package com.iponlove.app.feature.couple.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.formatPhp
import com.iponlove.app.core.ui.formatShortDate
import com.iponlove.app.core.ui.parseHexColor
import com.iponlove.app.feature.couple.domain.model.CombinedEntry
import com.iponlove.app.feature.couple.domain.model.MemberSpend
import com.iponlove.app.feature.transactions.domain.model.TransactionType

private val IncomeColor = Color(0xFF2E7D32)

/**
 * Chrome-less Combined body — no Scaffold/TopAppBar. The Couple tab host
 * ([CoupleScreen]) provides the single scaffold; this renders the pull-to-refresh
 * list + budget editor dialog only.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombinedBody(
    modifier: Modifier = Modifier,
    viewModel: CombinedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = viewModel::sync,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            state.isLoading ->
                CircularProgressIndicator(Modifier.align(Alignment.Center))

            !state.isPaired ->
                EmptyState(
                    title = "Not paired yet",
                    body = "Pair with your partner to see your shared spending here.",
                    modifier = Modifier.align(Alignment.Center),
                )

            else -> {
                val colors = state.members.associate { it.id to it.accentColor }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        CoupleBudgetCard(
                            budget = state.coupleBudget,
                            monthLabel = state.monthLabel,
                            onSet = viewModel::startEditBudget,
                            onClear = viewModel::clearBudget,
                        )
                    }
                    item { SpendingChips(state.monthLabel, state.members) }
                    if (state.entries.isEmpty()) {
                        item {
                            EmptyState(
                                title = "No shared activity yet",
                                body = "Your and your partner's non-private transactions show up here.",
                                modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                            )
                        }
                    } else {
                        items(state.entries, key = { it.id }) { entry ->
                            CombinedRow(entry = entry, ownerColor = ownerColor(colors[entry.ownerId], entry.isMine))
                        }
                    }
                }
            }
        }
    }

    state.budgetEditor?.let { editor ->
        BudgetEditorDialog(
            editor = editor,
            onAmountChange = viewModel::onBudgetAmountChange,
            onSave = viewModel::saveBudget,
            onCancel = viewModel::cancelBudgetEdit,
        )
    }
}

@Composable
private fun CoupleBudgetCard(
    budget: CoupleBudgetUi?,
    monthLabel: String,
    onSet: () -> Unit,
    onClear: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Couple budget · $monthLabel",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSet) { Text(if (budget == null) "Set" else "Edit") }
            }
            if (budget == null) {
                Text(
                    text = "Set a joint monthly limit. Both of your expenses count toward it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LinearProgressIndicator(
                    progress = { budget.fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (budget.isOverBudget) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${formatPhp(budget.spent)} of ${formatPhp(budget.limit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (budget.isOverBudget) {
                            "Over by ${formatPhp(budget.spent - budget.limit)}"
                        } else {
                            "${formatPhp(budget.remaining)} left"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (budget.isOverBudget) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                TextButton(onClick = onClear) { Text("Remove budget") }
            }
        }
    }
}

@Composable
private fun BudgetEditorDialog(
    editor: BudgetEditorState,
    onAmountChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (editor.isEditing) "Edit couple budget" else "Set couple budget") },
        text = {
            OutlinedTextField(
                value = editor.amountText,
                onValueChange = onAmountChange,
                label = { Text("Monthly limit (₱)") },
                singleLine = true,
                isError = editor.amountError,
                supportingText = if (editor.amountError) {
                    { Text("Enter an amount greater than zero") }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun SpendingChips(monthLabel: String, members: List<MemberSpend>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Spending · $monthLabel",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            members.forEach { member ->
                MemberSpendCard(member, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MemberSpendCard(member: MemberSpend, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(ownerColor(member.accentColor, member.isMine))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = member.label(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(formatPhp(member.monthlyExpense), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CombinedRow(entry: CombinedEntry, ownerColor: Color) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ColorDot(ownerColor)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${if (entry.isMine) "You" else "Partner"}  •  ${formatShortDate(entry.date)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = entry.signedAmount(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = entry.amountColor(),
            )
        }
    }
}

@Composable
private fun ColorDot(color: Color) {
    Box(Modifier.size(12.dp).clip(CircleShape).background(color))
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

/** A member's stored accent if usable, else a theme color distinct per side. */
@Composable
private fun ownerColor(accentHex: String?, isMine: Boolean): Color =
    parseHexColor(accentHex)
        ?: if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary

private fun MemberSpend.label(): String = displayName ?: if (isMine) "You" else "Partner"

@Composable
private fun CombinedEntry.signedAmount(): String {
    val prefix = when (type) {
        TransactionType.INCOME -> "+"
        TransactionType.EXPENSE -> "−"
        TransactionType.TRANSFER -> ""
    }
    return prefix + formatPhp(amount)
}

@Composable
private fun CombinedEntry.amountColor(): Color = when (type) {
    TransactionType.INCOME -> IncomeColor
    TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
    TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
}
