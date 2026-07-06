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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.iponlove.app.core.ui.FullScreenImagePager
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.iponlove.app.core.ui.EntityChipRow
import com.iponlove.app.core.ui.EntityGrid
import com.iponlove.app.core.ui.EntityPickerOption
import com.iponlove.app.core.ui.StartTourOnFirstVisit
import com.iponlove.app.core.ui.coachMarkTarget
import com.iponlove.app.core.ui.formatShortDate
import com.iponlove.app.core.ui.icons.ACCOUNT_ICONS
import com.iponlove.app.core.ui.icons.CATEGORY_ICONS
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.presentation.TutorialTargets
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.transactions.domain.model.TransactionImage
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
    StartTourOnFirstVisit(TutorialTours.TRANSACTION_ENTRY, TutorialTours.TRANSACTION_ENTRY_COUPLE)
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
        onTransferFeeChange = viewModel::onTransferFeeChange,
        onDateChange = viewModel::onDateChange,
        onImagePicked = viewModel::onImagePicked,
        onRemoveImage = viewModel::onRemoveImage,
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
    onTransferFeeChange: (String) -> Unit,
    onDateChange: (Instant) -> Unit,
    onImagePicked: (Uri) -> Unit,
    onRemoveImage: (String) -> Unit,
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
                onTransferFeeChange = onTransferFeeChange,
                onDateChange = onDateChange,
                onImagePicked = onImagePicked,
                onRemoveImage = onRemoveImage,
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
    onTransferFeeChange: (String) -> Unit,
    onDateChange: (Instant) -> Unit,
    onImagePicked: (Uri) -> Unit,
    onRemoveImage: (String) -> Unit,
) {
    val accountOptions = state.accounts.map {
        EntityPickerOption(it.id, it.name, it.icon?.let { k -> ACCOUNT_ICONS[k] }, it.color)
    }
    // Filtered by the current type; the grid takes an already-filtered list so a search box is a
    // drop-in later (Slice 1 design note).
    val categoryOptions = state.categories
        .filter { it.type == editor.type.matchingCategoryType() }
        .map { EntityPickerOption(it.id, it.name, it.icon?.let { k -> CATEGORY_ICONS[k] }, it.color) }

    // Spend touching a shared account is forced non-private (ADR-0018): hide the toggle then.
    val sharedAccountIds = state.accounts.filter { it.isShared }.map { it.id }.toSet()
    val touchesSharedAccount =
        editor.accountId in sharedAccountIds || editor.toAccountId in sharedAccountIds

    var showDatePicker by remember { mutableStateOf(false) }
    var receiptViewerIndex by remember { mutableStateOf<Int?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? -> uri?.let(onImagePicked) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Column(Modifier.coachMarkTarget(TutorialTargets.TXN_TYPE)) {
            FieldLabel("Type")
            EntityChipRow(
                options = TransactionType.entries.map { EntityPickerOption(it.name, it.label()) },
                selectedId = editor.type.name,
                onSelect = { onTypeChange(TransactionType.valueOf(it)) },
            )
        }
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
        EntityChipRow(options = accountOptions, selectedId = editor.accountId, onSelect = onAccountChange)
        Spacer(Modifier.height(16.dp))

        if (editor.type == TransactionType.TRANSFER) {
            FieldLabel("To account")
            EntityChipRow(
                options = accountOptions.filter { it.id != editor.accountId },
                selectedId = editor.toAccountId,
                onSelect = onToAccountChange,
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = editor.transferFeeText,
                onValueChange = onTransferFeeChange,
                label = { Text("Transfer fee (₱, optional)") },
                supportingText = {
                    Text("Recorded as a separate expense under \"Transfer fees\".")
                },
                singleLine = true,
                isError = editor.transferFeeError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            FieldLabel("Category")
            EntityGrid(
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
            Text(
                // Pre-pairing framing (ADR-0038 dec. 6): a persistent inline hint that a future
                // partner won't see private entries — the disclaimer the raw ask called for, shown
                // durably here (not only as a one-off tour step) since Private may be toggled anytime.
                if (state.isPaired) {
                    "Hides this transaction from ${state.partnerName}'s combined view."
                } else {
                    "When you pair with a partner later, private transactions stay out of their " +
                        "combined view."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        ReceiptStrip(
            images = editor.images,
            onAddReceipt = { galleryLauncher.launch("image/*") },
            onRemoveReceipt = onRemoveImage,
            onViewReceipt = { index -> receiptViewerIndex = index },
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

    receiptViewerIndex?.let { startIndex ->
        val models: List<Any> = editor.images.mapNotNull { it.localPath?.let(::File) ?: it.url }
        if (models.isEmpty()) {
            receiptViewerIndex = null
        } else {
            FullScreenImagePager(
                models = models,
                startIndex = startIndex,
                contentDescription = "Receipt full size",
                onDismiss = { receiptViewerIndex = null },
            )
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

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
}

/**
 * Up to [TransactionImage.MAX] receipt thumbnails in a horizontally-scrollable strip, mirroring
 * the notes editor. Header carries the label, an `n/3` counter (red at the cap, hidden while
 * empty), and an add button that greys out once the cap is reached. Each thumbnail opens the
 * full-screen pager (by index) and has a corner remove button.
 */
@Composable
private fun ReceiptStrip(
    images: List<TransactionImage>,
    onAddReceipt: () -> Unit,
    onRemoveReceipt: (String) -> Unit,
    onViewReceipt: (Int) -> Unit,
) {
    val atMax = images.size >= TransactionImage.MAX
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Receipts", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (images.isNotEmpty()) {
                Text(
                    text = "${images.size}/${TransactionImage.MAX}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (atMax) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onAddReceipt, enabled = !atMax) {
                Icon(
                    Icons.Filled.AddPhotoAlternate,
                    contentDescription = "Attach receipt",
                    tint = if (atMax) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (images.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                itemsIndexed(images, key = { _, image -> image.id }) { index, image ->
                    val model: Any? = image.localPath?.let { File(it) } ?: image.url
                    Box {
                        AsyncImage(
                            model = model,
                            contentDescription = "Receipt thumbnail",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = model != null) { onViewReceipt(index) },
                        )
                        SmallFloatingActionButton(
                            onClick = { onRemoveReceipt(image.id) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(20.dp),
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove receipt",
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
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
