package com.iponlove.app.feature.transactions.presentation

import android.content.Context
import android.net.Uri
import com.iponlove.app.feature.widget.presentation.Widgets
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import com.iponlove.app.core.ui.UpsellPrompt
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountsUseCase
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.couple.domain.model.CoupleMembers
import com.iponlove.app.feature.couple.domain.usecase.ObserveCoupleMembersUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.PaidOnBehalfUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveReceiptGalleryCopyEnabledUseCase
import com.iponlove.app.feature.transactions.data.ReceiptScanFileStore
import com.iponlove.app.feature.transactions.domain.model.ReceiptScanResult
import com.iponlove.app.feature.transactions.domain.model.TransactionImage
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.CheckReceiptPhotoCapUseCase
import com.iponlove.app.feature.transactions.domain.usecase.CompressReceiptUseCase
import com.iponlove.app.feature.transactions.domain.usecase.GetTransactionImagesUseCase
import com.iponlove.app.feature.transactions.domain.usecase.GetTransactionUseCase
import com.iponlove.app.feature.transactions.domain.usecase.SaveReceiptToGalleryUseCase
import com.iponlove.app.feature.transactions.domain.usecase.SaveTransactionImagesUseCase
import com.iponlove.app.feature.transactions.domain.usecase.SaveTransferUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ScanReceiptUseCase
import com.iponlove.app.feature.transactions.domain.usecase.UpsertTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject

/**
 * Backs the full-screen add/edit-transaction route. The editable draft is mirrored into
 * [SavedStateHandle] on every change so it survives process death — the route alone does not
 * (Slice 0/1). Pure state transitions live in [TransactionEditorReducer]; this class wires the
 * DB/couple context, persistence, and side effects.
 */
@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    observeAccounts: ObserveAccountsUseCase,
    observeCategories: ObserveCategoriesUseCase,
    observeCoupleMembers: ObserveCoupleMembersUseCase,
    private val getTransaction: GetTransactionUseCase,
    private val upsertTransaction: UpsertTransactionUseCase,
    private val paidOnBehalf: PaidOnBehalfUseCase,
    private val saveTransfer: SaveTransferUseCase,
    private val compressReceipt: CompressReceiptUseCase,
    private val getTransactionImages: GetTransactionImagesUseCase,
    private val saveTransactionImages: SaveTransactionImagesUseCase,
    private val checkReceiptPhotoCap: CheckReceiptPhotoCapUseCase,
    private val scanReceipt: ScanReceiptUseCase,
    private val receiptScanFileStore: ReceiptScanFileStore,
    private val saveReceiptToGallery: SaveReceiptToGalleryUseCase,
    private val observeGalleryCopyEnabled: ObserveReceiptGalleryCopyEnabledUseCase,
    private val premiumGate: PremiumGate,
    private val analytics: Analytics,
) : ViewModel() {

    private val saved = savedStateHandle
    private val argId: String? = savedStateHandle[TXN_ID_KEY]
    private val isEditing: Boolean = argId != null && argId != NEW

    private val editor = MutableStateFlow<TransactionEditorState?>(null)
    private val missing = MutableStateFlow(false)
    private val upsell = MutableStateFlow<UpsellPrompt?>(null)
    private val scan = MutableStateFlow(ReceiptScanUiState())

    /**
     * The in-flight (or gallery-copy-pending) `cacheDir/scans` capture, mirrored into
     * [SavedStateHandle] because it must survive process death: `ACTION_IMAGE_CAPTURE` hands off
     * to a separate camera process, and without this a restored draft cannot find its own capture
     * and would silently skip decision 7's Save-time gallery write (ADR-0062 decision 9).
     */
    private var scanTempPath: String? = null

    // Which cap raised the current upsell — the analytics source for its "Get Premium" tap.
    private var upsellSource: String? = null

    private var latestAccounts: List<Account> = emptyList()
    private var coupleId: String? = null
    private var myId: String? = null
    private var partnerId: String? = null
    // The transfer's currently-linked fee expense id, loaded from DB when editing (ADR-0031).
    // Null for a new transfer or one that never had a fee; survives process death via
    // SavedStateHandle so a resumed save still retires the correct old row.
    private var existingTransferFeeId: String? = null

    private data class Sources(
        val accounts: List<Account>,
        val categories: List<Category>,
        val members: CoupleMembers?,
    )

    /**
     * Scan state folded together with its soft gate (`Feature.RECEIPT_SCANNING`, individual
     * scope). Reactive rather than one-shot so an enforcement flip or a purchase re-locks the two
     * entry buttons live; **false throughout while dormant**, so nothing changes pre-flip.
     */
    private val scanState = combine(
        scan,
        premiumGate.observeLocked(Scope.INDIVIDUAL),
        observeGalleryCopyEnabled(),
    ) { state, locked, galleryCopyEnabled ->
        state.copy(locked = locked, galleryCopyEnabled = galleryCopyEnabled)
    }

    val uiState: StateFlow<AddTransactionUiState> =
        combine(
            observeAccounts(),
            observeCategories(),
            observeCoupleMembers(),
            editor,
            missing,
        ) { accounts, categories, members, editorState, isMissing ->
            latestAccounts = accounts
            coupleId = members?.me?.coupleId
            myId = members?.me?.id
            partnerId = members?.partner?.id

            AddTransactionUiState(
                editor = editorState,
                accounts = accounts,
                categories = categories,
                // Debt creation only makes sense while creating (not editing) with a known partner.
                canPayForPartner = !isEditing && coupleId != null && myId != null && partnerId != null,
                partnerName = members?.partner?.displayName ?: "Partner",
                isPaired = coupleId != null,
                missing = isMissing,
            )
        }.combine(upsell) { state, upsellState ->
            // Outer combine: the primary one is already at Kotlin's 5-flow typed max.
            state.copy(upsell = upsellState)
        }.combine(scanState) { state, scanUiState ->
            state.copy(scan = scanUiState)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AddTransactionUiState(),
        )

    init {
        // Restored before the editor branches below: a capture that was in flight when the
        // process died is redelivered through ActivityResultRegistry, and without its path the
        // result would arrive with nothing to read (ADR-0062 decision 9).
        scanTempPath = saved[KEY_SCAN_TEMP_PATH]
        saved.get<String>(KEY_SCAN_PREVIEW)?.let { path -> scan.update { it.copy(previewPath = path) } }

        val restored = hydrateFromSaved()
        when {
            restored != null -> {
                editor.value = restored
                existingTransferFeeId = saved[KEY_LINKED_FEE_ID]
            }
            !isEditing -> setEditor(
                TransactionEditorState(id = UUID.randomUUID().toString(), date = Instant.now()),
            )
            else -> viewModelScope.launch {
                val t = getTransaction(argId!!)
                if (t == null) {
                    missing.value = true
                } else {
                    existingTransferFeeId = t.transferFeeTransactionId
                    val feeAmountText = t.transferFeeTransactionId
                        ?.let { getTransaction(it) }
                        ?.amount
                        ?.toPlainString()
                        .orEmpty()
                    setEditor(
                        TransactionEditorState(
                            id = t.id,
                            isEditing = true,
                            type = t.type,
                            amountText = t.amount.toPlainString(),
                            accountId = t.accountId,
                            toAccountId = t.toAccountId,
                            categoryId = t.categoryId,
                            note = t.note.orEmpty(),
                            isPrivate = t.isPrivate,
                            date = t.date,
                            isAdjustment = t.isAdjustment,
                            isSettlement = t.isSettlement,
                            images = getTransactionImages(t.id),
                            transferFeeText = feeAmountText,
                        ),
                    )
                }
            }
        }
    }

    private fun sharedAccountIds(): Set<String> =
        latestAccounts.filter { it.isShared }.map { it.id }.toSet()

    private fun mutate(transform: (TransactionEditorState) -> TransactionEditorState) {
        val current = editor.value ?: return
        setEditor(transform(current))
    }

    fun onTypeChange(type: TransactionType) = mutate { TransactionEditorReducer.onType(it, type) }
    fun onAmountChange(value: String) = mutate { it.copy(amountText = value, errors = emptySet()) }
    fun onAccountChange(id: String) = mutate { TransactionEditorReducer.onAccount(it, id, sharedAccountIds()) }
    fun onToAccountChange(id: String) = mutate { TransactionEditorReducer.onToAccount(it, id, sharedAccountIds()) }
    fun onCategoryChange(id: String) = mutate { it.copy(categoryId = id, errors = emptySet()) }
    fun onNoteChange(value: String) = mutate { it.copy(note = value) }
    fun onPrivateChange(value: Boolean) = mutate { it.copy(isPrivate = value) }
    fun onPaidForPartnerChange(value: Boolean) = mutate { TransactionEditorReducer.onPaidForPartner(it, value) }
    fun onAmountOwedChange(value: String) = mutate { it.copy(amountOwedText = value, amountOwedError = false) }
    fun onTransferFeeChange(value: String) = mutate { TransactionEditorReducer.onTransferFee(it, value) }
    fun onDateChange(date: Instant) = mutate { it.copy(date = date) }

    /**
     * Tap-time media-cap check (Item 28): consulted at the "Add photo" tap, *before* the system
     * picker opens, so an enforced free user at the cap sees the cap sheet immediately instead of
     * being made to browse and pick first (the S8 wart). Dormant or under-cap → [launchPicker]
     * runs and the flow is exactly as before; [onImagePicked] keeps the same check as backstop.
     */
    fun onAddPhotoTap(launchPicker: () -> Unit) {
        val current = editor.value ?: return
        if (current.images.size >= TransactionImage.MAX) return // hard ceiling — add is disabled anyway
        viewModelScope.launch {
            when (val check = checkReceiptPhotoCap(current.images.size)) {
                CapCheck.Allowed -> launchPicker()
                is CapCheck.Blocked -> raiseUpsell("receipt_photos", "receipt photos", check)
            }
        }
    }

    fun onImagePicked(uri: Uri) {
        val current = editor.value ?: return
        if (current.images.size >= TransactionImage.MAX) return // hard ceiling (= premium max); backstop lives in the save use case + repo
        viewModelScope.launch {
            // Free-tier media cap (S8): with enforcement off, or under the cap, this returns
            // Allowed and the receipt is added exactly as before. Free = 1 photo, premium = 3.
            when (val check = checkReceiptPhotoCap(current.images.size)) {
                CapCheck.Allowed -> attachReceipt(uri)
                is CapCheck.Blocked -> raiseUpsell("receipt_photos", "receipt photos", check)
            }
        }
    }

    /**
     * Compresses [uri] into `filesDir/receipts` and appends it to the draft, returning the local
     * path. Shared by the manual attach path and the scan flow.
     *
     * The decode/rotate/re-encode runs on IO, not the caller's Main dispatcher. That was a
     * pre-existing main-thread bitmap operation on the manual path; this slice makes it routine —
     * every scan hits it, with a full-resolution camera frame, on the low-RAM budget-Android
     * devices this feature targets — so it is corrected here for the same reason the ADR corrects
     * the EXIF bug alongside it.
     */
    private suspend fun attachReceipt(uri: Uri): String {
        val imageId = UUID.randomUUID().toString()
        val localPath = withContext(Dispatchers.IO) { compressReceipt(uri, imageId) }
        mutate { state ->
            state.copy(
                images = state.images + TransactionImage(
                    id = imageId,
                    transactionId = state.id,
                    localPath = localPath,
                    url = null,
                    position = state.images.size,
                ),
            )
        }
        return localPath
    }

    // --- Receipt scan (v1.7.3 Item 2, ADR-0062) -------------------------------------------------

    /**
     * The `Scan receipt` button. Mints a `cacheDir/scans` target and hands its [FileProvider][androidx.core.content.FileProvider]
     * Uri to [launchCamera], which the screen wires to `TakePicture`. **No `CAMERA` permission is
     * involved** — `ACTION_IMAGE_CAPTURE` only requires one if the app declares it, and this
     * design deliberately never does (decision 2).
     *
     * The lock is checked by the *screen* against `state.scan.locked` before calling this, the
     * same shape the Recurring calendar gate uses.
     */
    fun onScanTap(launchCamera: (Uri) -> Unit) {
        if (editor.value == null) return
        // Release any previous capture first — one is still held whenever the last scan failed, or
        // succeeded with the gallery-copy toggle ON. Without this, scanning twice in a row strands
        // the first file until the sweep, which is the leak class v1.7.0 Item 14 already paid to
        // fix. This makes onScanTap the single owner of the temp's replacement.
        releaseScanTemp()
        val capture = receiptScanFileStore.newCapture()
        setScanTempPath(capture.file.absolutePath)
        scan.update { it.copy(failure = null, amountNotFound = false) }
        launchCamera(capture.uri)
    }

    /** Deletes the current `cacheDir/scans` capture, if any, and forgets it. */
    private fun releaseScanTemp() {
        scanTempPath?.let { receiptScanFileStore.delete(it) }
        setScanTempPath(null)
    }

    /** The `From gallery` button. Same feature from a different source — gated identically, since
     *  gating only the camera leg would leave a free bypass (decision 3). */
    fun onScanFromGalleryTap(launchPicker: () -> Unit) {
        if (editor.value == null) return
        scan.update { it.copy(failure = null, amountNotFound = false) }
        launchPicker()
    }

    /** `TakePicture` result. A cancelled capture leaves nothing behind; the age-based sweep is the
     *  backstop if the process died instead. */
    fun onCaptureTaken(success: Boolean) {
        val path = scanTempPath
        if (!success || path == null) {
            releaseScanTemp()
            return
        }
        processScan(receiptScanFileStore.uriFor(File(path)), fromCamera = true)
    }

    /** A scan sourced from the picker — covers the GCash/Maya/GrabPay confirmation screenshot
     *  case, the cleanest read this app's receipts get. */
    fun onScanImagePicked(uri: Uri) = processScan(uri, fromCamera = false)

    /**
     * capture → recognise → parse → *then* compress (decision 4). OCR runs on the full-resolution
     * image because the 1080px/JPEG-85 storage size sits below what reliably reads thermal print.
     */
    private fun processScan(uri: Uri, fromCamera: Boolean) {
        if (editor.value == null) return
        scan.update { it.copy(inProgress = true, failure = null, amountNotFound = false) }
        viewModelScope.launch {
            val result = runCatching { scanReceipt(uri) }.getOrNull()
            if (result == null || result.isEmpty) {
                // Nothing usable — stay in the camera (decision 8). The temp file is kept so a
                // retake can delete it deliberately rather than leaving it to the sweep.
                scan.update {
                    it.copy(
                        inProgress = false,
                        previewPath = null,
                        failure = if (result == null) {
                            ReceiptScanFailure.NO_TEXT
                        } else {
                            ReceiptScanFailure.NOTHING_USABLE
                        },
                    )
                }
                return@launch
            }

            applyScan(result)
            // The photo rides the existing cap path unchanged; the scan gate above is what
            // differentiates the feature now, not maxReceiptPhotos (decision 6's reversal).
            val current = editor.value
            // A compression failure (unreadable frame, OOM on a full-resolution capture) must not
            // take the draft down with it: the read already landed, so degrade to "fields
            // prefilled, photo not attached" rather than letting it escape the scope.
            val preview = if (current != null && current.images.size < TransactionImage.MAX &&
                checkReceiptPhotoCap(current.images.size) == CapCheck.Allowed
            ) {
                runCatching { attachReceipt(uri) }.getOrNull()
            } else {
                null
            }

            // Decision 9, owner (1): the temp dies here, unless the gallery-copy toggle is ON —
            // then decision 7's Save-time write holds it, because that write must source the
            // full-resolution original rather than the downgraded re-encode.
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

    /** A **partial** read is not a failure (decision 8): each field fills in only if it was found,
     *  and anything already typed survives. `Type` is forced to EXPENSE — a receipt is never
     *  income — through the reducer, so the fields that don't apply are normalised with it. */
    private fun applyScan(result: ReceiptScanResult) {
        mutate { state ->
            val expense = TransactionEditorReducer.onType(state, TransactionType.EXPENSE)
            expense.copy(
                amountText = result.amount?.toPlainString() ?: expense.amountText,
                note = result.merchant ?: expense.note,
                // Matches the DatePicker's own convention (UTC midnight = a calendar day).
                date = result.date?.atStartOfDay(ZoneOffset.UTC)?.toInstant() ?: expense.date,
            )
        }
    }

    /** Retake, from the failed-read prompt — the actual fix for a bad frame, and the reason no
     *  cropping or edge detection is built (decision 8). */
    fun onRetakeScan(launchCamera: (Uri) -> Unit) {
        setScanPreview(null)
        scan.update { it.copy(failure = null, amountNotFound = false, dateGuessed = false) }
        onScanTap(launchCamera)
    }

    /** "Enter manually" — dismisses the prompt and leaves the form as it is. */
    fun onDismissScanFailure() {
        releaseScanTemp()
        scan.update { it.copy(failure = null) }
    }

    /** A tap on a locked scan button — logs the §10.10 funnel touchpoint before the screen routes
     *  to the paywall. Unreachable while the gate is dormant. */
    fun onScanUpsellTap(): String {
        val source = "receipt_scanning"
        analytics.log("upsell_tap", source = source)
        return source
    }

    private fun setScanTempPath(path: String?) {
        scanTempPath = path
        if (path == null) saved.remove<String>(KEY_SCAN_TEMP_PATH) else saved[KEY_SCAN_TEMP_PATH] = path
    }

    private fun setScanPreview(path: String?) {
        if (path == null) saved.remove<String>(KEY_SCAN_PREVIEW) else saved[KEY_SCAN_PREVIEW] = path
        scan.update { it.copy(previewPath = path) }
    }

    private fun raiseUpsell(source: String, entityLabel: String, blocked: CapCheck.Blocked) {
        upsellSource = source
        upsell.value = UpsellPrompt(entityLabel, blocked.freeLimit, blocked.premiumMax)
    }

    fun dismissUpsell() {
        upsell.value = null
    }

    /** The upsell "Get Premium" tap — logs the funnel touchpoint (§10.10) before the screen routes
     *  to the paywall. */
    fun onUpsellUpgrade(): String {
        val source = upsellSource ?: "receipt_photos"
        analytics.log("upsell_tap", source = source)
        upsell.value = null
        return source
    }

    fun onRemoveImage(imageId: String) {
        editor.value?.images?.find { it.id == imageId }?.localPath?.let { path ->
            File(path).delete()
            // Removing the scanned receipt from the strip must also drop the review preview above
            // the form — it points at the file just deleted, and its "view" tap has nothing left
            // to open.
            if (path == scan.value.previewPath) setScanPreview(null)
        }
        mutate { it.copy(images = it.images.filterNot { image -> image.id == imageId }) }
    }

    fun save(onDone: () -> Unit) {
        val s = editor.value ?: return
        when (val result = TransactionEditorReducer.build(s, sharedAccountIds(), uiState.value.canPayForPartner)) {
            is TransactionEditorReducer.BuildResult.Invalid -> setEditor(s.copy(errors = result.errors))
            TransactionEditorReducer.BuildResult.OwedInvalid -> setEditor(s.copy(amountOwedError = true))
            TransactionEditorReducer.BuildResult.TransferFeeInvalid -> setEditor(s.copy(transferFeeError = true))
            is TransactionEditorReducer.BuildResult.Ready -> viewModelScope.launch {
                val owed = result.amountOwed
                val fee = result.transferFee
                when {
                    owed != null -> paidOnBehalf(
                        transaction = result.transaction,
                        amountOwed = owed,
                        borrowerId = partnerId!!,
                        lenderId = myId!!,
                        coupleId = coupleId!!,
                    )
                    fee != null -> saveTransfer(
                        result.transaction.copy(transferFeeTransactionId = existingTransferFeeId),
                        fee,
                    )
                    else -> upsertTransaction(result.transaction)
                }
                // Reconcile the draft's receipt images to transaction_images rows now that the
                // parent transaction exists (deferred persistence — see SaveTransactionImagesUseCase).
                saveTransactionImages(result.transaction.id, s.images)
                writeGalleryCopy()
                clearDraft()
                Widgets.updateAll(context)
                onDone()
            }
        }
    }

    /**
     * The `Pictures/Love, Ipon` copy, written **on Save and never at capture** (ADR-0062 decision
     * 7, pinned 2026-08-03): writing at capture would pollute the gallery with abandoned scans the
     * moment a user backs out of an unsaved draft — worse than the v1.7.0 Item 14 leak it would
     * repeat, since a file there sits outside every sweep the app owns and may already have synced
     * to Google Photos. Camera leg only: a picked image is already in the gallery.
     *
     * The temp file is released either way, so this is also the last of decision 9's three owners.
     */
    private suspend fun writeGalleryCopy() {
        val path = scanTempPath ?: return
        if (observeGalleryCopyEnabled().first()) saveReceiptToGallery(path)
        releaseScanTemp()
    }

    // --- SavedStateHandle draft persistence (survives process death) ---

    private fun setEditor(state: TransactionEditorState) {
        editor.value = state
        saved[KEY_ID] = state.id
        saved[KEY_IS_EDITING] = state.isEditing
        saved[KEY_TYPE] = state.type.name
        saved[KEY_AMOUNT] = state.amountText
        saved[KEY_ACCOUNT] = state.accountId
        saved[KEY_TO_ACCOUNT] = state.toAccountId
        saved[KEY_CATEGORY] = state.categoryId
        saved[KEY_NOTE] = state.note
        saved[KEY_PRIVATE] = state.isPrivate
        saved[KEY_DATE] = state.date.toEpochMilli()
        saved[KEY_IS_ADJUSTMENT] = state.isAdjustment
        saved[KEY_IS_SETTLEMENT] = state.isSettlement
        // Persist the receipt-image draft as three parallel string lists (SavedStateHandle has no
        // typed-list support for a domain model); blank encodes null.
        saved[KEY_IMAGE_IDS] = ArrayList(state.images.map { it.id })
        saved[KEY_IMAGE_PATHS] = ArrayList(state.images.map { it.localPath.orEmpty() })
        saved[KEY_IMAGE_URLS] = ArrayList(state.images.map { it.url.orEmpty() })
        saved[KEY_PAID_FOR_PARTNER] = state.paidForPartner
        saved[KEY_AMOUNT_OWED] = state.amountOwedText
        saved[KEY_TRANSFER_FEE] = state.transferFeeText
        saved[KEY_LINKED_FEE_ID] = existingTransferFeeId
    }

    private fun clearDraft() {
        listOf(
            KEY_ID, KEY_IS_EDITING, KEY_TYPE, KEY_AMOUNT, KEY_ACCOUNT, KEY_TO_ACCOUNT,
            KEY_CATEGORY, KEY_NOTE, KEY_PRIVATE, KEY_DATE, KEY_IS_ADJUSTMENT, KEY_IS_SETTLEMENT, KEY_IMAGE_IDS,
            KEY_IMAGE_PATHS, KEY_IMAGE_URLS, KEY_PAID_FOR_PARTNER, KEY_AMOUNT_OWED, KEY_TRANSFER_FEE,
            KEY_LINKED_FEE_ID, KEY_SCAN_TEMP_PATH, KEY_SCAN_PREVIEW,
        ).forEach { saved.remove<Any>(it) }
        existingTransferFeeId = null
        scanTempPath = null
        scan.value = ReceiptScanUiState()
    }

    private fun hydrateFromSaved(): TransactionEditorState? {
        val id: String = saved[KEY_ID] ?: return null
        return TransactionEditorState(
            id = id,
            isEditing = saved[KEY_IS_EDITING] ?: false,
            type = (saved.get<String>(KEY_TYPE))?.let { TransactionType.valueOf(it) } ?: TransactionType.EXPENSE,
            amountText = saved[KEY_AMOUNT] ?: "",
            accountId = saved[KEY_ACCOUNT],
            toAccountId = saved[KEY_TO_ACCOUNT],
            categoryId = saved[KEY_CATEGORY],
            note = saved[KEY_NOTE] ?: "",
            isPrivate = saved[KEY_PRIVATE] ?: false,
            date = (saved.get<Long>(KEY_DATE))?.let { Instant.ofEpochMilli(it) } ?: Instant.now(),
            isAdjustment = saved[KEY_IS_ADJUSTMENT] ?: false,
            isSettlement = saved[KEY_IS_SETTLEMENT] ?: false,
            images = hydrateImages(id),
            paidForPartner = saved[KEY_PAID_FOR_PARTNER] ?: false,
            amountOwedText = saved[KEY_AMOUNT_OWED] ?: "",
            transferFeeText = saved[KEY_TRANSFER_FEE] ?: "",
        )
    }

    private fun hydrateImages(transactionId: String): List<TransactionImage> {
        val ids: List<String> = saved.get<ArrayList<String>>(KEY_IMAGE_IDS) ?: return emptyList()
        val paths: List<String> = saved.get<ArrayList<String>>(KEY_IMAGE_PATHS) ?: emptyList()
        val urls: List<String> = saved.get<ArrayList<String>>(KEY_IMAGE_URLS) ?: emptyList()
        return ids.mapIndexed { index, imageId ->
            TransactionImage(
                id = imageId,
                transactionId = transactionId,
                localPath = paths.getOrNull(index)?.ifBlank { null },
                url = urls.getOrNull(index)?.ifBlank { null },
                position = index,
            )
        }
    }

    companion object {
        const val TXN_ID_KEY = "txnId"
        const val NEW = "new"

        private const val KEY_ID = "draft_id"
        private const val KEY_IS_EDITING = "draft_is_editing"
        private const val KEY_TYPE = "draft_type"
        private const val KEY_AMOUNT = "draft_amount"
        private const val KEY_ACCOUNT = "draft_account"
        private const val KEY_TO_ACCOUNT = "draft_to_account"
        private const val KEY_CATEGORY = "draft_category"
        private const val KEY_NOTE = "draft_note"
        private const val KEY_PRIVATE = "draft_private"
        private const val KEY_DATE = "draft_date"
        private const val KEY_IS_ADJUSTMENT = "draft_is_adjustment"
        private const val KEY_IS_SETTLEMENT = "draft_is_settlement"
        private const val KEY_IMAGE_IDS = "draft_image_ids"
        private const val KEY_IMAGE_PATHS = "draft_image_paths"
        private const val KEY_IMAGE_URLS = "draft_image_urls"
        private const val KEY_PAID_FOR_PARTNER = "draft_paid_for_partner"
        private const val KEY_AMOUNT_OWED = "draft_amount_owed"
        private const val KEY_TRANSFER_FEE = "draft_transfer_fee"
        private const val KEY_LINKED_FEE_ID = "draft_linked_fee_id"
        private const val KEY_SCAN_TEMP_PATH = "draft_scan_temp_path"
        private const val KEY_SCAN_PREVIEW = "draft_scan_preview"

        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
