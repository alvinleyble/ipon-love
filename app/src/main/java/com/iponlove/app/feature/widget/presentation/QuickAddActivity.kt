package com.iponlove.app.feature.widget.presentation

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iponlove.app.MainActivity
import com.iponlove.app.core.ui.IponFilterChip
import com.iponlove.app.core.ui.LocalCurrencySymbol
import com.iponlove.app.core.ui.currencyGlyph
import com.iponlove.app.core.ui.formatShortDate
import com.iponlove.app.core.ui.money
import com.iponlove.app.core.ui.theme.IponTheme
import com.iponlove.app.core.ui.theme.LocalPlayfulColors
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import com.iponlove.app.feature.settings.domain.model.ThemePreferences
import com.iponlove.app.feature.settings.domain.usecase.ObserveCurrencySymbolUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveThemePreferencesUseCase
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.TransactionError
import com.iponlove.app.feature.transactions.presentation.components.InferredCaption
import com.iponlove.app.feature.transactions.presentation.components.ScanEntryRow
import com.iponlove.app.feature.transactions.presentation.components.ScanFailedDialog
import com.iponlove.app.feature.transactions.presentation.components.ScanHint
import com.iponlove.app.feature.transactions.presentation.components.ScanPreview
import com.iponlove.app.feature.transactions.presentation.components.needsLegacyGalleryPermission
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {

    @Inject lateinit var observeThemePreferences: ObserveThemePreferencesUseCase
    @Inject lateinit var observeCurrencySymbol: ObserveCurrencySymbolUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themePreferences by observeThemePreferences()
                .collectAsState(initial = ThemePreferences())
            // This widget entry is its own composition root (not under MainActivity's provider),
            // so it must supply LocalCurrencySymbol itself for the amount label to track the choice.
            val currencySymbol by observeCurrencySymbol()
                .collectAsState(initial = CurrencySymbol.DEFAULT)
            IponTheme(themePreferences = themePreferences) {
                CompositionLocalProvider(LocalCurrencySymbol provides currencySymbol) {
                    QuickAddSheet(
                        onFinish = ::finish,
                        onOpenPremium = ::openPremiumAndFinish,
                    )
                }
            }
        }
    }

    /**
     * A locked scan tap (ADR-0067 decision 4). This Activity is launched straight from the widget
     * and has **no `NavController`**, so it can't reach `SubscriptionScreen` the way every other
     * locked-tap site does. Rather than build a second, sheet-hosted upsell that still couldn't
     * complete a purchase without leaving here for Play Billing, it hands the source to
     * [MainActivity], which lands on the same `subscriptionRoute(source)` every other site uses,
     * and finishes the sheet.
     */
    private fun openPremiumAndFinish(source: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_UPSELL_SOURCE, source),
        )
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(
    onFinish: () -> Unit,
    onOpenPremium: (source: String) -> Unit,
    viewModel: QuickAddViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val colors = LocalPlayfulColors.current

    // Cancel, swipe-dismiss and system Back all route through one abandon path (ADR-0067 decision
    // 7): an abandoned sheet can hold a captured temp and a compressed receipt that no row owns.
    val abandonAndFinish: () -> Unit = {
        viewModel.onAbandon()
        onFinish()
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success -> viewModel.onCaptureTaken(success) }

    val scanPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? -> uri?.let(viewModel::onScanImagePicked) }

    val launchCamera: (Uri) -> Unit = { uri -> cameraLauncher.launch(uri) }

    // API 26-28 only: the Pictures/Love, Ipon copy needs WRITE_EXTERNAL_STORAGE there. The scan
    // proceeds either way — the gallery copy is a convenience, never a gate on scanning.
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

    ModalBottomSheet(onDismissRequest = abandonAndFinish) {
        Column(
            // Scrollable rather than promoted to a full-screen activity (ADR-0067 decision 6): the
            // scan doors, photo preview, Notes and the draft exit make this taller than it was, but
            // a full screen would undercut the reason Quick Add exists as a lightweight entry.
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Quick add", style = MaterialTheme.typography.titleLarge)

            // Above the fields, same as the full form (ADR-0062 decision 3) — an affordance found
            // only after typing everything it would have filled is worth nothing.
            ScanEntryRow(
                scan = state.scan,
                onScanTap = startScan,
                onScanFromGalleryTap = {
                    if (state.scan.locked) {
                        onOpenPremium(viewModel.onScanUpsellTap())
                    } else {
                        viewModel.onScanFromGalleryTap { scanPickerLauncher.launch("image/*") }
                    }
                },
            )

            state.scan.previewPath?.let { path ->
                // Shown during review so the read fields (amount, date, merchant) are verifiable
                // by looking — shorter here than on the full form, which has room to spare.
                ScanPreview(path = path, height = 120.dp)
            }

            if (state.scan.amountNotFound && state.scan.failure == null) {
                ScanHint("Read the receipt, but couldn't find the total — check the amount below.")
            }

            state.scan.duplicate?.let { duplicate ->
                // Warns, never blocks: two identical same-day expenses are ordinary.
                ScanHint(
                    text = "You already recorded ${money(duplicate.amount)} on " +
                        "${formatShortDate(duplicate.date)}. Save anyway if this is a different one.",
                    tone = colors.semantic.negative,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(TransactionType.INCOME, TransactionType.EXPENSE).forEach { type ->
                    IponFilterChip(
                        selected = state.type == type,
                        onClick = { viewModel.onTypeChange(type) },
                        label = { Text(type.label()) },
                    )
                }
            }

            OutlinedTextField(
                value = state.amountText,
                onValueChange = viewModel::onAmountChange,
                label = { Text("Amount (${currencyGlyph()})") },
                singleLine = true,
                isError = TransactionError.AMOUNT_NOT_POSITIVE in state.errors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                ChipPicker(
                    label = "Category",
                    options = state.categories.map { it.id to it.name },
                    selectedId = state.categoryId,
                    onSelect = viewModel::onCategoryChange,
                    isError = TransactionError.CATEGORY_REQUIRED in state.errors,
                )
                if (state.scan.categoryInferred) {
                    state.scan.inferredFrom?.let { InferredCaption(it) }
                }
            }

            Column {
                ChipPicker(
                    label = "Account",
                    options = state.accounts.map { it.id to it.name },
                    selectedId = state.accountId,
                    onSelect = viewModel::onAccountChange,
                    isError = TransactionError.ACCOUNT_REQUIRED in state.errors,
                )
                if (state.scan.accountInferred) {
                    state.scan.inferredFrom?.let { InferredCaption(it) }
                }
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::onNoteChange,
                label = { Text("Notes") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.errors.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.errors.forEach { error ->
                        Text(
                            text = error.message(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = abandonAndFinish) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                // Always present (ADR-0067 decision 2), disabled until there's something worth
                // parking. Icon rather than a third text button, same call the full form's top bar
                // made — three spelled-out actions don't fit a sheet's action row.
                TextButton(
                    onClick = {
                        viewModel.saveAsDraft {
                            // Regular Save gets implicit feedback from the balance widget
                            // repainting; a parked draft writes nothing visible from the home
                            // screen, so it needs its own signal (ADR-0067 decision 8).
                            Toast.makeText(context, "Saved to drafts", Toast.LENGTH_SHORT).show()
                            onFinish()
                        }
                    },
                    enabled = state.canSaveAsDraft,
                ) {
                    Icon(
                        imageVector = Icons.Filled.BookmarkAdd,
                        contentDescription = "Save as draft",
                        modifier = Modifier.height(20.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Draft")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { viewModel.save(onSaved = onFinish) }) { Text("Save") }
            }
        }
    }

    state.scan.failure?.let { failure ->
        ScanFailedDialog(
            failure = failure,
            onRetake = { viewModel.onRetakeScan(launchCamera) },
            onEnterManually = viewModel::onDismissScanFailure,
        )
    }
}

@Composable
private fun ChipPicker(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    isError: Boolean = false,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        if (options.isEmpty()) {
            Text(
                text = "None available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                options.forEachIndexed { index, (id, name) ->
                    if (index > 0) Spacer(Modifier.width(8.dp))
                    IponFilterChip(
                        selected = id == selectedId,
                        onClick = { onSelect(id) },
                        label = { Text(name) },
                    )
                }
            }
        }
    }
}

private fun TransactionType.label(): String = when (this) {
    TransactionType.INCOME -> "Income"
    TransactionType.EXPENSE -> "Expense"
    TransactionType.TRANSFER -> "Transfer"
}

private fun TransactionError.message(): String = when (this) {
    TransactionError.AMOUNT_NOT_POSITIVE -> "Enter an amount greater than zero"
    TransactionError.ACCOUNT_REQUIRED -> "Choose an account"
    TransactionError.CATEGORY_REQUIRED -> "Choose a category"
    TransactionError.DESTINATION_REQUIRED -> "Choose a destination account"
    TransactionError.DESTINATION_SAME_AS_SOURCE -> "Destination must differ from source"
    TransactionError.PRIVATE_ON_SHARED_ACCOUNT -> "A shared account's spend can't be private"
}
