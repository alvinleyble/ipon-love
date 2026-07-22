package com.iponlove.app.feature.couple.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.date.DayGrouping
import com.iponlove.app.core.date.MonthWindow
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import com.iponlove.app.feature.budgets.domain.usecase.BudgetRowsCalculator
import com.iponlove.app.feature.budgets.domain.usecase.ObserveSharedBudgetUseCase
import com.iponlove.app.core.sync.SyncEngine
import com.iponlove.app.feature.categories.domain.usecase.AnalysisExclusion
import com.iponlove.app.feature.categories.domain.usecase.ObserveAllCategoriesUseCase
import com.iponlove.app.feature.couple.domain.model.CombinedLedger
import com.iponlove.app.feature.couple.domain.usecase.CombinedLedgerCalculator
import com.iponlove.app.feature.couple.domain.usecase.ObserveCoupleMembersUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObservePrivacyModeUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetPrivacyModeUseCase
import com.iponlove.app.feature.transactions.domain.model.OwnedTransaction
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.usecase.ObserveCombinedTransactionsForBudgetsUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ObserveCombinedTransactionsUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ObserveHasAnyCombinedTransactionUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ObserveTransactionImageUrlsUseCase
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

/**
 * Drives the combined couple view (ADR-0011): the merged transaction stream, per-member monthly
 * spend, and a **read-only** summary of the couple's shared budgets for the viewed month (Item 35 /
 * ADR-0047 — creating/editing shared budgets lives in the Budgets tab). The derived state is
 * recomputed on the fly from the live transaction + category + member + shared-budget streams; only
 * the viewed month holds transient UI state. The viewed month defaults to the current calendar
 * month and steps independently of Records' own (ADR-0032), so [CombinedLedgerCalculator] stays
 * pure/testable.
 */
@HiltViewModel
class CombinedViewModel @Inject constructor(
    observeCombinedTransactions: ObserveCombinedTransactionsUseCase,
    observeCombinedTransactionsForBudgets: ObserveCombinedTransactionsForBudgetsUseCase,
    observeTransactionImageUrls: ObserveTransactionImageUrlsUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    observeCoupleMembers: ObserveCoupleMembersUseCase,
    observeSharedBudget: ObserveSharedBudgetUseCase,
    observeHasAnyCombinedTransaction: ObserveHasAnyCombinedTransactionUseCase,
    observePrivacyMode: ObservePrivacyModeUseCase,
    private val setPrivacyMode: SetPrivacyModeUseCase,
    private val syncEngine: SyncEngine,
    private val premiumGate: PremiumGate,
    private val analytics: Analytics,
) : ViewModel() {

    private val isRefreshing = MutableStateFlow(false)

    /** The calendar month currently paged to (ADR-0032); independent of Records' own. */
    private val viewedMonth = MutableStateFlow(LocalDate.now(ZONE).withDayOfMonth(1))

    /** DEEP_HISTORY back-wall lock (individual scope, S10); false while dormant. Held as a
     *  StateFlow so [previousMonth] can read it synchronously as a backstop. */
    private val deepHistoryLocked: StateFlow<Boolean> =
        premiumGate.observeLocked(Scope.INDIVIDUAL)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val combinedTransactionsInRange: Flow<List<OwnedTransaction>> = viewedMonth.flatMapLatest { month ->
        val window = MonthWindow.windowFor(month, ZONE)
        observeCombinedTransactions(window.startInclusive, window.endExclusive)
    }

    // Bundle the windowed transactions with the receipt-image map and the unbounded combined ledger
    // (for shared-budget rollover), so the 5-arg combine below stays within combine's typed arity.
    private val txBundle: Flow<TxBundle> =
        combine(
            combinedTransactionsInRange,
            observeTransactionImageUrls(),
            observeCombinedTransactionsForBudgets(),
        ) { windowed, images, allCombined -> TxBundle(windowed, images, allCombined) }

    val uiState: StateFlow<CombinedUiState> =
        combine(
            txBundle,
            observeAllCategories(),
            observeCoupleMembers(),
            observeSharedBudget(),
            viewedMonth,
        ) { bundle, categories, members, sharedBudgets, month ->
            if (members == null) {
                return@combine CombinedUiState(isLoading = false, isPaired = false)
            }

            val window = MonthWindow.windowFor(month, ZONE)
            val categoryNames = categories.associateBy({ it.id }, { it.name })
            // Pass-through categories (reimbursables, ADR-0049) — including the partner's, whose flag
            // now crosses via the partner_categories redacting view — aren't shared household spending,
            // so drop them from both the combined feed/spend and the shared-budget summary below.
            val excludedIds = AnalysisExclusion.excludedIds(categories)
            val ledger: CombinedLedger = CombinedLedgerCalculator.analyze(
                transactions = AnalysisExclusion.retainAnalyzable(bundle.windowed, excludedIds) { it.transaction.categoryId },
                categoryNames = categoryNames,
                imageUrls = bundle.imageUrls,
                me = members.me,
                partner = members.partner,
                monthStartInclusive = window.startInclusive,
                monthEndExclusive = window.endExclusive,
            )

            val monthKey = YearMonth.from(month).toString()
            // The couple's shared budgets for the displayed month — read-only summary. Reuses the
            // Budgets tab's calculator (personal side empty) so progress/rollover match exactly:
            // combined non-private spend, per category, calendar-month cycle.
            val sharedSummaries = BudgetRowsCalculator.build(
                personalBudgets = emptyList(),
                sharedBudgets = sharedBudgets,
                ownTransactions = emptyList(),
                combinedTransactions = AnalysisExclusion.retainAnalyzable(bundle.allCombined, excludedIds) { it.categoryId },
                categoryNames = categoryNames,
                monthKey = monthKey,
                zone = ZONE,
            ).map {
                SharedBudgetSummaryUi(
                    id = it.id,
                    title = it.title,
                    spent = it.spent,
                    limit = it.limit,
                    remaining = it.remaining,
                    fraction = it.fraction,
                    isOverBudget = it.isOverBudget,
                )
            }

            val today = LocalDate.now(ZONE)
            val isCurrentMonth = YearMonth.from(month) == YearMonth.from(today)

            CombinedUiState(
                isLoading = false,
                isPaired = true,
                monthLabel = month.format(MONTH_FORMAT),
                members = ledger.members,
                dayGroups = DayGrouping.groupByDay(
                    items = ledger.entries,
                    dateOf = { it.date },
                    zone = ZONE,
                    today = today,
                    isCurrentMonth = isCurrentMonth,
                ),
                canGoToNextMonth = MonthWindow.canStepForward(month, today),
                sharedBudgets = sharedSummaries,
            )
        }
            .combine(observeHasAnyCombinedTransaction()) { state, hasAnyEver ->
                state.copy(hasAnySharedActivityEver = hasAnyEver)
            }
            .combine(isRefreshing) { state, refreshing -> state.copy(isRefreshing = refreshing) }
            .combine(observePrivacyMode()) { state, privacyModeOn ->
                state.copy(privacyModeEnabled = privacyModeOn)
            }
            // DEEP_HISTORY back-wall (S10). viewedMonth.value is read fresh — the same anchor that
            // produced `state`. Always allowed while dormant (locked = false).
            .combine(deepHistoryLocked) { state, locked ->
                state.copy(
                    canGoToPreviousMonth =
                        MonthWindow.canStepBack(viewedMonth.value, LocalDate.now(ZONE), locked),
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = CombinedUiState(),
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

    fun togglePrivacyMode() {
        viewModelScope.launch { setPrivacyMode(!uiState.value.privacyModeEnabled) }
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

    /** The windowed ledger + receipt images + unbounded combined ledger, bundled to keep the main
     *  combine within combine's typed 5-flow arity. */
    private data class TxBundle(
        val windowed: List<OwnedTransaction>,
        val imageUrls: Map<String, List<String>>,
        val allCombined: List<Transaction>,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val ZONE: ZoneId = ZoneId.systemDefault()
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    }
}
