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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.CapReachedSheet
import com.iponlove.app.core.ui.HeartTippedProgress
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulChip
import com.iponlove.app.core.ui.PlayfulDialog
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.SharedBadge
import com.iponlove.app.core.ui.currencyGlyph
import com.iponlove.app.core.ui.money
import com.iponlove.app.core.ui.playfulBackground
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import java.math.BigDecimal

private const val OVERALL_ID = "__overall__"

/** Like [money] but keeps a leading `-` legible instead of "₱-200.00" (ADR-0036 decision 2). */
@Composable
private fun formatSignedPhp(amount: BigDecimal): String =
    if (amount.signum() < 0) "-" + money(amount.negate()) else money(amount)

/**
 * Chrome-less Budgets body — no Scaffold/TopAppBar/FAB. The Manage host provides the single
 * scaffold + page-aware FAB (which calls [BudgetsViewModel.startCreate]); this renders only the
 * month stepper + list + editor dialog.
 */
@Composable
fun BudgetsBody(
    modifier: Modifier = Modifier,
    onOpenPremium: (source: String) -> Unit = {},
    viewModel: BudgetsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.playfulBackground()) {
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
                    itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                        BudgetCard(
                            row = row,
                            index = index,
                            nextMonthShortLabel = state.nextMonthShortLabel,
                            onClick = { viewModel.startEdit(row) },
                            onDelete = { viewModel.delete(row.id) },
                            onResetRollover = { viewModel.resetRollover(row) },
                            onDuplicate = { viewModel.duplicateToNextMonth(row) },
                            onToggleMute = { viewModel.toggleMute(row) },
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
            onScopeChange = viewModel::onScopeChange,
            onRolloverChange = viewModel::onRolloverToggle,
            onRolloverLockedTap = { onOpenPremium(viewModel.onRolloverLockedTap()) },
            onSave = viewModel::save,
            onCancel = viewModel::cancelEdit,
        )
    }

    state.upsell?.let { prompt ->
        CapReachedSheet(
            prompt = prompt,
            onDismiss = viewModel::dismissUpsell,
            onUpgrade = { onOpenPremium(viewModel.onUpsellUpgrade()) },
        )
    }
}

@Composable
private fun MonthStepper(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month",
                tint = colors.accent,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        IconButton(onClick = onNext) {
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
                tint = colors.accent,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun BudgetCard(
    row: BudgetRow,
    index: Int,
    nextMonthShortLabel: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onResetRollover: () -> Unit,
    onDuplicate: () -> Unit,
    onToggleMute: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val colors = LocalPlayfulColors.current
    PlayfulCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.leafFor(index, 22.dp, 9.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                if (row.isMuted) {
                    Spacer(Modifier.width(6.dp))
                    Text(text = "🔕", style = MaterialTheme.typography.titleMedium)
                }
                if (row.isShared) {
                    Spacer(Modifier.width(8.dp))
                    SharedBadge()
                }
                Spacer(Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = colors.textSecondary)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (row.rolloverEnabled) {
                            DropdownMenuItem(
                                text = { Text("Reset rollover") },
                                onClick = { menuOpen = false; confirmReset = true },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (row.isMuted) "Unmute alerts" else "Mute alerts") },
                            onClick = { menuOpen = false; onToggleMute() },
                        )
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
            Spacer(Modifier.height(10.dp))
            // Overspend cue preserved (grill note, v1.6.7 Item 8 Slice-6 rollout): HeartTippedProgress
            // clamps 0–100%, so an over-budget row still needs an explicit red fill to keep the signal.
            HeartTippedProgress(
                progress = row.fraction,
                modifier = Modifier.fillMaxWidth(),
                fillColor = if (row.isOverBudget) colors.semantic.negative else colors.accent,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${money(row.spent)} of ${formatSignedPhp(row.limit)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (row.isOverBudget) {
                        "Over by ${money(row.spent - row.limit)}"
                    } else {
                        "${money(row.remaining)} left"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (row.isOverBudget) colors.semantic.negative else colors.textSecondary,
                )
            }
            if (row.rolloverEnabled && row.carriedAmount.signum() != 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (row.carriedAmount.signum() > 0) {
                        "Base ${money(row.baseAmount)} + ${money(row.carriedAmount)} carried over from last month"
                    } else {
                        "Base ${money(row.baseAmount)} − ${money(row.carriedAmount.abs())} deficit carried over from last month"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (row.carriedAmount.signum() < 0) colors.semantic.negative else colors.textSecondary,
                )
            }
            if (row.limit.signum() < 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Limit already ${formatSignedPhp(row.limit)} before this month's spending",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.semantic.negative,
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
                    "This month starts from its own limit only, ignoring the balance carried " +
                        "in from last month. Rollover turns off for this month; you can turn " +
                        "it back on afterward for a fresh chain.",
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
    onScopeChange: (Boolean) -> Unit,
    onRolloverChange: (Boolean) -> Unit,
    onRolloverLockedTap: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val selectedChipId = editor.categoryId ?: OVERALL_ID
    PlayfulDialog(
        onDismissRequest = onCancel,
        title = { Text(if (editor.isEditing) "Edit budget" else "New budget") },
        text = {
            Column {
                // Personal vs Shared scope — only offered when paired, and only at creation
                // (scope is immutable once a budget exists, ADR-0047). A shared budget's spend
                // counts both partners' non-private transactions.
                if (state.isPaired && !editor.isEditing) {
                    Text("Budget type", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Row {
                        BudgetChip(
                            label = "Personal",
                            selected = !editor.shared,
                            onClick = { onScopeChange(false) },
                        )
                        Spacer(Modifier.width(8.dp))
                        BudgetChip(
                            label = "Shared",
                            selected = editor.shared,
                            onClick = { onScopeChange(true) },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                } else if (editor.shared) {
                    // Editing an existing shared budget: show it's shared (scope can't change here).
                    SharedBadge()
                    Spacer(Modifier.height(12.dp))
                }
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
                    label = { Text("Monthly limit (${currencyGlyph()})") },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        // Locked: the whole row routes to the paywall (soft gate); unlocked: inert.
                        .then(
                            if (state.rolloverLocked) Modifier.clickable(onClick = onRolloverLockedTap)
                            else Modifier
                        ),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Roll over from last month", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Unused amount carries forward as extra room; overspending carries forward as a deficit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.rolloverLocked) {
                        // Soft gate (S9): the toggle is replaced by a Premium lock; the underlying
                        // saved value is left untouched (T1 freeze) — a lapsed budget keeps rolling.
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = "Premium",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Switch(checked = editor.rolloverEnabled, onCheckedChange = onRolloverChange)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun BudgetChip(label: String, selected: Boolean, onClick: () -> Unit) {
    PlayfulChip(label = label, selected = selected, onClick = onClick)
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
