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
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
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
    private val upsertTransaction: UpsertTransactionUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val editor = MutableStateFlow<TransactionEditorState?>(null)
    private val isRefreshing = MutableStateFlow(false)

    // Latest domain values, captured so the editor and saves can read full transactions
    // (the list exposes display models only).
    private var latestTransactions: List<Transaction> = emptyList()
    private var firstAccountId: String? = null

    val uiState: StateFlow<TransactionsUiState> =
        combine(
            observeTransactions(),
            observeAccounts(),
            observeCategories(),
            editor,
            isRefreshing,
        ) { transactions, accounts, categories, editorState, refreshing ->
            latestTransactions = transactions
            firstAccountId = accounts.firstOrNull()?.id

            val accountNames = accounts.associate { it.id to it.name }
            val categoryNames = categories.associate { it.id to it.name }

            TransactionsUiState(
                isLoading = false,
                isRefreshing = refreshing,
                items = transactions.map { it.toListItem(accountNames, categoryNames) },
                accounts = accounts,
                categories = categories,
                editor = editorState,
                canAdd = accounts.isNotEmpty(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = TransactionsUiState(),
        )

    fun startCreate() {
        editor.value = TransactionEditorState(accountId = firstAccountId, date = Instant.now())
    }

    fun startEdit(id: String) {
        val t = latestTransactions.firstOrNull { it.id == id } ?: return
        editor.value = TransactionEditorState(
            id = t.id,
            type = t.type,
            amountText = t.amount.toPlainString(),
            accountId = t.accountId,
            toAccountId = t.toAccountId,
            categoryId = t.categoryId,
            note = t.note.orEmpty(),
            isPrivate = t.isPrivate,
            date = t.date,
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
            errors = emptySet(),
        )
    }

    fun onAmountChange(value: String) = editor.update { it?.copy(amountText = value, errors = emptySet()) }

    fun onAccountChange(id: String) = editor.update { it?.copy(accountId = id, errors = emptySet()) }

    fun onToAccountChange(id: String) = editor.update { it?.copy(toAccountId = id, errors = emptySet()) }

    fun onCategoryChange(id: String) = editor.update { it?.copy(categoryId = id, errors = emptySet()) }

    fun onNoteChange(value: String) = editor.update { it?.copy(note = value) }

    fun onPrivateChange(value: Boolean) = editor.update { it?.copy(isPrivate = value) }

    fun onDateChange(date: Instant) = editor.update { it?.copy(date = date) }

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
            id = s.id ?: UUID.randomUUID().toString(),
            type = s.type,
            amount = amount,
            accountId = s.accountId!!,
            toAccountId = toAccountId,
            categoryId = categoryId,
            note = s.note.trim().ifBlank { null },
            date = s.date,
            isPrivate = s.isPrivate,
        )
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
