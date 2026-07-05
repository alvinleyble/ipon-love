package com.iponlove.app.feature.budgets.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.iponlove.app.core.ui.IponFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.formatPhp
import java.math.BigDecimal

private const val OVERALL_ID = "__overall__"

/** Like [formatPhp] but keeps a leading `-` legible instead of "₱-200.00" (ADR-0036 decision 2). */
private fun formatSignedPhp(amount: BigDecimal): String =
    if (amount.signum() < 0) "-" + formatPhp(amount.negate()) else formatPhp(amount)

/**
 * Chrome-less Budgets body — no Scaffold/TopAppBar/FAB. The Manage host provides the single
 * scaffold + page-aware FAB (which calls [BudgetsViewModel.startCreate]); this renders only the
 * month stepper + list + editor dialog.
 */
@Composable
fun BudgetsBody(
    modifier: Modifier = Modifier,
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier) {
        MonthStepper(
            label = state.monthLabel,
            onPrevious = viewModel::previousMonth,
            onNext = viewModel::nextMonth,
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.rows.isEmpty() ->
                    EmptyState(state.monthLabel, Modifier.align(Alignment.Center))

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.rows, key = { it.id }) { row ->
                        BudgetCard(
                            row = row,
                            nextMonthShortLabel = state.nextMonthShortLabel,
                            onClick = { viewModel.startEdit(row) },
                            onDelete = { viewModel.delete(row.id) },
                            onResetRollover = { viewModel.resetRollover(row) },
                            onDuplicate = { viewModel.duplicateToNextMonth(row) },
                        )
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        BudgetEditorDialog(
            editor = editor,
            state = state,
            onCategoryChange = viewModel::onCategoryChange,
            onAmountChange = viewModel::onAmountChange,
            onRolloverChange = viewModel::onRolloverToggle,
            onSave = viewModel::save,
            onCancel = viewModel::cancelEdit,
        )
    }
}

@Composable
private fun MonthStepper(label: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun BudgetCard(
    row: BudgetRow,
    nextMonthShortLabel: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onResetRollover: () -> Unit,
    onDuplicate: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (row.rolloverEnabled) {
                            DropdownMenuItem(
                                text = { Text("Reset rollover") },
                                onClick = { menuOpen = false; confirmReset = true },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Duplicate for $nextMonthShortLabel") },
                            onClick = { menuOpen = false; onDuplicate() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { row.fraction },
                modifier = Modifier.fillMaxWidth(),
                color = if (row.isOverBudget) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${formatPhp(row.spent)} of ${formatSignedPhp(row.limit)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (row.isOverBudget) {
                        "Over by ${formatPhp(row.spent - row.limit)}"
                    } else {
                        "${formatPhp(row.remaining)} left"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (row.isOverBudget) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (row.rolloverEnabled && row.carriedAmount.signum() != 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (row.carriedAmount.signum() > 0) {
                        "Base ${formatPhp(row.baseAmount)} + ${formatPhp(row.carriedAmount)} carried over from last month"
                    } else {
                        "Base ${formatPhp(row.baseAmount)} − ${formatPhp(row.carriedAmount.abs())} deficit carried over from last month"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.carriedAmount.signum() < 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (row.limit.signum() < 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Limit already ${formatSignedPhp(row.limit)} before this month's spending",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset rollover?") },
            text = {
                Text(
                    "Next month starts from its own limit only, ignoring the amount carried " +
                        "here. You can turn rollover back on afterward for a fresh chain.",
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onResetRollover() }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BudgetEditorDialog(
    editor: BudgetEditorState,
    state: BudgetsUiState,
    onCategoryChange: (String?) -> Unit,
    onAmountChange: (String) -> Unit,
    onRolloverChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val selectedChipId = editor.categoryId ?: OVERALL_ID
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (editor.isEditing) "Edit budget" else "New budget") },
        text = {
            Column {
                Text("Applies to", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    BudgetChip(
                        label = "Overall",
                        selected = selectedChipId == OVERALL_ID,
                        onClick = { onCategoryChange(null) },
                    )
                    state.expenseCategories.forEach { category ->
                        Spacer(Modifier.width(8.dp))
                        BudgetChip(
                            label = category.name,
                            selected = selectedChipId == category.id,
                            onClick = { onCategoryChange(category.id) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
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
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Roll over from last month", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Unused amount carries forward as extra room; overspending carries forward as a deficit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = editor.rolloverEnabled, onCheckedChange = onRolloverChange)
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun BudgetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    IponFilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun EmptyState(monthLabel: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No budgets for $monthLabel", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap + to set a monthly limit.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
