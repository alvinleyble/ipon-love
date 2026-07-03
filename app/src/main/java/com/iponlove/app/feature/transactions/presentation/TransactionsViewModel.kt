package com.iponlove.app.feature.transactions.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.glance.appwidget.updateAll
import com.iponlove.app.core.sync.SyncEngine
import com.iponlove.app.feature.widget.presentation.AddTransactionWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountsUseCase
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.DeleteTransactionUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ObserveTransactionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    observeTransactions: ObserveTransactionsUseCase,
    observeAccounts: ObserveAccountsUseCase,
    observeCategories: ObserveCategoriesUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<TransactionsUiState> =
        combine(
            observeTransactions(),
            observeAccounts(),
            observeCategories(),
            isRefreshing,
        ) { transactions, accounts, categories, refreshing ->
            val accountNames = accounts.associate { it.id to it.name }
            val categoryNames = categories.associate { it.id to it.name }

            TransactionsUiState(
                isLoading = false,
                isRefreshing = refreshing,
                items = transactions.map { it.toListItem(accountNames, categoryNames) },
                canAdd = accounts.isNotEmpty(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = TransactionsUiState(),
        )

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

    fun delete(id: String) {
        viewModelScope.launch {
            deleteTransaction(id)
            AddTransactionWidget().updateAll(context)
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
