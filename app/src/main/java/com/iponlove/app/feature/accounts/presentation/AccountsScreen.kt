package com.iponlove.app.feature.accounts.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.iponlove.app.core.ui.IponFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.CapReachedSheet
import com.iponlove.app.core.ui.EntityColorPicker
import com.iponlove.app.core.ui.HeartBullet
import com.iponlove.app.core.ui.PlayfulCard
import com.iponlove.app.core.ui.PlayfulChip
import com.iponlove.app.core.ui.PlayfulDialog
import com.iponlove.app.core.ui.PlayfulGradientCard
import com.iponlove.app.core.ui.PlayfulSurface
import com.iponlove.app.core.ui.SharedBadge
import com.iponlove.app.core.ui.currencyGlyph
import com.iponlove.app.core.ui.money
import com.iponlove.app.core.ui.icons.ACCOUNT_ICONS
import com.iponlove.app.core.ui.icons.IconPicker
import com.iponlove.app.core.ui.parseHexColor
import com.iponlove.app.core.ui.playfulBackground
import com.iponlove.app.core.ui.theme.LeafShapes
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.core.util.movedTo
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.model.AccountType
import java.math.BigDecimal

/**
 * Chrome-less Accounts body — no Scaffold/TopAppBar/FAB. The Manage host
 * ([feature/manage/presentation/ManageScreen.kt]) provides the single scaffold + page-aware FAB
 * (which calls [AccountsViewModel.startCreate]); this renders only the list + editor dialog.
 *
 * The list supports drag-handle reordering (item 9b): [localOrder] is a composable-owned working
 * copy of [AccountsUiState.accounts] that mutates live during a drag (same "composable-owned
 * draft" approach as the notes editor, V1.5 slice 1B) and is only persisted via
 * [AccountsViewModel.reorder] when the drag ends. It resyncs from the ViewModel whenever nothing
 * is being dragged, so external changes (edits, archive, another device's sync) still show up.
 */
@Composable
fun AccountsBody(
    modifier: Modifier = Modifier,
    onOpenPremium: (source: String) -> Unit = {},
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var localOrder by remember { mutableStateOf(state.accounts) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val rowPitchPx = with(LocalDensity.current) { 88.dp.toPx() }
    LaunchedEffect(state.accounts) {
        if (draggingId == null) localOrder = state.accounts
    }

    Column(modifier = modifier.playfulBackground()) {
        // Personal accounts only (own accounts, own or shared-by-me) — never wire this into
        // the Combined view (ADR-0011). Net assets covers active accounts only (see ViewModel).
        if (!state.isLoading && state.accounts.isNotEmpty()) {
            AccountsHero(
                netAssets = state.netAssets,
                accountCount = state.accounts.count { !it.isArchived },
                sharedCount = state.accounts.count { it.isShared && !it.isArchived },
            )
        }

        if (!state.isLoading && (state.hasArchived || state.showArchived)) {
            ArchivedToggleRow(
                showArchived = state.showArchived,
                onToggle = viewModel::setShowArchived,
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            when {
                state.isLoading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                localOrder.isEmpty() ->
                    EmptyState(Modifier.align(Alignment.Center))

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(localOrder, key = { _, account -> account.id }) { index, account ->
                        val dragging = draggingId == account.id
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer { translationY = if (dragging) dragAccum else 0f },
                        ) {
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = "Reorder ${account.name}",
                                tint = LocalPlayfulColors.current.textTertiary,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .pointerInput(account.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { draggingId = account.id; dragAccum = 0f },
                                            onDragEnd = {
                                                draggingId = null
                                                dragAccum = 0f
                                                viewModel.reorder(localOrder.map { it.id })
                                            },
                                            onDragCancel = { draggingId = null; dragAccum = 0f },
                                            onDrag = { change, drag ->
                                                change.consume()
                                                dragAccum += drag.y
                                                val from = localOrder.indexOfFirst { it.id == account.id }
                                                if (from < 0) return@detectDragGesturesAfterLongPress
                                                when {
                                                    dragAccum > rowPitchPx / 2 && from < localOrder.lastIndex -> {
                                                        localOrder = localOrder.movedTo(from, from + 1)
                                                        dragAccum -= rowPitchPx
                                                    }
                                                    dragAccum < -rowPitchPx / 2 && from > 0 -> {
                                                        localOrder = localOrder.movedTo(from, from - 1)
                                                        dragAccum += rowPitchPx
                                                    }
                                                }
                                            },
                                        )
                                    },
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                AccountCard(
                                    account = account,
                                    index = index,
                                    balance = state.balances[account.id] ?: account.openingBalance,
                                    isPaired = state.isPaired,
                                    onClick = { viewModel.startEdit(account) },
                                    onToggleArchive = { viewModel.archive(account.id, !account.isArchived) },
                                    onShare = { viewModel.share(account.id) },
                                    onUnshare = { viewModel.unshare(account.id) },
                                    onDelete = { viewModel.requestDelete(account) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        AccountEditorDialog(
            editor = editor,
            onNameChange = viewModel::onNameChange,
            onTypeChange = viewModel::onTypeChange,
            onBalanceChange = viewModel::onBalanceChange,
            onIconChange = viewModel::onIconChange,
            onColorChange = viewModel::onColorChange,
            onSave = viewModel::save,
            onCancel = viewModel::cancelEdit,
        )
    }

    state.pendingDelete?.let { pending ->
        DeleteAccountDialog(
            pending = pending,
            onArchiveInstead = viewModel::archiveInstead,
            onDeleteAnyway = viewModel::confirmDelete,
            onCancel = viewModel::cancelDelete,
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

/**
 * Adaptive delete-confirm (v1.6.7 Item 5). Deleting is permanent (soft-delete, no un-delete UI),
 * so it's always confirmed. When the account has transactions the dialog shows the exact count
 * (either leg, incl. transfers) and steers to the non-destructive Archive (which keeps the account
 * out of the picker but preserves its balance + the rows' labels); the empty case is a plain
 * confirm. Archive itself stays a frictionless one-tap elsewhere — no dialog there.
 */
@Composable
private fun DeleteAccountDialog(
    pending: PendingAccountDelete,
    onArchiveInstead: () -> Unit,
    onDeleteAnyway: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    val count = pending.transactionCount
    val hasTransactions = count > 0
    val noun = if (count == 1) "transaction" else "transactions"
    PlayfulDialog(
        onDismissRequest = onCancel,
        title = { Text("Delete “${pending.name}”?") },
        text = {
            Text(
                if (hasTransactions) {
                    "Used by $count $noun (incl. transfers). Deleting removes it from your balance. " +
                        "Archive instead to keep everything."
                } else {
                    "This can't be undone."
                },
            )
        },
        confirmButton = {
            if (hasTransactions) {
                Column(horizontalAlignment = Alignment.End) {
                    Button(onClick = onArchiveInstead) { Text("Archive instead") }
                    TextButton(onClick = onDeleteAnyway) {
                        Text("Delete anyway", color = colors.semantic.negative)
                    }
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            } else {
                TextButton(onClick = onDeleteAnyway) {
                    Text("Delete", color = colors.semantic.negative)
                }
            }
        },
        dismissButton = if (hasTransactions) {
            null
        } else {
            { TextButton(onClick = onCancel) { Text("Cancel") } }
        },
    )
}

@Composable
private fun ArchivedToggleRow(showArchived: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        PlayfulChip(
            label = "Show archived",
            selected = showArchived,
            onClick = { onToggle(!showArchived) },
        )
    }
}

@Composable
private fun AccountsHero(
    netAssets: BigDecimal,
    accountCount: Int,
    sharedCount: Int,
) {
    val colors = LocalPlayfulColors.current
    PlayfulGradientCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        shape = LeafShapes.leaf(34.dp, 14.dp),
        tiltDegrees = -0.6f,
        contentPadding = 18.dp,
    ) {
        // Oversized translucent heart accent, clipped by the card's own leaf-squircle shape.
        HeartBullet(
            color = colors.onAccent.copy(alpha = 0.14f),
            sizeDp = 96,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 20.dp, y = 20.dp)
                .rotate(-10f),
        )
        Column {
            Text(
                text = "Net assets",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onAccent.copy(alpha = 0.85f),
            )
            Text(
                text = money(netAssets),
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-1).sp,
                    color = colors.onAccent,
                ),
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = "$accountCount accounts · $sharedCount shared",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onAccent.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AccountCard(
    account: Account,
    index: Int,
    balance: BigDecimal,
    isPaired: Boolean,
    onClick: () -> Unit,
    onToggleArchive: () -> Unit,
    onShare: () -> Unit,
    onUnshare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = LocalPlayfulColors.current
    val squircleColor = parseHexColor(account.color) ?: colors.accent
    val squircleInk = if (account.color != null) Color.White else colors.onAccent
    val imageVector = account.icon?.let { ACCOUNT_ICONS[it] }

    PlayfulCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (account.isArchived) 0.5f else 1f)
            .clickable(onClick = onClick),
        surface = PlayfulSurface.Glass,
        shape = LeafShapes.leafFor(index, 22.dp, 9.dp),
        contentPadding = 14.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(42.dp).clip(LeafShapes.IconSquircle).background(squircleColor),
                contentAlignment = Alignment.Center,
            ) {
                if (imageVector != null) {
                    Icon(
                        imageVector = imageVector,
                        contentDescription = null,
                        tint = squircleInk,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        text = account.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = squircleInk,
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (account.isShared) {
                        Spacer(Modifier.size(6.dp))
                        SharedBadge()
                    }
                }
                Text(
                    text = if (account.isArchived) "${account.type.label()} · Archived"
                    else account.type.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Text(
                text = money(balance),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (balance.signum() < 0) colors.semantic.negative else colors.textPrimary,
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options", tint = colors.textSecondary)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // Share/un-share is a couple-only capability (ADR-0018), shown only when paired.
                    // Only the creator may un-share (v1.6.5 Item 20) — a non-creator's revert would
                    // stamp the partner's user_id onto the row and wedge sync, so hide it for them.
                    if (isPaired) {
                        if (!account.isShared) {
                            DropdownMenuItem(
                                text = { Text("Share with partner") },
                                onClick = { menuOpen = false; onShare() },
                            )
                        } else if (account.isCreator) {
                            DropdownMenuItem(
                                text = { Text("Make personal") },
                                onClick = { menuOpen = false; onUnshare() },
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text(if (account.isArchived) "Unarchive" else "Archive") },
                        onClick = { menuOpen = false; onToggleArchive() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountEditorDialog(
    editor: AccountEditorState,
    onNameChange: (String) -> Unit,
    onTypeChange: (AccountType) -> Unit,
    onBalanceChange: (String) -> Unit,
    onIconChange: (String?) -> Unit,
    onColorChange: (String?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val tintColor = parseHexColor(editor.color) ?: MaterialTheme.colorScheme.primary
    PlayfulDialog(
        onDismissRequest = onCancel,
        title = { Text(if (editor.isEditing) "Edit account" else "New account") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    isError = editor.nameError,
                    supportingText = if (editor.nameError) {
                        { Text("Name is required") }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Type", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountType.entries.forEach { type ->
                        IponFilterChip(
                            selected = type == editor.type,
                            onClick = { onTypeChange(type) },
                            label = { Text(type.label()) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // On create, or on an account with an empty ledger, this is the starting balance.
                // Once an account has real activity, it's a target — Save records a marked
                // correction row for the difference instead of rewriting opening_balance
                // (ADR-0057). Archived accounts are frozen: the field is shown but not editable.
                val archived = editor.source?.isArchived == true
                OutlinedTextField(
                    value = editor.balanceText,
                    onValueChange = onBalanceChange,
                    label = { Text("Balance (${currencyGlyph()})") },
                    singleLine = true,
                    readOnly = archived,
                    enabled = !archived,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                IconPicker(
                    icons = ACCOUNT_ICONS,
                    selectedKey = editor.icon,
                    tintColor = tintColor,
                    onSelect = { key ->
                        if (key == editor.icon) onIconChange(null) else onIconChange(key)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                EntityColorPicker(
                    selectedHex = editor.color,
                    onSelect = { hex ->
                        if (hex == editor.color) onColorChange(null) else onColorChange(hex)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No accounts yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap + to add your first account.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun AccountType.label(): String = when (this) {
    AccountType.CASH -> "Cash"
    AccountType.CARD -> "Card"
    AccountType.BANK -> "Bank"
    AccountType.EWALLET -> "E-wallet"
}
