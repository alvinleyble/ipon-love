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
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.AttachReceiptUseCase
import com.iponlove.app.feature.transactions.domain.usecase.GetTransactionUseCase
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
    private val attachReceipt: AttachReceiptUseCase,
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
            restored != null -> editor.value = restored
            !isEditing -> setEditor(
                TransactionEditorState(id = UUID.randomUUID().toString(), date = Instant.now()),
            )
            else -> viewModelScope.launch {
                val t = getTransaction(argId!!)
                if (t == null) {
                    missing.value = true
                } else {
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
                            attachmentUrl = t.attachmentUrl,
                            attachmentLocalPath = t.attachmentLocalPath,
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
    fun onDateChange(date: Instant) = mutate { it.copy(date = date) }

    fun onReceiptPicked(uri: Uri) {
        val id = editor.value?.id ?: return
        viewModelScope.launch {
            val localPath = attachReceipt(uri, id)
            mutate { it.copy(attachmentLocalPath = localPath) }
        }
    }

    fun onRemoveReceipt() = mutate { it.copy(attachmentLocalPath = null, attachmentUrl = null) }

    fun save(onDone: () -> Unit) {
        val s = editor.value ?: return
        when (val result = TransactionEditorReducer.build(s, sharedAccountIds(), uiState.value.canPayForPartner)) {
            is TransactionEditorReducer.BuildResult.Invalid -> setEditor(s.copy(errors = result.errors))
            TransactionEditorReducer.BuildResult.OwedInvalid -> setEditor(s.copy(amountOwedError = true))
            is TransactionEditorReducer.BuildResult.Ready -> viewModelScope.launch {
                val owed = result.amountOwed
                if (owed != null) {
                    paidOnBehalf(
                        transaction = result.transaction,
                        amountOwed = owed,
                        borrowerId = partnerId!!,
                        lenderId = myId!!,
                        coupleId = coupleId!!,
                    )
                } else {
                    upsertTransaction(result.transaction)
                }
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
        saved[KEY_LOCAL_PATH] = state.attachmentLocalPath
        saved[KEY_URL] = state.attachmentUrl
        saved[KEY_PAID_FOR_PARTNER] = state.paidForPartner
        saved[KEY_AMOUNT_OWED] = state.amountOwedText
    }

    private fun clearDraft() {
        listOf(
            KEY_ID, KEY_IS_EDITING, KEY_TYPE, KEY_AMOUNT, KEY_ACCOUNT, KEY_TO_ACCOUNT,
            KEY_CATEGORY, KEY_NOTE, KEY_PRIVATE, KEY_DATE, KEY_LOCAL_PATH, KEY_URL,
            KEY_PAID_FOR_PARTNER, KEY_AMOUNT_OWED,
        ).forEach { saved.remove<Any>(it) }
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
            attachmentLocalPath = saved[KEY_LOCAL_PATH],
            attachmentUrl = saved[KEY_URL],
            paidForPartner = saved[KEY_PAID_FOR_PARTNER] ?: false,
            amountOwedText = saved[KEY_AMOUNT_OWED] ?: "",
        )
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
        private const val KEY_LOCAL_PATH = "draft_local_path"
        private const val KEY_URL = "draft_url"
        private const val KEY_PAID_FOR_PARTNER = "draft_paid_for_partner"
        private const val KEY_AMOUNT_OWED = "draft_amount_owed"

        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
