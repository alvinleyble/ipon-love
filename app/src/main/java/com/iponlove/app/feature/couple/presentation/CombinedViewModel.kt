package com.iponlove.app.feature.couple.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.date.DayGrouping
import com.iponlove.app.core.date.MonthWindow
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.entitlement.Scope
import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.usecase.DeleteBudgetUseCase
import com.iponlove.app.feature.budgets.domain.usecase.ObserveSharedBudgetUseCase
import com.iponlove.app.feature.budgets.domain.usecase.UpsertSharedBudgetUseCase
import com.iponlove.app.core.sync.SyncEngine
import com.iponlove.app.feature.categories.domain.usecase.ObserveAllCategoriesUseCase
import com.iponlove.app.feature.couple.domain.model.CombinedLedger
import com.iponlove.app.feature.couple.domain.usecase.CombinedLedgerCalculator
import com.iponlove.app.feature.couple.domain.usecase.ObserveCoupleMembersUseCase
import com.iponlove.app.feature.transactions.domain.model.OwnedTransaction
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

/**
 * Drives the combined couple view (ADR-0011): the merged transaction stream, per-member
 * monthly spend, and the couple's joint budget for the viewed month. The derived state is
 * recomputed on the fly from the live transaction + category + member + shared-budget streams;
 * only the budget editor and viewed month hold transient UI state. The viewed month defaults
 * to the current calendar month and steps independently of Records' own (ADR-0032), so
 * [CombinedLedgerCalculator] stays pure/testable.
 */
@HiltViewModel
class CombinedViewModel @Inject constructor(
    observeCombinedTransactions: ObserveCombinedTransactionsUseCase,
    observeTransactionImageUrls: ObserveTransactionImageUrlsUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    observeCoupleMembers: ObserveCoupleMembersUseCase,
    observeSharedBudget: ObserveSharedBudgetUseCase,
    observeHasAnyCombinedTransaction: ObserveHasAnyCombinedTransactionUseCase,
    private val upsertSharedBudget: UpsertSharedBudgetUseCase,
    private val deleteBudget: DeleteBudgetUseCase,
    private val syncEngine: SyncEngine,
    private val premiumGate: PremiumGate,
    private val analytics: Analytics,
) : ViewModel() {

    private val budgetEditor = MutableStateFlow<BudgetEditorState?>(null)
    private val isRefreshing = MutableStateFlow(false)

    /** The calendar month currently paged to (ADR-0032); independent of Records' own. */
    private val viewedMonth = MutableStateFlow(LocalDate.now(ZONE).withDayOfMonth(1))

    /** DEEP_HISTORY back-wall lock (individual scope, S10); false while dormant. Held as a
     *  StateFlow so [previousMonth] can read it synchronously as a backstop. */
    private val deepHistoryLocked: StateFlow<Boolean> =
        premiumGate.observeLocked(Scope.INDIVIDUAL)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    // Captured from the latest emission so the editor's save/clear can act without re-deriving:
    // the couple to stamp ownership on, the month to target, and the existing budget to reuse.
    private var coupleId: String? = null
    private var monthKey: String = YearMonth.now().toString()
    private var currentBudget: Budget? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val combinedTransactionsInRange: Flow<List<OwnedTransaction>> = viewedMonth.flatMapLatest { month ->
        val window = MonthWindow.windowFor(month, ZONE)
        observeCombinedTransactions(window.startInclusive, window.endExclusive)
    }

    // Pair the windowed transactions with the receipt-image map so the 5-arg combine below stays
    // within combine's typed arity while analyze() still gets both.
    private val transactionsWithImages: Flow<Pair<List<OwnedTransaction>, Map<String, List<String>>>> =
        combine(combinedTransactionsInRange, observeTransactionImageUrls()) { txns, images -> txns to images }

    val uiState: StateFlow<CombinedUiState> =
        combine(
            transactionsWithImages,
            observeAllCategories(),
            observeCoupleMembers(),
            observeSharedBudget(),
            viewedMonth,
        ) { txnsAndImages, categories, members, sharedBudgets, month ->
            val (transactions, imageUrls) = txnsAndImages
            if (members == null) {
                coupleId = null
                currentBudget = null
                return@combine CombinedUiState(isLoading = false, isPaired = false)
            }

            val window = MonthWindow.windowFor(month, ZONE)
            val ledger: CombinedLedger = CombinedLedgerCalculator.analyze(
                transactions = transactions,
                categoryNames = categories.associateBy({ it.id }, { it.name }),
                imageUrls = imageUrls,
                me = members.me,
                partner = members.partner,
                monthStartInclusive = window.startInclusive,
                monthEndExclusive = window.endExclusive,
            )

            coupleId = members.me.coupleId
            monthKey = YearMonth.from(month).toString()

            // The overall joint budget for the displayed month; combined spend = both members'
            // EXPENSE this month (the same per-member figures shown on the spending chips).
            val budget = sharedBudgets.firstOrNull { it.yearMonth == monthKey }
            currentBudget = budget
            val coupleBudget = budget?.let {
                val spent = ledger.members.fold(BigDecimal.ZERO) { acc, m -> acc + m.monthlyExpense }
                val fraction = if (it.amount.signum() > 0) {
                    (spent.toFloat() / it.amount.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                CoupleBudgetUi(
                    id = it.id,
                    limit = it.amount,
                    spent = spent,
                    remaining = it.amount - spent,
                    fraction = fraction,
                    isOverBudget = spent > it.amount,
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
                coupleBudget = coupleBudget,
            )
        }
            .combine(budgetEditor) { state, editor -> state.copy(budgetEditor = editor) }
            .combine(observeHasAnyCombinedTransaction()) { state, hasAnyEver ->
                state.copy(hasAnySharedActivityEver = hasAnyEver)
            }
            .combine(isRefreshing) { state, refreshing -> state.copy(isRefreshing = refreshing) }
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

    fun startEditBudget() {
        budgetEditor.value = BudgetEditorState(
            amountText = currentBudget?.amount?.toPlainString().orEmpty(),
            isEditing = currentBudget != null,
        )
    }

    fun onBudgetAmountChange(value: String) =
        budgetEditor.update { it?.copy(amountText = value, amountError = false) }

    fun cancelBudgetEdit() {
        budgetEditor.value = null
    }

    fun saveBudget() {
        val editor = budgetEditor.value ?: return
        val couple = coupleId ?: return
        val amount = editor.amountText.trim().toBigDecimalOrNull()
        if (amount == null || amount.signum() <= 0) {
            budgetEditor.value = editor.copy(amountError = true)
            return
        }
        // Reuse the existing budget row for this month so edits don't create a duplicate.
        val budget = Budget(
            id = currentBudget?.id ?: UUID.randomUUID().toString(),
            categoryId = null,
            amount = amount,
            yearMonth = monthKey,
        )
        viewModelScope.launch {
            upsertSharedBudget(budget, couple)
            budgetEditor.value = null
        }
    }

    fun clearBudget() {
        val id = currentBudget?.id ?: return
        viewModelScope.launch { deleteBudget(id) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val ZONE: ZoneId = ZoneId.systemDefault()
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    }
}
