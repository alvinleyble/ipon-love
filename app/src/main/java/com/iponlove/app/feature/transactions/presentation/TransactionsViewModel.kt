package com.iponlove.app.feature.transactions.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.glance.appwidget.updateAll
import com.iponlove.app.core.date.DayGrouping
import com.iponlove.app.core.date.MonthWindow
import com.iponlove.app.core.sync.SyncEngine
import com.iponlove.app.feature.widget.presentation.AddTransactionWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountsUseCase
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.DeleteTransactionUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ObserveHasAnyTransactionUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ObserveTransactionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    observeTransactions: ObserveTransactionsUseCase,
    observeAccounts: ObserveAccountsUseCase,
    observeCategories: ObserveCategoriesUseCase,
    observeHasAnyTransaction: ObserveHasAnyTransactionUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val syncEngine: SyncEngine,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)

    /** The calendar month currently paged to (ADR-0032); independent of Combined's own. */
    private val viewedMonth = MutableStateFlow(LocalDate.now(ZONE).withDayOfMonth(1))

    @OptIn(ExperimentalCoroutinesApi::class)
    private val transactionsInRange: Flow<List<Transaction>> = viewedMonth.flatMapLatest { month ->
        val window = MonthWindow.windowFor(month, ZONE)
        observeTransactions(window.startInclusive, window.endExclusive)
    }

    val uiState: StateFlow<TransactionsUiState> =
        combine(
            transactionsInRange,
            observeAccounts(),
            observeCategories(),
            observeHasAnyTransaction(),
            viewedMonth,
        ) { transactions, accounts, categories, hasAnyEver, month ->
            val accountNames = accounts.associate { it.id to it.name }
            val categoryNames = categories.associate { it.id to it.name }
            val today = LocalDate.now(ZONE)
            val isCurrentMonth = YearMonth.from(month) == YearMonth.from(today)

            TransactionsUiState(
                isLoading = false,
                monthLabel = month.format(MONTH_FORMAT),
                dayGroups = DayGrouping.groupByDay(
                    items = transactions.map { it.toListItem(accountNames, categoryNames) },
                    dateOf = { it.date },
                    zone = ZONE,
                    today = today,
                    isCurrentMonth = isCurrentMonth,
                ),
                hasAnyTransactionEver = hasAnyEver,
                canAdd = accounts.isNotEmpty(),
            )
        }.combine(isRefreshing) { state, refreshing -> state.copy(isRefreshing = refreshing) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = TransactionsUiState(),
            )

    fun previousMonth() {
        viewedMonth.value = MonthWindow.step(viewedMonth.value, forward = false)
    }

    fun nextMonth() {
        viewedMonth.value = MonthWindow.step(viewedMonth.value, forward = true)
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
        val ZONE: ZoneId = ZoneId.systemDefault()
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    }
}
