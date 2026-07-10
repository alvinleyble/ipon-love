package com.iponlove.app.feature.accounts.presentation

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.iponlove.app.core.ui.IponFilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.CapReachedSheet
import com.iponlove.app.core.ui.EntityColorPicker
import com.iponlove.app.core.ui.SharedBadge
import com.iponlove.app.core.ui.SummaryHeader
import com.iponlove.app.core.ui.formatPhp
import com.iponlove.app.core.ui.icons.ACCOUNT_ICONS
import com.iponlove.app.core.ui.icons.IconPicker
import com.iponlove.app.core.ui.parseHexColor
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
    onOpenPremium: () -> Unit = {},
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

    Column(modifier = modifier) {
        // Personal accounts only (own accounts, own or shared-by-me) — never wire this into
        // the Combined view (ADR-0011).
        if (!state.isLoading && state.accounts.isNotEmpty()) {
            val netAssets = state.balances.values.fold(BigDecimal.ZERO, BigDecimal::add)
            SummaryHeader(label = "Net assets", amount = netAssets)
        }

        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            when {
                state.isLoading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                localOrder.isEmpty() ->
                    EmptyState(Modifier.align(Alignment.Center))

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(localOrder, key = { _, account -> account.id }) { _, account ->
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                    balance = state.balances[account.id] ?: account.openingBalance,
                                    isPaired = state.isPaired,
                                    onClick = { viewModel.startEdit(account) },
                                    onToggleArchive = { viewModel.archive(account.id, !account.isArchived) },
                                    onShare = { viewModel.share(account.id) },
                                    onUnshare = { viewModel.unshare(account.id) },
                                    onDelete = { viewModel.delete(account.id) },
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
            onBalanceChange = viewModel::onOpeningBalanceChange,
            onIconChange = viewModel::onIconChange,
            onColorChange = viewModel::onColorChange,
            onSave = viewModel::save,
            onCancel = viewModel::cancelEdit,
        )
    }

    state.upsell?.let { prompt ->
        CapReachedSheet(
            prompt = prompt,
            onDismiss = viewModel::dismissUpsell,
            onUpgrade = { viewModel.onUpsellUpgrade(); onOpenPremium() },
        )
    }
}

@Composable
private fun AccountCard(
    account: Account,
    balance: BigDecimal,
    isPaired: Boolean,
    onClick: () -> Unit,
    onToggleArchive: () -> Unit,
    onShare: () -> Unit,
    onUnshare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val iconColor = parseHexColor(account.color) ?: MaterialTheme.colorScheme.primary
    val containerColor = if (account.color != null) iconColor.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.primaryContainer
    val contentColor = if (account.color != null) iconColor
    else MaterialTheme.colorScheme.onPrimaryContainer
    val imageVector = account.icon?.let { ACCOUNT_ICONS[it] }

    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(44.dp).clip(CircleShape),
                color = containerColor,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (imageVector != null) {
                        Icon(
                            imageVector = imageVector,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(24.dp),
                        )
                    } else {
                        Text(
                            text = account.name.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = contentColor,
                        )
                    }
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        account.name,
                        style = MaterialTheme.typography.titleMedium,
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
                    text = account.type.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatPhp(balance),
                style = MaterialTheme.typography.titleMedium,
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // Share/un-share is a couple-only capability (ADR-0018), shown only when paired.
                    if (isPaired) {
                        if (account.isShared) {
                            DropdownMenuItem(
                                text = { Text("Make personal") },
                                onClick = { menuOpen = false; onUnshare() },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Share with partner") },
                                onClick = { menuOpen = false; onShare() },
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
    AlertDialog(
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
                OutlinedTextField(
                    value = editor.openingBalanceText,
                    onValueChange = onBalanceChange,
                    label = { Text("Opening balance (₱)") },
                    singleLine = true,
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
