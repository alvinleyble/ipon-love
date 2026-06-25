package com.iponlove.app.feature.recurring.presentation

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.iponlove.app.core.ui.IponFilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import com.iponlove.app.feature.recurring.domain.model.RecurringFrequency
import com.iponlove.app.feature.recurring.domain.usecase.RecurringError
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val IncomeColor = Color(0xFF2E7D32)
private val DATE_LABEL: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    viewModel: RecurringViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recurring") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.canAdd) {
                FloatingActionButton(onClick = viewModel::startCreate) {
                    Icon(Icons.Filled.Add, contentDescription = "Add recurring rule")
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
                        title = "Set up an account and category first",
                        body = "Recurring rules need an account to charge and a category to file under.",
                        modifier = Modifier.align(Alignment.Center),
                    )

                state.items.isEmpty() ->
                    EmptyState(
                        title = "No recurring rules yet",
                        body = "Tap + to automate a salary, bill, or subscription.",
                        modifier = Modifier.align(Alignment.Center),
                    )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.items, key = { it.id }) { item ->
                        RecurringRow(
                            item = item,
                            onClick = { viewModel.startEdit(item.id) },
                            onDelete = { viewModel.delete(item.id) },
                        )
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        RecurringEditorDialog(
            editor = editor,
            state = state,
            onAmountChange = viewModel::onAmountChange,
            onAccountChange = viewModel::onAccountChange,
            onCategoryChange = viewModel::onCategoryChange,
            onFrequencyChange = viewModel::onFrequencyChange,
            onIntervalChange = viewModel::onIntervalChange,
            onStartDateChange = viewModel::onStartDateChange,
            onEndDateChange = viewModel::onEndDateChange,
            onNoteChange = viewModel::onNoteChange,
            onSave = viewModel::save,
            onCancel = viewModel::cancelEdit,
        )
    }
}

@Composable
private fun RecurringRow(
    item: RecurringRuleListItem,
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
                    text = item.scheduleLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = item.nextLabel,
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
private fun RecurringEditorDialog(
    editor: RecurringEditorState,
    state: RecurringUiState,
    onAmountChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onFrequencyChange: (RecurringFrequency) -> Unit,
    onIntervalChange: (String) -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate?) -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val accountOptions = state.accounts.map { PickerOption(it.id, it.name) }
    val categoryOptions = state.categories.map { PickerOption(it.id, it.name) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (editor.isEditing) "Edit recurring rule" else "New recurring rule") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = editor.amountText,
                    onValueChange = onAmountChange,
                    label = { Text("Amount (₱)") },
                    singleLine = true,
                    isError = RecurringError.AMOUNT_NOT_POSITIVE in editor.errors,
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
                ChipRow(
                    label = "Category",
                    options = categoryOptions,
                    selectedId = editor.categoryId,
                    onSelect = onCategoryChange,
                )
                Spacer(Modifier.height(12.dp))
                ChipRow(
                    label = "Repeats",
                    options = RecurringFrequency.entries.map { PickerOption(it.name, it.label()) },
                    selectedId = editor.frequency.name,
                    onSelect = { onFrequencyChange(RecurringFrequency.valueOf(it)) },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = editor.intervalText,
                    onValueChange = onIntervalChange,
                    label = { Text("Every N ${editor.frequency.unit()}") },
                    singleLine = true,
                    isError = RecurringError.INTERVAL_INVALID in editor.errors,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                DateField(
                    label = "Starts",
                    date = editor.startDate,
                    onPick = onStartDateChange,
                )
                Spacer(Modifier.height(12.dp))
                DateField(
                    label = "Ends",
                    date = editor.endDate,
                    placeholder = "No end date",
                    onPick = onEndDateChange,
                    onClear = { onEndDateChange(null) },
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = editor.note,
                    onValueChange = onNoteChange,
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    IponFilterChip(
                        selected = option.id == selectedId,
                        onClick = { onSelect(option.id) },
                        label = { Text(option.label) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    label: String,
    date: LocalDate?,
    onPick: (LocalDate) -> Unit,
    placeholder: String = "Pick a date",
    onClear: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { open = true }) {
                Text(date?.format(DATE_LABEL) ?: placeholder)
            }
            if (onClear != null && date != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
    }

    if (open) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date?.toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onPick(it.toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
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

// DatePicker speaks UTC-midnight millis; convert through UTC so the calendar day is exact.
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@Composable
private fun RecurringRuleListItem.signedAmount(): String {
    val prefix = if (type == TransactionType.INCOME) "+" else "−"
    return prefix + formatPhp(amount)
}

@Composable
private fun RecurringRuleListItem.amountColor(): Color =
    if (type == TransactionType.INCOME) IncomeColor else MaterialTheme.colorScheme.error

private fun RecurringFrequency.label(): String = when (this) {
    RecurringFrequency.DAILY -> "Daily"
    RecurringFrequency.WEEKLY -> "Weekly"
    RecurringFrequency.MONTHLY -> "Monthly"
}

private fun RecurringFrequency.unit(): String = when (this) {
    RecurringFrequency.DAILY -> "days"
    RecurringFrequency.WEEKLY -> "weeks"
    RecurringFrequency.MONTHLY -> "months"
}

private fun RecurringError.message(): String = when (this) {
    RecurringError.AMOUNT_NOT_POSITIVE -> "Enter an amount greater than zero"
    RecurringError.ACCOUNT_REQUIRED -> "Choose an account"
    RecurringError.CATEGORY_REQUIRED -> "Choose a category"
    RecurringError.INTERVAL_INVALID -> "Repeat interval must be at least 1"
    RecurringError.END_BEFORE_START -> "End date must be on or after the start date"
}
