package com.iponlove.app.feature.transactions.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.glance.appwidget.updateAll
import com.iponlove.app.core.sync.SyncEngine
import com.iponlove.app.feature.widget.presentation.BalanceWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountsUseCase
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.couple.domain.model.CoupleMembers
import com.iponlove.app.feature.couple.domain.usecase.ObserveCoupleMembersUseCase
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.partnerdebt.domain.usecase.PaidOnBehalfUseCase
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import android.net.Uri
import com.iponlove.app.feature.transactions.domain.usecase.AttachReceiptUseCase
import com.iponlove.app.feature.transactions.domain.usecase.DeleteTransactionUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ObserveTransactionsUseCase
import com.iponlove.app.feature.transactions.domain.usecase.TransactionValidator
import com.iponlove.app.feature.transactions.domain.usecase.UpsertTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    observeTransactions: ObserveTransactionsUseCase,
    observeAccounts: ObserveAccountsUseCase,
    observeCategories: ObserveCategoriesUseCase,
    observeCoupleMembers: ObserveCoupleMembersUseCase,
    private val upsertTransaction: UpsertTransactionUseCase,
    private val paidOnBehalf: PaidOnBehalfUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val attachReceipt: AttachReceiptUseCase,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val editor = MutableStateFlow<TransactionEditorState?>(null)
    private val isRefreshing = MutableStateFlow(false)

    // Latest domain values, captured so the editor and saves can read full transactions
    // (the list exposes display models only).
    private var latestTransactions: List<Transaction> = emptyList()
    private var firstAccountId: String? = null
    // Couple identity captured for the "paid for partner" save path; null when not paired
    // (or the partner row hasn't replicated in yet).
    private var coupleId: String? = null
    private var myId: String? = null
    private var partnerId: String? = null
    private var partnerName: String = "Partner"

    private data class Sources(
        val transactions: List<Transaction>,
        val accounts: List<Account>,
        val categories: List<Category>,
        val members: CoupleMembers?,
    )

    val uiState: StateFlow<TransactionsUiState> =
        combine(
            observeTransactions(),
            observeAccounts(),
            observeCategories(),
            observeCoupleMembers(),
        ) { transactions, accounts, categories, members ->
            Sources(transactions, accounts, categories, members)
        }
            .combine(editor) { sources, editorState -> sources to editorState }
            .combine(isRefreshing) { (sources, editorState), refreshing ->
                latestTransactions = sources.transactions
                firstAccountId = sources.accounts.firstOrNull()?.id
                coupleId = sources.members?.me?.coupleId
                myId = sources.members?.me?.id
                partnerId = sources.members?.partner?.id
                partnerName = sources.members?.partner?.displayName ?: "Partner"

                val accountNames = sources.accounts.associate { it.id to it.name }
                val categoryNames = sources.categories.associate { it.id to it.name }

                TransactionsUiState(
                    isLoading = false,
                    isRefreshing = refreshing,
                    items = sources.transactions.map { it.toListItem(accountNames, categoryNames) },
                    accounts = sources.accounts,
                    categories = sources.categories,
                    editor = editorState,
                    canAdd = sources.accounts.isNotEmpty(),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = TransactionsUiState(),
            )

    // Paid-for-partner needs a known borrower (the partner) and lender (me) under a couple.
    private fun canPayForPartner(): Boolean =
        coupleId != null && myId != null && partnerId != null

    fun startCreate() {
        editor.value = TransactionEditorState(
            id = UUID.randomUUID().toString(),
            isEditing = false,
            accountId = firstAccountId,
            date = Instant.now(),
            canPayForPartner = canPayForPartner(),
            partnerName = partnerName,
        )
    }

    fun startEdit(id: String) {
        val t = latestTransactions.firstOrNull { it.id == id } ?: return
        editor.value = TransactionEditorState(
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
        )
    }

    fun cancelEdit() {
        editor.value = null
    }

    fun sync() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                syncEngine.sync()
            } catch (_: Exception) {
                // SyncEngine already surfaces the error via SyncState.Error;
                // swallow here so an uncaught exception doesn't crash the app.
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun onTypeChange(type: TransactionType) = editor.update { e ->
        e?.copy(
            type = type,
            categoryId = if (type == TransactionType.TRANSFER) null else e.categoryId,
            toAccountId = if (type == TransactionType.TRANSFER) e.toAccountId else null,
            // "Paid for partner" only makes sense on an expense; clear it on any other type.
            paidForPartner = if (type == TransactionType.EXPENSE) e.paidForPartner else false,
            errors = emptySet(),
        )
    }

    fun onAmountChange(value: String) = editor.update { it?.copy(amountText = value, errors = emptySet()) }

    fun onAccountChange(id: String) = editor.update { it?.copy(accountId = id, errors = emptySet()) }

    fun onToAccountChange(id: String) = editor.update { it?.copy(toAccountId = id, errors = emptySet()) }

    fun onCategoryChange(id: String) = editor.update { it?.copy(categoryId = id, errors = emptySet()) }

    fun onNoteChange(value: String) = editor.update { it?.copy(note = value) }

    fun onPrivateChange(value: Boolean) = editor.update { it?.copy(isPrivate = value) }

    fun onPaidForPartnerChange(value: Boolean) = editor.update { e ->
        e ?: return@update null
        e.copy(
            paidForPartner = value,
            // Default the owed amount to the full transaction amount when switching on; the
            // user can edit it down for a bill split. Clear the error either way.
            amountOwedText = if (value && e.amountOwedText.isBlank()) e.amountText else e.amountOwedText,
            amountOwedError = false,
        )
    }

    fun onAmountOwedChange(value: String) =
        editor.update { it?.copy(amountOwedText = value, amountOwedError = false) }

    fun onDateChange(date: Instant) = editor.update { it?.copy(date = date) }

    fun onReceiptPicked(uri: Uri) {
        val id = editor.value?.id ?: return
        viewModelScope.launch {
            val localPath = attachReceipt(uri, id)
            editor.update { it?.copy(attachmentLocalPath = localPath) }
        }
    }

    fun onRemoveReceipt() = editor.update {
        it?.copy(attachmentLocalPath = null, attachmentUrl = null)
    }

    fun save() {
        val s = editor.value ?: return
        val amount = s.amountText.trim().toBigDecimalOrNull() ?: BigDecimal.ZERO
        val categoryId = if (s.type == TransactionType.TRANSFER) null else s.categoryId
        val toAccountId = if (s.type == TransactionType.TRANSFER) s.toAccountId else null

        val errors = TransactionValidator.validate(
            type = s.type,
            amount = amount,
            accountId = s.accountId,
            toAccountId = toAccountId,
            categoryId = categoryId,
        )
        if (errors.isNotEmpty()) {
            editor.value = s.copy(errors = errors.toSet())
            return
        }

        val transaction = Transaction(
            id = s.id,
            type = s.type,
            amount = amount,
            accountId = s.accountId!!,
            toAccountId = toAccountId,
            categoryId = categoryId,
            note = s.note.trim().ifBlank { null },
            date = s.date,
            isPrivate = s.isPrivate,
            attachmentUrl = s.attachmentUrl,
            attachmentLocalPath = s.attachmentLocalPath,
        )

        // "Paid for partner": record the transaction and auto-create a partner debt the
        // partner owes (ADR-0019 #12). Only valid on an expense while paired.
        val payForPartner = s.paidForPartner && s.canPayForPartner && s.type == TransactionType.EXPENSE
        if (payForPartner) {
            val borrower = partnerId
            val lender = myId
            val couple = coupleId
            // Blank owed = full amount (the field defaults to it); otherwise must be 0 < owed ≤ amount.
            val owed = s.amountOwedText.trim().ifBlank { amount.toPlainString() }.toBigDecimalOrNull()
            if (borrower == null || lender == null || couple == null ||
                owed == null || owed.signum() <= 0 || owed > amount
            ) {
                editor.value = s.copy(amountOwedError = true)
                return
            }
            viewModelScope.launch {
                paidOnBehalf(
                    transaction = transaction,
                    amountOwed = owed,
                    borrowerId = borrower,
                    lenderId = lender,
                    coupleId = couple,
                )
                editor.value = null
                BalanceWidget().updateAll(context)
            }
            return
        }

        viewModelScope.launch {
            upsertTransaction(transaction)
            editor.value = null
            BalanceWidget().updateAll(context)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            deleteTransaction(id)
            BalanceWidget().updateAll(context)
        }
    }

    private fun Transaction.toListItem(
        accountNames: Map<String, String>,
        categoryNames: Map<String, String>,
    ): TransactionListItem {
        val accountName = accountNames[accountId] ?: "Account"
        val noteSuffix = note?.takeIf { it.isNotBlank() }?.let { "  •  $it" }.orEmpty()
        return when (type) {
            TransactionType.TRANSFER -> TransactionListItem(
                id = id,
                type = type,
                amount = amount,
                title = "Transfer",
                subtitle = "$accountName → ${accountNames[toAccountId] ?: "Account"}$noteSuffix",
                date = date,
            )
            else -> TransactionListItem(
                id = id,
                type = type,
                amount = amount,
                title = categoryNames[categoryId] ?: "Uncategorized",
                subtitle = "$accountName$noteSuffix",
                date = date,
            )
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
