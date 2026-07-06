package com.iponlove.app.feature.transactions.presentation

import android.content.Context
import android.net.Uri
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountsUseCase
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.couple.domain.model.CoupleMembers
import com.iponlove.app.feature.couple.domain.usecase.ObserveCoupleMembersUseCase
import com.iponlove.app.feature.partnerdebt.domain.usecase.PaidOnBehalfUseCase
import com.iponlove.app.feature.transactions.domain.model.TransactionImage
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.CompressReceiptUseCase
import com.iponlove.app.feature.transactions.domain.usecase.GetTransactionImagesUseCase
import com.iponlove.app.feature.transactions.domain.usecase.GetTransactionUseCase
import com.iponlove.app.feature.transactions.domain.usecase.SaveTransactionImagesUseCase
import com.iponlove.app.feature.transactions.domain.usecase.SaveTransferUseCase
import com.iponlove.app.feature.transactions.domain.usecase.UpsertTransactionUseCase
import com.iponlove.app.feature.widget.presentation.AddTransactionWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
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
) : ViewModel() {

    private val saved = savedStateHandle
    private val argId: String? = savedStateHandle[TXN_ID_KEY]
    private val isEditing: Boolean = argId != null && argId != NEW

    private val editor = MutableStateFlow<TransactionEditorState?>(null)
    private val missing = MutableStateFlow(false)

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
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AddTransactionUiState(),
        )

    init {
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

    fun onImagePicked(uri: Uri) {
        val current = editor.value ?: return
        if (current.images.size >= TransactionImage.MAX) return // UI-level cap (backstop lives in the save use case + repo)
        viewModelScope.launch {
            val imageId = UUID.randomUUID().toString()
            val localPath = compressReceipt(uri, imageId)
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
        }
    }

    fun onRemoveImage(imageId: String) =
        mutate { it.copy(images = it.images.filterNot { image -> image.id == imageId }) }

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
                clearDraft()
                AddTransactionWidget().updateAll(context)
                onDone()
            }
        }
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
            KEY_CATEGORY, KEY_NOTE, KEY_PRIVATE, KEY_DATE, KEY_IMAGE_IDS, KEY_IMAGE_PATHS,
            KEY_IMAGE_URLS, KEY_PAID_FOR_PARTNER, KEY_AMOUNT_OWED, KEY_TRANSFER_FEE, KEY_LINKED_FEE_ID,
        ).forEach { saved.remove<Any>(it) }
        existingTransferFeeId = null
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
        private const val KEY_IMAGE_IDS = "draft_image_ids"
        private const val KEY_IMAGE_PATHS = "draft_image_paths"
        private const val KEY_IMAGE_URLS = "draft_image_urls"
        private const val KEY_PAID_FOR_PARTNER = "draft_paid_for_partner"
        private const val KEY_AMOUNT_OWED = "draft_amount_owed"
        private const val KEY_TRANSFER_FEE = "draft_transfer_fee"
        private const val KEY_LINKED_FEE_ID = "draft_linked_fee_id"

        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
