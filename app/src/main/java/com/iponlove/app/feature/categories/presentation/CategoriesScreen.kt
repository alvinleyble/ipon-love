package com.iponlove.app.feature.categories.presentation

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.iponlove.app.core.ui.IponFilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.EntityColorPicker
import com.iponlove.app.core.ui.SharedBadge
import com.iponlove.app.core.ui.icons.CATEGORY_ICONS
import com.iponlove.app.core.ui.icons.IconPicker
import com.iponlove.app.core.ui.parseHexColor
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.model.CategoryType

@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    CategoriesContent(
        state = state,
        onFilterChange = viewModel::setFilter,
        onAdd = viewModel::startCreate,
        onEdit = viewModel::startEdit,
        onToggleArchive = { category -> viewModel.archive(category.id, !category.isArchived) },
        onShare = { category -> viewModel.share(category.id) },
        onUnshare = { category -> viewModel.unshare(category.id) },
        onDelete = { category -> viewModel.delete(category.id) },
        onNameChange = viewModel::onNameChange,
        onTypeChange = viewModel::onTypeChange,
        onIconChange = viewModel::onIconChange,
        onColorChange = viewModel::onColorChange,
        onSave = viewModel::save,
        onCancel = viewModel::cancelEdit,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoriesContent(
    state: CategoriesUiState,
    onFilterChange: (CategoryFilter) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Category) -> Unit,
    onToggleArchive: (Category) -> Unit,
    onShare: (Category) -> Unit,
    onUnshare: (Category) -> Unit,
    onDelete: (Category) -> Unit,
    onNameChange: (String) -> Unit,
    onTypeChange: (CategoryType) -> Unit,
    onIconChange: (String?) -> Unit,
    onColorChange: (String?) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Categories") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "Add category")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            FilterRow(selected = state.filter, onSelect = onFilterChange)
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isLoading ->
                        CircularProgressIndicator(Modifier.align(Alignment.Center))

                    state.categories.isEmpty() ->
                        EmptyState(state.filter, Modifier.align(Alignment.Center))

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.categories, key = { it.id }) { category ->
                            CategoryCard(
                                category = category,
                                isPaired = state.isPaired,
                                onClick = { onEdit(category) },
                                onToggleArchive = { onToggleArchive(category) },
                                onShare = { onShare(category) },
                                onUnshare = { onUnshare(category) },
                                onDelete = { onDelete(category) },
                            )
                        }
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        CategoryEditorDialog(
            editor = editor,
            onNameChange = onNameChange,
            onTypeChange = onTypeChange,
            onIconChange = onIconChange,
            onColorChange = onColorChange,
            onSave = onSave,
            onCancel = onCancel,
        )
    }
}

@Composable
private fun FilterRow(selected: CategoryFilter, onSelect: (CategoryFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CategoryFilter.entries.forEach { filter ->
            IponFilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(filter.label()) },
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
                    Text(category.name, style = MaterialTheme.typography.titleMedium)
                    if (category.isShared) {
                        Spacer(Modifier.size(6.dp))
                        SharedBadge()
                    }
                }
                Text(
                    text = category.type.label(),
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
                    if (isPaired) {
                        if (category.isShared) {
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
