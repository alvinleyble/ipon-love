package com.iponlove.app.feature.transactions.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.widget.presentation.Widgets
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.date.DayGrouping
import com.iponlove.app.core.date.MonthWindow
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import com.iponlove.app.core.sync.SyncEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import com.iponlove.app.feature.accounts.domain.usecase.ObserveAccountsUseCase
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObservePrivacyModeUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetPrivacyModeUseCase
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
    private val premiumGate: PremiumGate,
    private val analytics: Analytics,
    observePrivacyMode: ObservePrivacyModeUseCase,
    private val setPrivacyMode: SetPrivacyModeUseCase,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)

    /** The calendar month currently paged to (ADR-0032); independent of Combined's own. */
    private val viewedMonth = MutableStateFlow(LocalDate.now(ZONE).withDayOfMonth(1))

    /** DEEP_HISTORY back-wall lock (individual scope, S10); false while dormant. Held as a
     *  StateFlow so [previousMonth] can read it synchronously as a backstop. */
    private val deepHistoryLocked: StateFlow<Boolean> =
        premiumGate.observeLocked(Scope.INDIVIDUAL)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

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
            // Icon/color keys for the Records row's tinted icon squircle (v1.6.7 Item 8 Slice 6a) —
            // only categories that set one contribute a map entry; toListItem's lookup is then a
            // plain miss (null) for icon-less categories, transfers, and settlements alike.
            val categoryIcons = categories.mapNotNull { c -> c.icon?.let { c.id to it } }.toMap()
            val categoryColors = categories.mapNotNull { c -> c.color?.let { c.id to it } }.toMap()
            val today = LocalDate.now(ZONE)
            val isCurrentMonth = YearMonth.from(month) == YearMonth.from(today)

            TransactionsUiState(
                isLoading = false,
                monthLabel = month.format(MONTH_FORMAT),
                dayGroups = DayGrouping.groupByDay(
                    items = transactions.map {
                        it.toListItem(accountNames, categoryNames, categoryIcons, categoryColors)
                    },
                    dateOf = { it.date },
                    zone = ZONE,
                    today = today,
                    isCurrentMonth = isCurrentMonth,
                ),
                hasAnyTransactionEver = hasAnyEver,
                canAdd = accounts.isNotEmpty(),
                canGoToNextMonth = MonthWindow.canStepForward(month, today),
            )
        }.combine(isRefreshing) { state, refreshing -> state.copy(isRefreshing = refreshing) }
            // DEEP_HISTORY back-wall (S10). viewedMonth.value is read fresh — the same anchor that
            // produced `state`. Always allowed while dormant (locked = false).
            .combine(deepHistoryLocked) { state, locked ->
                state.copy(
                    canGoToPreviousMonth =
                        MonthWindow.canStepBack(viewedMonth.value, LocalDate.now(ZONE), locked),
                )
            }
            // Records' privacy eye (v1.6.7 Item 8 Slice 6a) — the top-level combine above is
            // already at its 5-flow ceiling (matching the Item 9/25/Combined precedent), so this
            // rides a trailing .combine like deepHistoryLocked just above.
            .combine(observePrivacyMode()) { state, privacyModeOn ->
                state.copy(privacyModeEnabled = privacyModeOn)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = TransactionsUiState(),
            )

    fun previousMonth() {
        // Backstop for the DEEP_HISTORY back-wall (the ← is also lock-affordanced in the UI).
        if (!MonthWindow.canStepBack(viewedMonth.value, LocalDate.now(ZONE), deepHistoryLocked.value)) return
        viewedMonth.value = MonthWindow.step(viewedMonth.value, forward = false)
    }

    /** A tap on the locked ← at the −12mo wall — logs the §10.10 touchpoint before the screen
     *  routes to the paywall; the month does not move. */
    fun onDeepHistoryUpsell(): String {
        val source = "deep_history"
        analytics.log("upsell_tap", source = source)
        return source
    }

    fun nextMonth() {
        val current = viewedMonth.value
        if (MonthWindow.canStepForward(current, LocalDate.now(ZONE))) {
            viewedMonth.value = MonthWindow.step(current, forward = true)
        }
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
            Widgets.updateAll(context)
        }
    }

    /** Flips the *global* Privacy mode (Item 15) — not a local reveal. Matches the
     *  Accounts/Combined eye wiring (see the `feedback-privacy-eye-is-global` convention). */
    fun togglePrivacyMode() {
        viewModelScope.launch { setPrivacyMode(!uiState.value.privacyModeEnabled) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val ZONE: ZoneId = ZoneId.systemDefault()
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    }
}

/**
 * Maps a [Transaction] to its Records row. Extracted from the ViewModel so the display
 * logic (in particular the category-less label branches) is unit-testable without Hilt.
 * [categoryIcons]/[categoryColors] (v1.6.7 Item 8 Slice 6a) default to empty so existing callers
 * (and [TransactionsListItemMapperTest]) keep compiling unchanged — transfers/settlements never
 * carry a category, so they fall back to the row's letter-avatar treatment regardless.
 */
internal fun Transaction.toListItem(
    accountNames: Map<String, String>,
    categoryNames: Map<String, String>,
    categoryIcons: Map<String, String> = emptyMap(),
    categoryColors: Map<String, String> = emptyMap(),
): TransactionListItem {
    val accountName = accountNames[accountId] ?: "Account"
    val noteSuffix = note?.takeIf { it.isNotBlank() }?.let { "  •  $it" }.orEmpty()
    return when {
        type == TransactionType.TRANSFER -> TransactionListItem(
            id = id,
            type = type,
            amount = amount,
            title = "Transfer",
            subtitle = "$accountName → ${accountNames[toAccountId] ?: "Account"}$noteSuffix",
            date = date,
        )
        // Debt settlement legs carry categoryId = null + isSettlement = true by design
        // (ADR-0019 #14 / ADR-0042). Label them off the flag instead of falling to
        // "Uncategorized", mirroring the TRANSFER branch above.
        isSettlement -> TransactionListItem(
            id = id,
            type = type,
            amount = amount,
            title = "Debt settlement",
            subtitle = "$accountName$noteSuffix",
            date = date,
        )
        else -> TransactionListItem(
            id = id,
            type = type,
            amount = amount,
            title = categoryNames[categoryId] ?: "Uncategorized",
            subtitle = "$accountName$noteSuffix",
            date = date,
            categoryIcon = categoryId?.let { categoryIcons[it] },
            categoryColor = categoryId?.let { categoryColors[it] },
        )
    }
}
