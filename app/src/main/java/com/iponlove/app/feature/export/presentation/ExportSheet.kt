package com.iponlove.app.feature.export.presentation

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.export.domain.AttachmentLimits
import com.iponlove.app.feature.export.domain.model.ExportFormat
import com.iponlove.app.feature.transactions.presentation.components.TransactionFilterSheet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * The Records export sheet (v1.7.0 Item 6, re-grilled 2026-07-24). Fully self-contained: opens
 * **blank** (no inherited Records filter or viewed month) with a visible-but-optional **"What to
 * include"** row — tapping it opens the shared filter sheet ([TransactionFilterSheet]) stacked on
 * top — and an editable **From/To** date range defaulting to month-to-date. Solves the
 * discoverability gap the original inherited-filter design had: a user wanting to export just a
 * subset now sees the scoping control right on the sheet, rather than needing to know to filter
 * Records first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    onDismiss: () -> Unit,
    onOpenPremium: (source: String) -> Unit = {},
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val colors = LocalPlayfulColors.current
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var filterSheetOpen by remember { mutableStateOf(false) }
    var fromPickerOpen by remember { mutableStateOf(false) }
    var toPickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is ExportEvent.Share -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = event.mimeType
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(send, "Export records"))
                    onDismiss()
                }
                is ExportEvent.OpenPaywall -> {
                    onDismiss()
                    onOpenPremium(event.source)
                }
                ExportEvent.Failed ->
                    Toast.makeText(context, "Couldn't finish the export.", Toast.LENGTH_LONG).show()
            }
        }
    }

    // An export is foreground-only (decision 5): walking away from the sheet abandons it.
    ModalBottomSheet(
        onDismissRequest = { viewModel.cancelExport(); onDismiss() },
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Export",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = colors.textPrimary,
            )
            Text(
                text = scopeSummary(state),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            // "What to include" row (decision 2/4) — visible-but-optional scoping. Opens the same
            // shared filter sheet Records uses, stacked on top.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { filterSheetOpen = true }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "What to include:  ${state.includeLabel}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "Choose what to include",
                    tint = colors.textSecondary,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                DateField(
                    label = "From",
                    date = state.fromDate,
                    isError = state.rangeInvalid,
                    onClick = { fromPickerOpen = true },
                    modifier = Modifier.weight(1f),
                )
                DateField(
                    label = "To",
                    date = state.toDate,
                    isError = state.rangeInvalid,
                    onClick = { toPickerOpen = true },
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.rangeInvalid) {
                Text(
                    "From can't be after To",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.semantic.negative,
                )
            }

            // Slice 2: the three formats, with their pre-tap guards spelled out rather than the
            // buttons just going dead. CSV is always available; PDF/ZIP carry receipt photos.
            val progress = state.progress
            if (progress != null) {
                ExportProgressRow(progress = progress, onCancel = viewModel::cancelExport)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.export(ExportFormat.CSV) },
                        enabled = state.enabled(ExportFormat.CSV),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Export CSV") }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AttachmentFormatButton(
                            format = ExportFormat.PDF,
                            state = state,
                            onClick = { viewModel.export(ExportFormat.PDF) },
                            modifier = Modifier.weight(1f),
                        )
                        AttachmentFormatButton(
                            format = ExportFormat.ZIP,
                            state = state,
                            onClick = { viewModel.export(ExportFormat.ZIP) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    attachmentNote(state)?.let { note ->
                        Text(
                            note,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }

    if (filterSheetOpen) {
        TransactionFilterSheet(
            applied = state.appliedFilter,
            categories = state.filterableCategories,
            accounts = state.filterableAccounts,
            onApply = {
                viewModel.applyFilter(it)
                filterSheetOpen = false
            },
            onClear = {
                viewModel.clearFilter()
                filterSheetOpen = false
            },
            onDismiss = { filterSheetOpen = false },
        )
    }

    if (fromPickerOpen) {
        SingleDatePickerDialog(
            initialDate = state.fromDate,
            onDismiss = { fromPickerOpen = false },
            onConfirm = { viewModel.setFromDate(it); fromPickerOpen = false },
        )
    }
    if (toPickerOpen) {
        SingleDatePickerDialog(
            initialDate = state.toDate,
            onDismiss = { toPickerOpen = false },
            onConfirm = { viewModel.setToDate(it); toPickerOpen = false },
        )
    }
}

/** The live scope readout — transaction count, plus the photo count once there is one, since the
 *  photos are what make an attachment export slow, capped and network-bound. */
private fun scopeSummary(state: ExportUiState): String {
    if (state.transactionCount == 0) return "No transactions to export"
    val txns = "${state.transactionCount} transaction${if (state.transactionCount == 1) "" else "s"}"
    if (state.photoCount == 0) return "Exporting $txns"
    return "Exporting $txns · ${state.photoCount} receipt${if (state.photoCount == 1) "" else "s"}"
}

/**
 * The single line under the PDF/ZIP pair explaining why they can't be tapped, in the order the user
 * would hit them. Returns null when the pair is usable — no note, no noise.
 */
private fun attachmentNote(state: ExportUiState): String? = when {
    !state.ready -> null
    state.photoCapExceeded ->
        "PDF and ZIP carry up to ${AttachmentLimits.MAX_PHOTOS} receipts — this range has " +
            "${state.photoCount}. Narrow the dates or filter to fewer."
    state.offlineBlocked ->
        "PDF and ZIP need a connection to fetch your receipt photos. CSV works offline."
    state.attachmentsLocked -> "PDF and ZIP bundle your receipt photos — a Premium feature."
    state.photoCount == 0 -> "No receipts in this range — PDF and ZIP will carry the records only."
    else -> null
}

@Composable
private fun AttachmentFormatButton(
    format: ExportFormat,
    state: ExportUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        // A *locked* format stays tappable — the tap is the paywall route, not a refusal
        // (decision 2). Only the real blockers (cap, offline, empty scope) disable it.
        enabled = state.attachmentsAvailable(),
        modifier = modifier,
    ) {
        Text(format.label)
        if (state.attachmentsLocked) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.Lock,
                contentDescription = "Premium",
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/** Numeric, cancellable progress (decision 5) — "Downloading receipts… 34 of 112". */
@Composable
private fun ExportProgressRow(progress: ExportProgress, onCancel: () -> Unit) {
    val colors = LocalPlayfulColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = if (progress.assembling) {
                "Building your ${progress.format.label}…"
            } else {
                "Downloading receipts… ${progress.done} of ${progress.total}"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
        LinearProgressIndicator(
            progress = {
                if (progress.total == 0) 0f else progress.done.toFloat() / progress.total
            },
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

private val DATE_FIELD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

@Composable
private fun DateField(
    label: String,
    date: LocalDate,
    isError: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPlayfulColors.current
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        Text(
            date.format(DATE_FIELD_FORMAT),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (isError) colors.semantic.negative else colors.textPrimary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val pickerState = rememberDatePickerState(
        // DatePicker speaks UTC-midnight millis; convert through UTC so the calendar day is exact.
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { onConfirm(it.toUtcDate()) }
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState)
    }
}

private fun Long.toUtcDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
