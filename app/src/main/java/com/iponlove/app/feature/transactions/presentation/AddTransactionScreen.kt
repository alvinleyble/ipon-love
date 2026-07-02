package com.iponlove.app.feature.transactions.presentation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.iponlove.app.core.ui.IponFilterChip
import com.iponlove.app.core.ui.formatShortDate
import com.iponlove.app.core.ui.icons.ACCOUNT_ICONS
import com.iponlove.app.core.ui.icons.CATEGORY_ICONS
import com.iponlove.app.core.ui.parseHexColor
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.TransactionError
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun AddTransactionScreen(
    onBack: () -> Unit,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    AddTransactionContent(
        state = state,
        onBack = onBack,
        onTypeChange = viewModel::onTypeChange,
        onAmountChange = viewModel::onAmountChange,
        onAccountChange = viewModel::onAccountChange,
        onToAccountChange = viewModel::onToAccountChange,
        onCategoryChange = viewModel::onCategoryChange,
        onNoteChange = viewModel::onNoteChange,
        onPrivateChange = viewModel::onPrivateChange,
        onPaidForPartnerChange = viewModel::onPaidForPartnerChange,
        onAmountOwedChange = viewModel::onAmountOwedChange,
        onDateChange = viewModel::onDateChange,
        onReceiptPicked = viewModel::onReceiptPicked,
        onRemoveReceipt = viewModel::onRemoveReceipt,
        onSave = { viewModel.save(onBack) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTransactionContent(
    state: AddTransactionUiState,
    onBack: () -> Unit,
    onTypeChange: (TransactionType) -> Unit,
    onAmountChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onToAccountChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onPrivateChange: (Boolean) -> Unit,
    onPaidForPartnerChange: (Boolean) -> Unit,
    onAmountOwedChange: (String) -> Unit,
    onDateChange: (Instant) -> Unit,
    onReceiptPicked: (Uri) -> Unit,
    onRemoveReceipt: () -> Unit,
    onSave: () -> Unit,
) {
    val editor = state.editor
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editor?.isEditing == true) "Edit transaction" else "New transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (editor != null) {
                        TextButton(onClick = onSave) { Text("Save") }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.missing -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text("This transaction no longer exists.")
            }

            editor == null -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator()
            }

            else -> EditorForm(
                editor = editor,
                state = state,
                modifier = Modifier.padding(padding),
                onTypeChange = onTypeChange,
                onAmountChange = onAmountChange,
                onAccountChange = onAccountChange,
                onToAccountChange = onToAccountChange,
                onCategoryChange = onCategoryChange,
                onNoteChange = onNoteChange,
                onPrivateChange = onPrivateChange,
                onPaidForPartnerChange = onPaidForPartnerChange,
                onAmountOwedChange = onAmountOwedChange,
                onDateChange = onDateChange,
                onReceiptPicked = onReceiptPicked,
                onRemoveReceipt = onRemoveReceipt,
            )
        }
    }
}

@Composable
private fun EditorForm(
    editor: TransactionEditorState,
    state: AddTransactionUiState,
    modifier: Modifier = Modifier,
    onTypeChange: (TransactionType) -> Unit,
    onAmountChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onToAccountChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onNoteChange: (String) -> Unit,
    onPrivateChange: (Boolean) -> Unit,
    onPaidForPartnerChange: (Boolean) -> Unit,
    onAmountOwedChange: (String) -> Unit,
    onDateChange: (Instant) -> Unit,
    onReceiptPicked: (Uri) -> Unit,
    onRemoveReceipt: () -> Unit,
) {
    val accountOptions = state.accounts.map {
        PickerOption(it.id, it.name, it.icon?.let { k -> ACCOUNT_ICONS[k] }, it.color)
    }
    // Filtered by the current type; the grid takes an already-filtered list so a search box is a
    // drop-in later (Slice 1 design note).
    val categoryOptions = state.categories
        .filter { it.type == editor.type.matchingCategoryType() }
        .map { PickerOption(it.id, it.name, it.icon?.let { k -> CATEGORY_ICONS[k] }, it.color) }

    // Spend touching a shared account is forced non-private (ADR-0018): hide the toggle then.
    val sharedAccountIds = state.accounts.filter { it.isShared }.map { it.id }.toSet()
    val touchesSharedAccount =
        editor.accountId in sharedAccountIds || editor.toAccountId in sharedAccountIds

    var showDatePicker by remember { mutableStateOf(false) }
    var showFullScreenReceipt by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? -> uri?.let(onReceiptPicked) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        FieldLabel("Type")
        ChipRow(
            options = TransactionType.entries.map { PickerOption(it.name, it.label()) },
            selectedId = editor.type.name,
            onSelect = { onTypeChange(TransactionType.valueOf(it)) },
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = editor.amountText,
            onValueChange = onAmountChange,
            label = { Text("Amount (₱)") },
            singleLine = true,
            isError = TransactionError.AMOUNT_NOT_POSITIVE in editor.errors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        FieldLabel("Account")
        ChipRow(options = accountOptions, selectedId = editor.accountId, onSelect = onAccountChange)
        Spacer(Modifier.height(16.dp))

        if (editor.type == TransactionType.TRANSFER) {
            FieldLabel("To account")
            ChipRow(
                options = accountOptions.filter { it.id != editor.accountId },
                selectedId = editor.toAccountId,
                onSelect = onToAccountChange,
            )
        } else {
            FieldLabel("Category")
            CategoryGrid(
                options = categoryOptions,
                selectedId = editor.categoryId,
                onSelect = onCategoryChange,
            )
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = editor.note,
            onValueChange = onNoteChange,
            label = { Text("Note (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Date", style = MaterialTheme.typography.labelLarge)
                Text(formatShortDate(editor.date), style = MaterialTheme.typography.bodyMedium)
            }
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = "Pick date",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!touchesSharedAccount) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Private", modifier = Modifier.weight(1f))
                Switch(checked = editor.isPrivate, onCheckedChange = onPrivateChange)
            }
        }

        if (state.canPayForPartner && editor.type == TransactionType.EXPENSE) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Paid for ${state.partnerName}", modifier = Modifier.weight(1f))
                Switch(checked = editor.paidForPartner, onCheckedChange = onPaidForPartnerChange)
            }
            if (editor.paidForPartner) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editor.amountOwedText,
                    onValueChange = onAmountOwedChange,
                    label = { Text("Amount owed (₱)") },
                    supportingText = {
                        Text("How much ${state.partnerName} owes you — defaults to the full amount.")
                    },
                    singleLine = true,
                    isError = editor.amountOwedError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        ReceiptRow(
            localPath = editor.attachmentLocalPath,
            url = editor.attachmentUrl,
            onPickReceipt = { galleryLauncher.launch("image/*") },
            onRemoveReceipt = onRemoveReceipt,
            onViewReceipt = { showFullScreenReceipt = true },
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

    if (showFullScreenReceipt) {
        val imageSource = editor.attachmentLocalPath ?: editor.attachmentUrl
        if (imageSource != null) {
            FullScreenReceiptDialog(imageSource, onDismiss = { showFullScreenReceipt = false })
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerStateFor(editor.date)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDateChange(Instant.ofEpochMilli(it)) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private data class PickerOption(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    val colorHex: String? = null,
)

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
}

/** Tap-to-select pills — used for type and accounts. No dropdown (Slice 1 design). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    options: List<PickerOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    if (options.isEmpty()) {
        Text(
            "None available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val selected = option.id == selectedId
            val accent = parseHexColor(option.colorHex) ?: MaterialTheme.colorScheme.primary
            IponFilterChip(
                selected = selected,
                onClick = { onSelect(option.id) },
                label = { Text(option.label) },
                leadingIcon = option.icon?.let { vector ->
                    {
                        Icon(
                            imageVector = vector,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
            )
        }
    }
}

/** Wrapping grid of icon+color tiles, tap-to-select. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryGrid(
    options: List<PickerOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    if (options.isEmpty()) {
        Text(
            "None available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        options.forEach { option ->
            CategoryTile(
                option = option,
                selected = option.id == selectedId,
                onClick = { onSelect(option.id) },
            )
        }
    }
}

@Composable
private fun CategoryTile(option: PickerOption, selected: Boolean, onClick: () -> Unit) {
    val accent = parseHexColor(option.colorHex) ?: MaterialTheme.colorScheme.primary
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.width(72.dp).clickable(onClick = onClick),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (selected) accent.copy(alpha = 0.18f) else Color.Transparent)
                .border(
                    BorderStroke(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    CircleShape,
                ),
        ) {
            if (option.icon != null) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = option.label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = option.label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ReceiptRow(
    localPath: String?,
    url: String?,
    onPickReceipt: () -> Unit,
    onRemoveReceipt: () -> Unit,
    onViewReceipt: () -> Unit,
) {
    val hasReceipt = localPath != null || url != null
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Receipt", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        if (hasReceipt) {
            val imageSource: Any = if (localPath != null) File(localPath) else url!!
            AsyncImage(
                model = imageSource,
                contentDescription = "Receipt thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clickable(onClick = onViewReceipt),
            )
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = onRemoveReceipt) {
                Icon(Icons.Filled.Close, contentDescription = "Remove receipt", tint = MaterialTheme.colorScheme.error)
            }
        } else {
            IconButton(onClick = onPickReceipt) {
                Icon(
                    Icons.Filled.AddPhotoAlternate,
                    contentDescription = "Attach receipt",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun FullScreenReceiptDialog(imageSource: Any, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize().clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageSource,
                contentDescription = "Receipt full size",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun rememberDatePickerStateFor(date: Instant) =
    androidx.compose.material3.rememberDatePickerState(
        // DatePicker speaks UTC-midnight millis; convert through UTC so the calendar day is exact.
        initialSelectedDateMillis = date.atZone(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )

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
    TransactionError.PRIVATE_ON_SHARED_ACCOUNT -> "A shared account's spend can't be private"
}
