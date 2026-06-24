package com.iponlove.app.feature.transactions.presentation

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.formatPhp
import com.iponlove.app.core.ui.formatShortDate
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.TransactionError

private val IncomeColor = Color(0xFF2E7D32)

@Composable
fun TransactionsScreen(viewModel: TransactionsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    TransactionsContent(
        state = state,
        onAdd = viewModel::startCreate,
        onEdit = viewModel::startEdit,
        onDelete = viewModel::delete,
        onTypeChange = viewModel::onTypeChange,
        onAmountChange = viewModel::onAmountChange,
        onAccountChange = viewModel::onAccountChange,
        onToAccountChange = viewModel::onToAccountChange,
        onCategoryChange = viewModel::onCategoryChange,
        onNoteChange = viewModel::onNoteChange,
        onPrivateChange = viewModel::onPrivateChange,
        onSave = viewModel::save,
        onCancel = viewModel::cancelEdit,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionsContent(
    state: TransactionsUiState,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onAmountChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onToAccountChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onPrivateChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Records") }) },
        floatingActionButton = {
            if (state.canAdd) {
                FloatingActionButton(onClick = onAdd) {
                    Icon(Icons.Filled.Add, contentDescription = "Add transaction")
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                !state.canAdd ->
                    EmptyState(
                        title = "Create an account first",
                        body = "Transactions need an account. Add one on the Accounts tab.",
                        modifier = Modifier.align(Alignment.Center),
                    )

                state.items.isEmpty() ->
                    EmptyState(
                        title = "No transactions yet",
                        body = "Tap + to record income, an expense, or a transfer.",
                        modifier = Modifier.align(Alignment.Center),
                    )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
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

    state.editor?.let { editor ->
        TransactionEditorDialog(
            editor = editor,
            state = state,
            onTypeChange = onTypeChange,
            onAmountChange = onAmountChange,
            onAccountChange = onAccountChange,
            onToAccountChange = onToAccountChange,
            onCategoryChange = onCategoryChange,
            onNoteChange = onNoteChange,
            onPrivateChange = onPrivateChange,
            onSave = onSave,
            onCancel = onCancel,
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
private fun TransactionEditorDialog(
    editor: TransactionEditorState,
    state: TransactionsUiState,
    onTypeChange: (TransactionType) -> Unit,
    onAmountChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onToAccountChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onPrivateChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val accountOptions = state.accounts.map { PickerOption(it.id, it.name) }
    val categoryOptions = state.categories
        .filter { it.type == editor.type.matchingCategoryType() }
        .map { PickerOption(it.id, it.name) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (editor.isEditing) "Edit transaction" else "New transaction") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                ChipRow(
                    label = "Type",
                    options = TransactionType.entries.map { PickerOption(it.name, it.label()) },
                    selectedId = editor.type.name,
                    onSelect = { onTypeChange(TransactionType.valueOf(it)) },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = editor.amountText,
                    onValueChange = onAmountChange,
                    label = { Text("Amount (₱)") },
                    singleLine = true,
                    isError = TransactionError.AMOUNT_NOT_POSITIVE in editor.errors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                ChipRow(
                    label = "Account",
                    options = accountOptions,
                    selectedId = editor.accountId,
                    onSelect = onAccountChange,
                )
                Spacer(Modifier.height(12.dp))
                if (editor.type == TransactionType.TRANSFER) {
                    ChipRow(
                        label = "To account",
                        options = accountOptions.filter { it.id != editor.accountId },
                        selectedId = editor.toAccountId,
                        onSelect = onToAccountChange,
                    )
                } else {
                    ChipRow(
                        label = "Category",
                        options = categoryOptions,
                        selectedId = editor.categoryId,
                        onSelect = onCategoryChange,
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = editor.note,
                    onValueChange = onNoteChange,
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Private", modifier = Modifier.weight(1f))
                    Switch(checked = editor.isPrivate, onCheckedChange = onPrivateChange)
                }
                if (editor.errors.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    editor.errors.forEach { error ->
                        Text(
                            text = error.message(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave) { Text("Save") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

private data class PickerOption(val id: String, val label: String)

@Composable
private fun ChipRow(
    label: String,
    options: List<PickerOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        if (options.isEmpty()) {
            Text(
                text = "None available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                options.forEachIndexed { index, option ->
                    if (index > 0) Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = option.id == selectedId,
                        onClick = { onSelect(option.id) },
                        label = { Text(option.label) },
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
    return prefix + formatPhp(amount)
}

@Composable
private fun TransactionListItem.amountColor(): Color = when (type) {
    TransactionType.INCOME -> IncomeColor
    TransactionType.EXPENSE -> MaterialTheme.colorScheme.error
    TransactionType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun TransactionType.label(): String = when (this) {
    TransactionType.INCOME -> "Income"
    TransactionType.EXPENSE -> "Expense"
    TransactionType.TRANSFER -> "Transfer"
}

private fun TransactionType.matchingCategoryType(): CategoryType = when (this) {
    TransactionType.INCOME -> CategoryType.INCOME
    else -> CategoryType.EXPENSE
}

private fun TransactionError.message(): String = when (this) {
    TransactionError.AMOUNT_NOT_POSITIVE -> "Enter an amount greater than zero"
    TransactionError.ACCOUNT_REQUIRED -> "Choose an account"
    TransactionError.CATEGORY_REQUIRED -> "Choose a category"
    TransactionError.DESTINATION_REQUIRED -> "Choose a destination account"
    TransactionError.DESTINATION_SAME_AS_SOURCE -> "Destination must differ from the source"
}
