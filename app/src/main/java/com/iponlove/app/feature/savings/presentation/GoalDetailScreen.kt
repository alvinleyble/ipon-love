package com.iponlove.app.feature.savings.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.HeartBullet
import com.iponlove.app.core.ui.HeartTippedProgress
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.SharedBadge
import com.iponlove.app.core.ui.currencyGlyph
import com.iponlove.app.core.ui.money
import com.iponlove.app.core.ui.formatShortDate
import com.iponlove.app.core.ui.parseHexColor
import com.iponlove.app.core.ui.playfulBackground
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

/**
 * Restyled for "Playful Pop" (v1.6.7 Item 8 Slice 5). The [TopAppBar] (back arrow, title, overflow
 * menu) is deliberately left as standard M3 chrome — no established Playful pattern yet covers a
 * back+overflow-menu bar, matching the "dialogs/host chrome stay conservative" precedent from
 * Slices 3–4. The content area repaints with [playfulBackground] (same local-override pattern
 * [AccountsBody]/[CombinedBody] use under their own untouched hosts) and the goal header +
 * contribution rows move onto [PlayfulCard]/[HeartTippedProgress]. The add/edit-contribution
 * [AlertDialog]s and the delete-confirmation dialog stay untouched. The privacy eye (v1.6.7 Item 7
 * closeout) sits in the [TopAppBar] actions, before the overflow menu.
 */
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
                    IconButton(onClick = viewModel::togglePrivacyMode) {
                        Icon(
                            imageVector = if (state.privacyModeEnabled) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (state.privacyModeEnabled) "Show amounts" else "Hide amounts",
                        )
                    }
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
                AddContributionFab(onClick = { contributionEditor = ContributionEditorTarget.New })
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().playfulBackground().padding(padding)) {
            when {
                !state.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.missing -> Text(
                    "This goal is no longer available.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { GoalHeader(state) }
                    if (state.contributions.isEmpty()) {
                        item {
                            Text(
                                "No contributions yet. Add your first one below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalPlayfulColors.current.textSecondary,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    } else {
                        item {
                            Text(
                                "Contributions",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = LocalPlayfulColors.current.textSecondary,
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

/** A wide accent pill FAB — the extended-FAB flavor of the app's leaf-squircle FAB identity. */
@Composable
private fun AddContributionFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalPlayfulColors.current
    Row(
        modifier = modifier
            .clip(LeafShapes.Chip)
            .background(colors.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Add contribution",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onAccent,
        )
    }
}

@Composable
private fun GoalHeader(state: GoalDetailUiState) {
    val colors = LocalPlayfulColors.current
    val squircleColor = parseHexColor(state.color) ?: colors.deepPlum
    val squircleInk = if (state.color != null) Color.White else colors.onDeepPlum
    val progressColor = parseHexColor(state.color) ?: colors.accent

    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Hero,
        contentPadding = 18.dp,
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .rotate(-4f)
                        .clip(LeafShapes.IconSquircle)
                        .background(squircleColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        goalIcon(state.icon) ?: Icons.Filled.Savings,
                        contentDescription = null,
                        tint = squircleInk,
                        modifier = Modifier.size(28.dp).rotate(4f),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        money(state.savedAmount),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.textPrimary,
                    )
                    Text(
                        "of ${money(state.targetAmount)} goal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
                if (state.isShared) SharedBadge()
            }
            Spacer(Modifier.height(14.dp))
            HeartTippedProgress(progress = state.progress, fillColor = progressColor)
            state.targetDate?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Target date: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }
            if (state.reached) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Celebration, contentDescription = null, tint = progressColor)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Goal reached! 🎉",
                        style = MaterialTheme.typography.titleMedium,
                        color = progressColor,
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
    val colors = LocalPlayfulColors.current
    val ownerColor = if (row.isMine) colors.accent else colors.deepPlum

    PlayfulCard(
        modifier = Modifier.fillMaxWidth(),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.Card,
        contentPadding = 14.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeartBullet(ownerColor, sizeDp = 16)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(money(row.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(
                    "${row.byLabel} · ${formatShortDate(row.date)}" + (row.note?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            if (row.isMine) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Edit or delete", tint = colors.textSecondary)
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
                    label = { Text("Amount (${currencyGlyph()})") },
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
