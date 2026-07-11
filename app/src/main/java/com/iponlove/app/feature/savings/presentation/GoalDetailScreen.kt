package com.iponlove.app.feature.savings.presentation

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.money
import com.iponlove.app.core.ui.formatShortDate
import com.iponlove.app.core.ui.parseHexColor
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDetailScreen(
    onBack: () -> Unit,
    onEditGoal: (String) -> Unit,
    viewModel: GoalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var contributionEditor by remember { mutableStateOf<ContributionEditorTarget?>(null) }
    val accent = parseHexColor(state.color) ?: MaterialTheme.colorScheme.primary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.name.ifBlank { "Goal" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.canManage) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Edit goal") },
                                    onClick = { menuOpen = false; onEditGoal(state.goalId) },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (state.isArchived) "Unarchive" else "Archive") },
                                    onClick = { menuOpen = false; viewModel.toggleArchive() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = { menuOpen = false; showDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.loaded && !state.missing) {
                Button(onClick = { contributionEditor = ContributionEditorTarget.New }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Add contribution")
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !state.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.missing -> Text(
                    "This goal is no longer available.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { GoalHeader(state, accent) }
                    if (state.contributions.isEmpty()) {
                        item {
                            Text(
                                "No contributions yet. Add your first one below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    } else {
                        item {
                            Text(
                                "Contributions",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        items(state.contributions, key = { it.id }) { row ->
                            ContributionCard(
                                row = row,
                                onEdit = { contributionEditor = ContributionEditorTarget.Edit(row) },
                                onDelete = { viewModel.deleteContribution(row.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    contributionEditor?.let { target ->
        ContributionDialog(
            target = target,
            onDismiss = { contributionEditor = null },
            onConfirm = { amount, date, note ->
                when (target) {
                    ContributionEditorTarget.New -> viewModel.addContribution(amount, date, note)
                    is ContributionEditorTarget.Edit -> viewModel.editContribution(target.row.id, amount, date, note)
                }
                contributionEditor = null
            },
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete goal?") },
            text = { Text("This removes \"${state.name}\" and its contribution history. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showDelete = false; viewModel.deleteGoal(onBack) }) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GoalHeader(state: GoalDetailUiState, accent: androidx.compose.ui.graphics.Color) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp).clip(CircleShape),
                    color = accent.copy(alpha = 0.15f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            goalIcon(state.icon) ?: Icons.Filled.Savings,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        money(state.savedAmount),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "of ${money(state.targetAmount)} goal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            GoalProgressBar(progress = state.progress, color = accent)
            state.targetDate?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Target date: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.reached) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Celebration, contentDescription = null, tint = accent)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "Goal reached! 🎉",
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContributionCard(row: ContributionRow, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(money(row.amount), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${row.byLabel} · ${formatShortDate(row.date)}" + (row.note?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.isMine) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Edit or delete")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
        }
    }
}

private sealed interface ContributionEditorTarget {
    data object New : ContributionEditorTarget
    data class Edit(val row: ContributionRow) : ContributionEditorTarget
}

@Composable
private fun ContributionDialog(
    target: ContributionEditorTarget,
    onDismiss: () -> Unit,
    onConfirm: (amount: BigDecimal, date: Instant, note: String?) -> Unit,
) {
    val existing = (target as? ContributionEditorTarget.Edit)?.row
    var amountText by remember { mutableStateOf(existing?.amount?.stripTrailingZeros()?.toPlainString() ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: Instant.now()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val amount = runCatching { BigDecimal(amountText.trim()) }.getOrNull()
    val valid = amount != null && amount > BigDecimal.ZERO

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add contribution" else "Edit contribution") },
        text = {
            Column {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { v -> amountText = v.filter { it.isDigit() || it == '.' } },
                    label = { Text("Amount (₱)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Date: ${formatShortDate(date)}")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(amount!!, date, note.ifBlank { null }) },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDatePicker) {
        PickDateDialog(
            initial = date.atZone(ZoneId.systemDefault()).toLocalDate(),
            onPick = { picked ->
                if (picked != null) {
                    date = picked.atStartOfDay(ZoneId.systemDefault()).toInstant()
                }
                showDatePicker = false
            },
            onDismiss = { showDatePicker = false },
        )
    }
}
