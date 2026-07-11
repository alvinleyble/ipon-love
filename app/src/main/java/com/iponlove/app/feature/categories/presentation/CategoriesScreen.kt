package com.iponlove.app.feature.categories.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.CapReachedSheet
import com.iponlove.app.core.ui.EntityColorPicker
import com.iponlove.app.core.ui.SharedBadge
import com.iponlove.app.core.ui.icons.CATEGORY_ICONS
import com.iponlove.app.core.ui.icons.IconPicker
import com.iponlove.app.core.ui.parseHexColor
import com.iponlove.app.core.util.movedTo
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.model.CategoryType

/**
 * Chrome-less Categories body — no Scaffold/TopAppBar/FAB. The Manage host provides the single
 * scaffold + page-aware FAB (which calls [CategoriesViewModel.startCreate]); this renders only the
 * filter row + list + editor dialog.
 *
 * The list supports drag-handle reordering (item 9b): [localOrder] is a composable-owned working
 * copy of [CategoriesUiState.categories] that mutates live during a drag (same "composable-owned
 * draft" approach as the notes editor, V1.5 slice 1B) and is only persisted via
 * [CategoriesViewModel.reorder] when the drag ends, so intermediate positions never round-trip
 * through Room. It resyncs from the ViewModel whenever nothing is being dragged, so external
 * changes (edits, archive, another device's sync) still show up.
 */
@Composable
fun CategoriesBody(
    modifier: Modifier = Modifier,
    onOpenPremium: () -> Unit = {},
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var localOrder by remember { mutableStateOf(state.categories) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val rowPitchPx = with(LocalDensity.current) { 88.dp.toPx() }
    LaunchedEffect(state.categories) {
        if (draggingId == null) localOrder = state.categories
    }

    Column(modifier = modifier) {
        FilterRow(
            selected = state.filter,
            onSelect = viewModel::setFilter,
            showArchived = state.showArchived,
            canShowArchivedToggle = state.hasArchived || state.showArchived,
            onToggleArchived = viewModel::setShowArchived,
        )
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                localOrder.isEmpty() ->
                    EmptyState(state.filter, Modifier.align(Alignment.Center))

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    itemsIndexed(localOrder, key = { _, category -> category.id }) { _, category ->
                        val dragging = draggingId == category.id
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (dragging) 1f else 0f)
                                .graphicsLayer { translationY = if (dragging) dragAccum else 0f },
                        ) {
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = "Reorder ${category.name}",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .pointerInput(category.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { draggingId = category.id; dragAccum = 0f },
                                            onDragEnd = {
                                                draggingId = null
                                                dragAccum = 0f
                                                viewModel.reorder(localOrder.map { it.id })
                                            },
                                            onDragCancel = { draggingId = null; dragAccum = 0f },
                                            onDrag = { change, drag ->
                                                change.consume()
                                                dragAccum += drag.y
                                                val from = localOrder.indexOfFirst { it.id == category.id }
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
                                CategoryCard(
                                    category = category,
                                    isPaired = state.isPaired,
                                    onClick = { viewModel.startEdit(category) },
                                    onToggleArchive = { viewModel.archive(category.id, !category.isArchived) },
                                    onShare = { viewModel.share(category.id) },
                                    onUnshare = { viewModel.unshare(category.id) },
                                    onDelete = { viewModel.delete(category.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        CategoryEditorDialog(
            editor = editor,
            onNameChange = viewModel::onNameChange,
            onTypeChange = viewModel::onTypeChange,
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
private fun FilterRow(
    selected: CategoryFilter,
    onSelect: (CategoryFilter) -> Unit,
    showArchived: Boolean,
    canShowArchivedToggle: Boolean,
    onToggleArchived: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryFilter.entries.forEach { filter ->
            IponFilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filter.label()) },
            )
        }
        if (canShowArchivedToggle) {
            Spacer(Modifier.weight(1f))
            IponFilterChip(
                selected = showArchived,
                onClick = { onToggleArchived(!showArchived) },
                label = { Text("Archived") },
            )
        }
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    isPaired: Boolean,
    onClick: () -> Unit,
    onToggleArchive: () -> Unit,
    onShare: () -> Unit,
    onUnshare: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val iconColor = parseHexColor(category.color) ?: MaterialTheme.colorScheme.primary
    val containerColor = if (category.color != null) iconColor.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (category.color != null) iconColor
    else MaterialTheme.colorScheme.onSecondaryContainer
    val imageVector = category.icon?.let { CATEGORY_ICONS[it] }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (category.isArchived) 0.5f else 1f)
            .clickable(onClick = onClick),
    ) {
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
                            text = category.name.take(1).uppercase(),
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
                        category.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (category.isShared) {
                        Spacer(Modifier.size(6.dp))
                        SharedBadge()
                    }
                }
                Text(
                    text = if (category.isArchived) "${category.type.label()} · Archived"
                    else category.type.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    // Share/un-share is a couple-only capability (ADR-0018), shown only when paired.
                    // Only the creator may un-share (v1.6.5 Item 20); hide "Make personal" for the
                    // non-creator partner, whose revert would wedge sync.
                    if (isPaired) {
                        if (!category.isShared) {
                            DropdownMenuItem(
                                text = { Text("Share with partner") },
                                onClick = { menuOpen = false; onShare() },
                            )
                        } else if (category.isCreator) {
                            DropdownMenuItem(
                                text = { Text("Make personal") },
                                onClick = { menuOpen = false; onUnshare() },
                            )
                        }
                    }
                    DropdownMenuItem(
                        text = { Text(if (category.isArchived) "Unarchive" else "Archive") },
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

@Composable
private fun CategoryEditorDialog(
    editor: CategoryEditorState,
    onNameChange: (String) -> Unit,
    onTypeChange: (CategoryType) -> Unit,
    onIconChange: (String?) -> Unit,
    onColorChange: (String?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val tintColor = parseHexColor(editor.color) ?: MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (editor.isEditing) "Edit category" else "New category") },
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryType.entries.forEach { type ->
                        IponFilterChip(
                            selected = type == editor.type,
                            onClick = { onTypeChange(type) },
                            label = { Text(type.label()) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                IconPicker(
                    icons = CATEGORY_ICONS,
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
private fun EmptyState(filter: CategoryFilter, modifier: Modifier = Modifier) {
    val message = when (filter) {
        CategoryFilter.ALL -> "No categories yet"
        CategoryFilter.INCOME -> "No income categories yet"
        CategoryFilter.EXPENSE -> "No expense categories yet"
    }
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Tap + to add one.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun CategoryType.label(): String = when (this) {
    CategoryType.INCOME -> "Income"
    CategoryType.EXPENSE -> "Expense"
}

private fun CategoryFilter.label(): String = when (this) {
    CategoryFilter.ALL -> "All"
    CategoryFilter.INCOME -> "Income"
    CategoryFilter.EXPENSE -> "Expense"
}
