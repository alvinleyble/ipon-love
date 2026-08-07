package com.iponlove.app.feature.widget.presentation

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountsUseCase
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.drafts.domain.usecase.PromoteDraftUseCase
import com.iponlove.app.feature.drafts.domain.usecase.SaveDraftUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveReceiptGalleryCopyEnabledUseCase
import com.iponlove.app.feature.transactions.data.ReceiptScanFileStore
import com.iponlove.app.feature.transactions.domain.model.ReceiptScanResult
import com.iponlove.app.feature.transactions.domain.model.TransactionImage
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.CheckReceiptPhotoCapUseCase
import com.iponlove.app.feature.transactions.domain.usecase.CompressReceiptUseCase
import com.iponlove.app.feature.transactions.domain.usecase.FindDuplicateScanUseCase
import com.iponlove.app.feature.transactions.domain.usecase.InferFromReceiptHistoryUseCase
import com.iponlove.app.feature.transactions.domain.usecase.SaveReceiptToGalleryUseCase
import com.iponlove.app.feature.transactions.domain.usecase.SaveTransactionImagesUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ScanReceiptUseCase
import com.iponlove.app.feature.transactions.domain.usecase.TransactionError
import com.iponlove.app.feature.transactions.domain.usecase.TransactionValidator
import com.iponlove.app.feature.transactions.domain.usecase.UpsertTransactionUseCase
import com.iponlove.app.feature.transactions.presentation.DuplicateScanWarning
import com.iponlove.app.feature.transactions.presentation.ReceiptScanFailure
import com.iponlove.app.feature.transactions.presentation.ReceiptScanUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject

/**
 * Backs the home-screen widget's Quick Add sheet.
 *
 * v1.7.3 Item 14 ([ADR-0067]) gave it three things it never had: a Notes field, the same
 * `📷 Scan receipt` / `🖼️ From gallery` doors as the full form, and a `Save as draft` exit. It
 * stays its **own** lighter state machine (ADR-0067 decision 3) — it calls the same use cases the
 * full editor calls rather than adopting `TransactionEditorViewModel`, so the sheet can't slowly
 * regrow into a second copy of the full form.
 *
 * The form is mirrored into [SavedStateHandle] for the same reason the full editor mirrors its own:
 * `ACTION_IMAGE_CAPTURE` hands off to a separate camera process, and on a low-RAM device this
 * process is routinely killed while that camera is foreground. `ActivityResultRegistry` redelivers
 * the result across the restart — without the mirror it would arrive with no capture path to read
 * and no form to fill.
 */
@HiltViewModel
class QuickAddViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    observeAccounts: ObserveAccountsUseCase,
    observeCategories: ObserveCategoriesUseCase,
    private val upsertTransaction: UpsertTransactionUseCase,
    private val compressReceipt: CompressReceiptUseCase,
    private val saveTransactionImages: SaveTransactionImagesUseCase,
    private val checkReceiptPhotoCap: CheckReceiptPhotoCapUseCase,
    private val scanReceipt: ScanReceiptUseCase,
    private val inferFromReceiptHistory: InferFromReceiptHistoryUseCase,
    private val findDuplicateScan: FindDuplicateScanUseCase,
    private val receiptScanFileStore: ReceiptScanFileStore,
    private val saveReceiptToGallery: SaveReceiptToGalleryUseCase,
    private val observeGalleryCopyEnabled: ObserveReceiptGalleryCopyEnabledUseCase,
    private val saveDraft: SaveDraftUseCase,
    private val promoteDraft: PromoteDraftUseCase,
    private val premiumGate: PremiumGate,
    private val analytics: Analytics,
) : ViewModel() {

    private val form = MutableStateFlow(restoreForm())
    private val scan = MutableStateFlow(
        ReceiptScanUiState(previewPath = savedStateHandle[KEY_SCAN_PREVIEW]),
    )

    /** The in-flight (or gallery-copy-pending) `cacheDir/scans` capture — see the class note. */
    private var scanTempPath: String? = savedStateHandle[KEY_SCAN_TEMP_PATH]

    /**
     * The `filesDir/receipts` file compressed for *this* sheet that no `transaction_images` row or
     * draft owns yet. Deleted when the sheet is abandoned; handed over (forgotten, not deleted) on
     * Save and on `Save as draft`.
     */
    private var unsavedImagePath: String? = savedStateHandle[KEY_UNSAVED_IMAGE_PATH]

    /**
     * The date read off the receipt, if any. The sheet has no date picker — the full form shows one
     * and Quick Add deliberately doesn't (Item 14 scope) — so a read date is applied silently at
     * save/park time rather than displayed. Null ⇒ now.
     */
    private var scannedDate: Instant? =
        savedStateHandle.get<Long>(KEY_SCANNED_DATE)?.let(Instant::ofEpochMilli)

    private var latestAccounts: List<Account> = emptyList()
    private var latestCategories: List<Category> = emptyList()

    /** Scan state folded together with its soft gate — reactive, so an enforcement flip or a
     *  purchase re-locks the two doors live. **False throughout while dormant.** */
    private val scanState = combine(
        scan,
        premiumGate.observeLocked(Scope.INDIVIDUAL),
        observeGalleryCopyEnabled(),
    ) { state, locked, galleryCopyEnabled ->
        state.copy(locked = locked, galleryCopyEnabled = galleryCopyEnabled)
    }

    val uiState = combine(
        observeAccounts(),
        observeCategories(),
        form,
    ) { accounts, categories, f ->
        latestAccounts = accounts
        latestCategories = categories
        QuickAddUiState(
            type = f.type,
            amountText = f.amountText,
            accountId = f.accountId ?: accounts.firstOrNull()?.id,
            categoryId = f.categoryId,
            note = f.note,
            image = f.image,
            accounts = accounts,
            categories = categories.filter { it.type == f.type.toCategoryType() },
            errors = f.errors,
            canSaveAsDraft = f.hasDraftContent(),
        )
    }.combine(scanState) { state, scanUiState ->
        state.copy(scan = scanUiState)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = QuickAddUiState(),
    )

    fun onTypeChange(type: TransactionType) {
        clearDuplicateWarning()
        updateForm { it.copy(type = type, categoryId = null, errors = emptySet()) }
    }

    fun onAmountChange(value: String) {
        clearDuplicateWarning()
        updateForm { it.copy(amountText = value, errors = emptySet()) }
    }

    fun onAccountChange(id: String) {
        scan.update { it.copy(accountInferred = false) }
        updateForm { it.copy(accountId = id, errors = emptySet()) }
    }

    fun onCategoryChange(id: String) {
        scan.update { it.copy(categoryInferred = false) }
        updateForm { it.copy(categoryId = id, errors = emptySet()) }
    }

    fun onNoteChange(value: String) = updateForm { it.copy(note = value) }

    // --- Receipt scan (ADR-0067 decision 1 — the full pipeline, same use cases as the form) ------

    /** The `Scan receipt` door. Releases any previous capture first, so scanning twice in a row
     *  can't strand the first temp file (the v1.7.0 Item 14 leak class). */
    fun onScanTap(launchCamera: (Uri) -> Unit) {
        releaseScanTemp()
        val capture = receiptScanFileStore.newCapture()
        setScanTempPath(capture.file.absolutePath)
        scan.update { it.copy(failure = null, amountNotFound = false) }
        launchCamera(capture.uri)
    }

    /** The `From gallery` door — the same feature from a different source, gated identically. */
    fun onScanFromGalleryTap(launchPicker: () -> Unit) {
        scan.update { it.copy(failure = null, amountNotFound = false) }
        launchPicker()
    }

    fun onCaptureTaken(success: Boolean) {
        val path = scanTempPath
        if (!success || path == null) {
            releaseScanTemp()
            return
        }
        processScan(receiptScanFileStore.uriFor(File(path)), fromCamera = true)
    }

    fun onScanImagePicked(uri: Uri) = processScan(uri, fromCamera = false)

    /** Retake, from the failed-read prompt. */
    fun onRetakeScan(launchCamera: (Uri) -> Unit) {
        setScanPreview(null)
        scan.update { it.copy(failure = null, amountNotFound = false, dateGuessed = false) }
        onScanTap(launchCamera)
    }

    /** "Enter manually" — dismisses the prompt and leaves the sheet as it is. */
    fun onDismissScanFailure() {
        releaseScanTemp()
        scan.update { it.copy(failure = null) }
    }

    /** A tap on a locked scan door. Reuses the full form's `"receipt_scanning"` source unsplit by
     *  originating screen (ADR-0067 decision 4) — the caller deep-links to the paywall with it. */
    fun onScanUpsellTap(): String {
        val source = SCAN_UPSELL_SOURCE
        analytics.log("upsell_tap", source = source)
        return source
    }

    /** capture → recognise → parse → infer → *then* compress, exactly as the full form does. */
    private fun processScan(uri: Uri, fromCamera: Boolean) {
        retractInference()
        scan.update {
            it.copy(inProgress = true, failure = null, amountNotFound = false, duplicate = null)
        }
        viewModelScope.launch {
            val result = runCatching { scanReceipt(uri) }.getOrNull()
            if (result == null || result.isEmpty) {
                scan.update {
                    it.copy(
                        inProgress = false,
                        failure = if (result == null) {
                            ReceiptScanFailure.NO_TEXT
                        } else {
                            ReceiptScanFailure.NOTHING_USABLE
                        },
                    )
                }
                setScanPreview(null)
                return@launch
            }

            applyScan(result)
            catchingNonCancellation { inferFromHistory(result.merchant) }
            catchingNonCancellation { checkForDuplicate() }

            // One photo, so the cap is consulted against 0 (or against 1 on a re-scan, where the
            // old image is replaced below rather than added to).
            val preview = if (checkReceiptPhotoCap(0) == CapCheck.Allowed) {
                catchingNonCancellation { attachReceipt(uri) }
            } else {
                null
            }

            // The temp dies here unless the gallery-copy toggle is ON — then the Save-time write
            // holds it, because that write must source the full-resolution original rather than
            // the downgraded re-encode (ADR-0062 decisions 7 + 9).
            val holdForGalleryCopy = fromCamera && observeGalleryCopyEnabled().first()
            if (!holdForGalleryCopy) releaseScanTemp()

            setScanPreview(preview)
            scan.update {
                it.copy(
                    inProgress = false,
                    failure = null,
                    amountNotFound = result.amount == null,
                    dateGuessed = result.dateIsAmbiguous,
                )
            }
        }
    }

    /** A **partial** read is not a failure: each field fills in only if it was found, and anything
     *  already typed survives. Type is forced to EXPENSE — a receipt is never income. */
    private fun applyScan(result: ReceiptScanResult) {
        updateForm { f ->
            val expense = if (f.type == TransactionType.EXPENSE) f else f.copy(type = TransactionType.EXPENSE, categoryId = null)
            expense.copy(
                amountText = result.amount?.toPlainString() ?: expense.amountText,
                note = result.merchant ?: expense.note,
                errors = emptySet(),
            )
        }
        // Matches the full form's DatePicker convention (UTC midnight = a calendar day).
        result.date?.let { setScannedDate(it.atStartOfDay(ZoneOffset.UTC).toInstant()) }
    }

    /**
     * `Category` and `Account` from the user's own past transactions at the same merchant (the
     * same [[Merchant memory]] rules the full form follows): fill only a field the user left empty,
     * and only with an id that still names a live option.
     */
    private suspend fun inferFromHistory(merchant: String?) {
        if (merchant == null) return
        val match = inferFromReceiptHistory(merchant) ?: return

        val current = form.value
        val expenseCategoryIds = latestCategories
            .filter { it.type == CategoryType.EXPENSE }
            .mapTo(mutableSetOf()) { it.id }
        val categoryId = match.categoryId
            ?.takeIf { current.categoryId == null && it in expenseCategoryIds }
        val accountId = match.accountId
            ?.takeIf { current.accountId == null && latestAccounts.any { account -> account.id == it } }
        if (categoryId == null && accountId == null) return

        updateForm {
            it.copy(
                categoryId = categoryId ?: it.categoryId,
                accountId = accountId ?: it.accountId,
                errors = emptySet(),
            )
        }
        scan.update {
            it.copy(
                inferredFrom = match.merchant,
                categoryInferred = categoryId != null,
                accountInferred = accountId != null,
            )
        }
    }

    /** Un-does the last scan's inference before a new read supersedes it. */
    private fun retractInference() {
        val state = scan.value
        if (state.categoryInferred) updateForm { it.copy(categoryId = null) }
        if (state.accountInferred) updateForm { it.copy(accountId = null) }
        scan.update { it.copy(inferredFrom = null, categoryInferred = false, accountInferred = false) }
    }

    /** Advisory only — warns, never blocks (ADR-0062 Consequences). */
    private suspend fun checkForDuplicate() {
        val current = form.value
        val amount = current.amountText.trim().toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return
        val existing = findDuplicateScan(amount, effectiveDate(), current.id) ?: return
        scan.update { it.copy(duplicate = DuplicateScanWarning(existing.amount, existing.date)) }
    }

    private fun clearDuplicateWarning() {
        if (scan.value.duplicate != null) scan.update { it.copy(duplicate = null) }
    }

    /**
     * Compresses [uri] into `filesDir/receipts` and hangs it on the form. Replaces any image
     * already held (a re-scan supersedes the last read), releasing the old file so a retake can't
     * leak one.
     */
    private suspend fun attachReceipt(uri: Uri): String {
        val imageId = UUID.randomUUID().toString()
        val localPath = withContext(Dispatchers.IO) { compressReceipt(uri, imageId) }
        releaseUnsavedImage()
        setUnsavedImagePath(localPath)
        updateForm { f ->
            f.copy(
                image = TransactionImage(
                    id = imageId,
                    transactionId = f.id,
                    localPath = localPath,
                    url = null,
                    position = 0,
                ),
            )
        }
        return localPath
    }

    // --- The three exits -------------------------------------------------------------------------

    fun save(onSaved: () -> Unit) {
        val f = form.value
        val resolvedAccountId = uiState.value.accountId
        val amount = f.amountText.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
        val errors = TransactionValidator.validate(
            type = f.type,
            amount = amount,
            accountId = resolvedAccountId,
            toAccountId = null,
            categoryId = f.categoryId,
        )
        if (errors.isNotEmpty()) {
            updateForm { it.copy(errors = errors.toSet()) }
            return
        }
        viewModelScope.launch {
            val transaction = f.toTransaction(
                amount = amount,
                accountId = resolvedAccountId!!,
                date = effectiveDate(),
            )
            upsertTransaction(transaction)
            // The receipt's transaction_images row can only be written once its parent exists.
            saveTransactionImages(transaction.id, listOfNotNull(f.image))
            // Promotion's second half (ADR-0066 decision 5): a no-op unless this sheet's id was
            // already parked, which is why ordering — transaction first, retire second — is what
            // makes a re-run an idempotent upsert of the same id rather than doubled money.
            promoteDraft(transaction.id)
            writeGalleryCopy()
            handOverFiles()
            Widgets.updateAll(context)
            onSaved()
        }
    }

    /**
     * `Save as draft` — the third exit (ADR-0067 decision 2), reusing Item 8's use cases unchanged.
     *
     * Nothing is validated: an amount-less, account-less, category-less sheet is exactly what a
     * draft is for. Two things happen here that also happen on Save, and both are deliberate — the
     * gallery copy is written (draft-save is a deliberate act of keeping, not the accidental
     * pollution ADR-0062 decision 7 refused), and file ownership passes to the parked row, without
     * which [onAbandon] would delete the very photo the draft was parked to keep.
     */
    fun saveAsDraft(onSaved: () -> Unit) {
        val f = form.value
        if (!f.hasDraftContent()) return
        viewModelScope.launch {
            saveDraft(f.toDraft(accountId = uiState.value.accountId, parkedAt = effectiveDate()))
            writeGalleryCopy()
            handOverFiles()
            onSaved()
        }
    }

    /**
     * Cancel, swipe-dismiss and system Back all land here before `finish()` (ADR-0067 decision 7).
     * An abandoned sheet can hold a captured full-resolution temp and a compressed receipt that no
     * row owns; leaving them to the age-based sweep alone would reopen the v1.7.0 Item 14 leak
     * class through a new door, just with a longer window.
     *
     * Idempotent, and [onCleared] repeats it — a process-level teardown that skips the UI path
     * (task swipe, low-memory kill of the sheet) must clean up too.
     */
    fun onAbandon() {
        releaseUnsavedImage()
        releaseScanTemp()
    }

    override fun onCleared() {
        onAbandon()
        super.onCleared()
    }

    /**
     * The `Pictures/Love, Ipon` copy, written on Save and on draft-save, never at capture
     * (ADR-0062 decision 7). Camera leg only — a picked image is already in the gallery — and it
     * releases the held temp either way.
     */
    private suspend fun writeGalleryCopy() {
        val path = scanTempPath ?: return
        if (observeGalleryCopyEnabled().first()) saveReceiptToGallery(path)
        releaseScanTemp()
    }

    /** After a successful Save or park, the written row owns the compressed file — so this forgets
     *  it rather than deleting it, and [onAbandon]/[onCleared] then have nothing to reclaim. */
    private fun handOverFiles() {
        setUnsavedImagePath(null)
    }

    /** The receipt's own date when one was read, else now. */
    private fun effectiveDate(): Instant = scannedDate ?: Instant.now()

    /**
     * [runCatching] minus the one case it should never swallow: a cancelled `viewModelScope` (the
     * user dismissed the sheet mid-scan) must not let the rest of the pipeline run on.
     */
    private inline fun <T> catchingNonCancellation(block: () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }

    // --- SavedStateHandle mirroring ----------------------------------------------------------------

    private fun updateForm(transform: (QuickAddForm) -> QuickAddForm) {
        val next = transform(form.value)
        form.value = next
        savedStateHandle[KEY_ID] = next.id
        savedStateHandle[KEY_TYPE] = next.type.name
        savedStateHandle[KEY_AMOUNT] = next.amountText
        savedStateHandle[KEY_ACCOUNT] = next.accountId
        savedStateHandle[KEY_CATEGORY] = next.categoryId
        savedStateHandle[KEY_NOTE] = next.note
        savedStateHandle[KEY_IMAGE_ID] = next.image?.id
        savedStateHandle[KEY_IMAGE_PATH] = next.image?.localPath
    }

    private fun restoreForm(): QuickAddForm {
        val id: String = savedStateHandle[KEY_ID] ?: UUID.randomUUID().toString()
        val imageId: String? = savedStateHandle[KEY_IMAGE_ID]
        val imagePath: String? = savedStateHandle[KEY_IMAGE_PATH]
        return QuickAddForm(
            id = id,
            type = savedStateHandle.get<String>(KEY_TYPE)
                ?.let { TransactionType.valueOf(it) } ?: TransactionType.EXPENSE,
            amountText = savedStateHandle[KEY_AMOUNT] ?: "",
            accountId = savedStateHandle[KEY_ACCOUNT],
            categoryId = savedStateHandle[KEY_CATEGORY],
            note = savedStateHandle[KEY_NOTE] ?: "",
            image = imageId?.let {
                TransactionImage(
                    id = it,
                    transactionId = id,
                    localPath = imagePath,
                    url = null,
                    position = 0,
                )
            },
        )
    }

    private fun setScanTempPath(path: String?) {
        scanTempPath = path
        savedStateHandle[KEY_SCAN_TEMP_PATH] = path
    }

    private fun releaseScanTemp() {
        scanTempPath?.let { receiptScanFileStore.delete(it) }
        setScanTempPath(null)
    }

    private fun setUnsavedImagePath(path: String?) {
        unsavedImagePath = path
        savedStateHandle[KEY_UNSAVED_IMAGE_PATH] = path
    }

    private fun releaseUnsavedImage() {
        unsavedImagePath?.let { File(it).delete() }
        setUnsavedImagePath(null)
    }

    private fun setScanPreview(path: String?) {
        savedStateHandle[KEY_SCAN_PREVIEW] = path
        scan.update { it.copy(previewPath = path) }
    }

    private fun setScannedDate(date: Instant) {
        scannedDate = date
        savedStateHandle[KEY_SCANNED_DATE] = date.toEpochMilli()
    }

    companion object {
        /** Unsplit by originating screen: no other feature splits its upsell source that way, and
         *  a widget-specific variant would fragment an otherwise-aggregate metric. */
        const val SCAN_UPSELL_SOURCE = "receipt_scanning"

        private const val KEY_ID = "quick_add_id"
        private const val KEY_TYPE = "quick_add_type"
        private const val KEY_AMOUNT = "quick_add_amount"
        private const val KEY_ACCOUNT = "quick_add_account"
        private const val KEY_CATEGORY = "quick_add_category"
        private const val KEY_NOTE = "quick_add_note"
        private const val KEY_IMAGE_ID = "quick_add_image_id"
        private const val KEY_IMAGE_PATH = "quick_add_image_path"
        private const val KEY_SCAN_TEMP_PATH = "quick_add_scan_temp_path"
        private const val KEY_SCAN_PREVIEW = "quick_add_scan_preview"
        private const val KEY_SCANNED_DATE = "quick_add_scanned_date"
        private const val KEY_UNSAVED_IMAGE_PATH = "quick_add_unsaved_image_path"

        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

private fun TransactionType.toCategoryType(): CategoryType = when (this) {
    TransactionType.INCOME -> CategoryType.INCOME
    else -> CategoryType.EXPENSE
}
