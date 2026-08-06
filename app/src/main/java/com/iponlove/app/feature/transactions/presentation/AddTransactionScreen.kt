package com.iponlove.app.feature.transactions.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.iponlove.app.core.ui.CapReachedSheet
import com.iponlove.app.core.ui.FullScreenImagePager
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.iponlove.app.core.ui.EntityChipRow
import com.iponlove.app.core.ui.EntityGrid
import com.iponlove.app.core.ui.EntityPickerOption
import com.iponlove.app.core.ui.StartTourOnFirstVisit
import com.iponlove.app.core.ui.coachMarkTarget
import com.iponlove.app.core.ui.currencyGlyph
import com.iponlove.app.core.ui.formatShortDate
import com.iponlove.app.core.ui.icons.ACCOUNT_ICONS
import com.iponlove.app.core.ui.icons.CATEGORY_ICONS
import com.iponlove.app.core.ui.money
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
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
    onOpenPremium: (source: String) -> Unit = {},
    autoLaunchScan: Boolean = false,
    viewModel: AddTransactionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    StartTourOnFirstVisit(TutorialTours.TRANSACTION_ENTRY, TutorialTours.TRANSACTION_ENTRY_COUPLE)

    // Receipt scan (v1.7.3 Item 2). The launchers live here rather than in the form so the
    // failed-read prompt and the retake path can reach the same camera launcher.
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success -> viewModel.onCaptureTaken(success) }

    val scanPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? -> uri?.let(viewModel::onScanImagePicked) }

    val launchCamera: (Uri) -> Unit = { uri -> cameraLauncher.launch(uri) }

    // API 26-28 only: the Pictures/Love, Ipon copy needs WRITE_EXTERNAL_STORAGE there (declared
    // with maxSdkVersion="28"). Asked once, just before the first capture and only while the
    // "Save scans to gallery" toggle is on, and the scan proceeds either way — the gallery copy is
    // a convenience, never a gate on scanning.
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { viewModel.onScanTap(launchCamera) }

    val startScan: () -> Unit = {
        if (state.scan.locked) {
            onOpenPremium(viewModel.onScanUpsellTap())
        } else if (state.scan.galleryCopyEnabled && needsLegacyGalleryPermission(context)) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.onScanTap(launchCamera)
        }
    }

    // Records' FAB-wheel 📷 action (Item 2 Slice 3) lands here wanting the camera open
    // immediately — same gating as the form's own "Scan receipt" button, just auto-fired once on
    // arrival instead of waiting for a tap. Keyed on Unit, not autoLaunchScan, so a config change
    // or recomposition never re-fires it mid-review.
    LaunchedEffect(Unit) {
        if (autoLaunchScan) startScan()
    }

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
        onAddPhotoTap = viewModel::onAddPhotoTap,
        onImagePicked = viewModel::onImagePicked,
        onRemoveImage = viewModel::onRemoveImage,
        onScanTap = startScan,
        onScanFromGalleryTap = {
            if (state.scan.locked) {
                onOpenPremium(viewModel.onScanUpsellTap())
            } else {
                viewModel.onScanFromGalleryTap { scanPickerLauncher.launch("image/*") }
            }
        },
        onSave = { viewModel.save(onBack) },
        onSaveAsDraft = { viewModel.saveAsDraft(onBack) },
    )

    state.scan.failure?.let { failure ->
        ScanFailedDialog(
            failure = failure,
            onRetake = { viewModel.onRetakeScan(launchCamera) },
            onEnterManually = viewModel::onDismissScanFailure,
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

/** True only on API 26-28 with the legacy storage permission still ungranted. */
private fun needsLegacyGalleryPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) != PackageManager.PERMISSION_GRANTED

/**
 * A failed read stays in the camera (ADR-0062 decision 8): retaking is both the actual fix for a
 * bad frame and one tap away. Falling through to a blank form was rejected — it makes typing the
 * path of least resistance exactly when a second photo would have worked.
 */
@Composable
private fun ScanFailedDialog(
    failure: ReceiptScanFailure,
    onRetake: () -> Unit,
    onEnterManually: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onEnterManually,
        title = { Text("Couldn't read that one") },
        text = {
            Text(
                when (failure) {
                    ReceiptScanFailure.NO_TEXT ->
                        "Try again with the whole receipt in frame, in brighter light."
                    ReceiptScanFailure.NOTHING_USABLE ->
                        "We found some text but nothing we could use. Make sure the total is in " +
                            "frame and the receipt is flat."
                },
            )
        },
        confirmButton = { TextButton(onClick = onRetake) { Text("Retake") } },
        dismissButton = { TextButton(onClick = onEnterManually) { Text("Enter manually") } },
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
    onAddPhotoTap: (launchPicker: () -> Unit) -> Unit,
    onImagePicked: (Uri) -> Unit,
    onRemoveImage: (String) -> Unit,
    onScanTap: () -> Unit,
    onScanFromGalleryTap: () -> Unit,
    onSave: () -> Unit,
    onSaveAsDraft: () -> Unit,
) {
    val editor = state.editor
    val colors = LocalPlayfulColors.current
    // Transparent chrome — the app-wide playfulBackground() from IponApp shows through.
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = colors.textPrimary,
                    navigationIconContentColor = colors.textPrimary,
                    actionIconContentColor = colors.textSecondary,
                ),
                title = { Text(if (editor?.isEditing == true) "Edit transaction" else "New transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (editor != null) {
                        // The three exits sit together: ← Cancel … Save as draft, Save. The draft
                        // exit is an ICON, not a second text button — "Save as draft" spelled out
                        // beside "Save" costs ~170dp of the action slot and truncates the title on
                        // a 360dp screen. It reads as secondary to Save, which is correct, and
                        // carries a long-press tooltip so the glyph is never the only explanation.
                        if (!editor.isEditing) {
                            SaveAsDraftAction(enabled = state.canSaveAsDraft, onClick = onSaveAsDraft)
                        }
                        TextButton(onClick = onSave) { Text("Save") }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.missing -> Box(Modifier.padding(padding).fillMaxSize(), Alignment.Center) {
                Text("This transaction no longer exists.", color = colors.textPrimary)
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
                onAddPhotoTap = onAddPhotoTap,
                onImagePicked = onImagePicked,
                onRemoveImage = onRemoveImage,
                onScanTap = onScanTap,
                onScanFromGalleryTap = onScanFromGalleryTap,
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
    onAddPhotoTap: (launchPicker: () -> Unit) -> Unit,
    onImagePicked: (Uri) -> Unit,
    onRemoveImage: (String) -> Unit,
    onScanTap: () -> Unit,
    onScanFromGalleryTap: () -> Unit,
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

    val colors = LocalPlayfulColors.current
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
        // Receipt scan sits above everything (ADR-0062 decision 3): the existing ReceiptStrip is
        // dead last on this form, so an affordance there is only ever found *after* the user has
        // typed everything it would have filled in.
        if (!editor.isEditing) {
            ScanEntryRow(
                scan = state.scan,
                onScanTap = onScanTap,
                onScanFromGalleryTap = onScanFromGalleryTap,
            )
            Spacer(Modifier.height(16.dp))
        }

        state.scan.previewPath?.let { path ->
            // Shown during review so the three *read* fields (amount, date, merchant) are
            // verifiable by looking — which is why they carry no captions (decision 4).
            val previewIndex = editor.images.indexOfFirst { it.localPath == path }
            ScanPreview(
                path = path,
                onView = { if (previewIndex >= 0) receiptViewerIndex = previewIndex },
            )
            Spacer(Modifier.height(12.dp))
        }

        if (state.scan.amountNotFound && state.scan.failure == null) {
            // A partial read is not a failure (decision 8) — the form opens either way; this is
            // the framing hint, delivered where the user can act on it (Item 5, gap 5).
            ScanHint("Read the receipt, but couldn't find the total — check the amount below.")
            Spacer(Modifier.height(12.dp))
        }

        state.scan.duplicate?.let { duplicate ->
            // Warns, never blocks (ADR-0062 Consequences): Save stays fully enabled below, because
            // two identical same-day expenses are ordinary and refusing one would be worse than
            // the duplicate it prevented.
            ScanHint(
                text = "You already recorded ${money(duplicate.amount)} on " +
                    "${formatShortDate(duplicate.date)}. Save anyway if this is a different one.",
                tone = LocalPlayfulColors.current.semantic.negative,
            )
            Spacer(Modifier.height(12.dp))
        }

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
            label = { Text("Amount (${currencyGlyph()})") },
            singleLine = true,
            isError = TransactionError.AMOUNT_NOT_POSITIVE in editor.errors,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        FieldLabel("Account")
        EntityChipRow(options = accountOptions, selectedId = editor.accountId, onSelect = onAccountChange)
        if (state.scan.accountInferred) {
            state.scan.inferredFrom?.let { InferredCaption(it) }
        }
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
                label = { Text("Transfer fee (${currencyGlyph()}, optional)") },
                supportingText = {
                    Text("Recorded as a separate expense under \"Transfer fees\".")
                },
                singleLine = true,
                isError = editor.transferFeeError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (!editor.isAdjustment && !editor.isSettlement) {
            // Balance-adjustment (ADR-0057) and debt-settlement (ADR-0019 #14) rows are
            // system-generated with no category — a shown-but-empty picker would read as an
            // error the user can't clear.
            FieldLabel("Category")
            EntityGrid(
                options = categoryOptions,
                selectedId = editor.categoryId,
                onSelect = onCategoryChange,
            )
            if (state.scan.categoryInferred) {
                state.scan.inferredFrom?.let { InferredCaption(it) }
            }
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
                Text("Date", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                Text(
                    formatShortDate(editor.date),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
            }
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = "Pick date",
                tint = colors.accent,
            )
        }

        if (state.scan.dateGuessed) {
            // The receipt's date was written ambiguously (07/08 could be either order); month-first
            // was assumed per PH convention, so it is marked as guessed rather than read (decision 4).
            Text(
                "Date was unclear on the receipt — double-check it",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }

        if (!touchesSharedAccount) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Private", modifier = Modifier.weight(1f), color = colors.textPrimary)
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
                color = colors.textSecondary,
            )
        }

        if (state.canPayForPartner && editor.type == TransactionType.EXPENSE) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Paid for ${state.partnerName}", modifier = Modifier.weight(1f), color = colors.textPrimary)
                Switch(checked = editor.paidForPartner, onCheckedChange = onPaidForPartnerChange)
            }
            Text(
                "Adds what ${state.partnerName} owes you to the Partner Debt Tracker.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            if (editor.paidForPartner) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editor.amountOwedText,
                    onValueChange = onAmountOwedChange,
                    label = { Text("Amount owed (${currencyGlyph()})") },
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
            // Cap-checked at the tap (Item 28) — at the cap the sheet shows without opening the picker.
            onAddReceipt = { onAddPhotoTap { galleryLauncher.launch("image/*") } },
            onRemoveReceipt = onRemoveImage,
            onViewReceipt = { index -> receiptViewerIndex = index },
        )

        if (editor.errors.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            editor.errors.forEach { error ->
                Text(
                    text = error.message(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.semantic.negative,
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

/**
 * `Save as draft` — the parking exit (ADR-0066), sitting beside Save in the top bar so all three
 * exits (← Cancel, park, Save) are in one place.
 *
 * Icon rather than a second text button, for room: "Save as draft" spelled out next to "Save"
 * takes ~170dp of a 360dp bar and truncates "New transaction" to "New transactio…". The long-press
 * tooltip (and the same string as `contentDescription`, so TalkBack reads it) keeps the glyph from
 * being the only explanation.
 *
 * Disabled until there is something worth parking, so an untouched form can't mint an empty queue
 * row; hidden outright while editing (decision 11), which the caller handles.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveAsDraftAction(enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalPlayfulColors.current
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(SAVE_AS_DRAFT_LABEL) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.BookmarkAdd,
                contentDescription = SAVE_AS_DRAFT_LABEL,
                tint = if (enabled) colors.accent else colors.textTertiary,
            )
        }
    }
}

private const val SAVE_AS_DRAFT_LABEL = "Save as draft"

/**
 * The two scan doors, side by side (ADR-0062 decision 3, changed 2026-08-04 from a single CTA
 * plus a chooser sheet — one less tap, and both routes are named).
 *
 * Both carry the same `Feature.RECEIPT_SCANNING` gate: the gallery leg is the same feature from a
 * different source, so gating only the camera would leave a free bypass. That leg is deliberately
 * full-width and labelled rather than demoted to an icon, because it carries the GCash/Maya/GrabPay
 * screenshot case — the cleanest, most reliable read this app's receipts get.
 */
@Composable
private fun ScanEntryRow(
    scan: ReceiptScanUiState,
    onScanTap: () -> Unit,
    onScanFromGalleryTap: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ScanEntryButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.PhotoCamera,
            label = "Scan receipt",
            locked = scan.locked,
            enabled = !scan.inProgress,
            onClick = onScanTap,
        )
        ScanEntryButton(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.PhotoLibrary,
            label = "From gallery",
            locked = scan.locked,
            enabled = !scan.inProgress,
            onClick = onScanFromGalleryTap,
        )
    }
    if (scan.inProgress) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "Reading receipt…",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ScanEntryButton(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    locked: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPlayfulColors.current
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(
            // The lock glyph only ever appears under enforcement; the gate ships dormant.
            imageVector = if (locked) Icons.Filled.Lock else icon,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary)
    }
}

/** The scanned receipt, shown large at the top of the form during review. */
@Composable
private fun ScanPreview(path: String, onView: () -> Unit) {
    AsyncImage(
        model = File(path),
        contentDescription = "Scanned receipt",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onView),
    )
}

@Composable
private fun ScanHint(text: String, tone: Color = LocalPlayfulColors.current.accent) {
    val colors = LocalPlayfulColors.current
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .background(tone.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(10.dp),
    )
}

/**
 * The caption under an *inferred* field (ADR-0062 decision 4). Only Category and Account carry one:
 * the three read fields are verifiable against the photo shown at the top of the form, while these
 * two appear nowhere on the receipt — without a caption, a user comparing form to photo has no way
 * to account for them.
 */
@Composable
private fun InferredCaption(merchant: String) {
    Text(
        "From your last $merchant visit",
        style = MaterialTheme.typography.bodySmall,
        color = LocalPlayfulColors.current.textSecondary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = LocalPlayfulColors.current.textSecondary,
    )
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
    val colors = LocalPlayfulColors.current
    val atMax = images.size >= TransactionImage.MAX
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Receipts",
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            if (images.isNotEmpty()) {
                Text(
                    text = "${images.size}/${TransactionImage.MAX}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (atMax) colors.semantic.negative else colors.textSecondary,
                )
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onAddReceipt, enabled = !atMax) {
                Icon(
                    Icons.Filled.AddPhotoAlternate,
                    contentDescription = "Attach receipt",
                    tint = if (atMax) colors.textTertiary else colors.accent,
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
