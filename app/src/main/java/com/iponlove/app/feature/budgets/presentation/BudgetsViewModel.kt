package com.iponlove.app.feature.budgets.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.ui.UpsellPrompt
import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.usecase.BudgetProgressCalculator
import com.iponlove.app.feature.budgets.domain.usecase.CheckBudgetCapUseCase
import com.iponlove.app.feature.budgets.domain.usecase.DeleteBudgetUseCase
import com.iponlove.app.feature.budgets.domain.usecase.DuplicateBudgetToNextMonthUseCase
import com.iponlove.app.feature.budgets.domain.usecase.ObserveBudgetsUseCase
import com.iponlove.app.feature.budgets.domain.usecase.ResetBudgetRolloverUseCase
import com.iponlove.app.feature.budgets.domain.usecase.UpsertBudgetUseCase
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ObserveTransactionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    observeBudgets: ObserveBudgetsUseCase,
    observeTransactions: ObserveTransactionsUseCase,
    observeCategories: ObserveCategoriesUseCase,
    private val upsertBudget: UpsertBudgetUseCase,
    private val deleteBudget: DeleteBudgetUseCase,
    private val resetBudgetRollover: ResetBudgetRolloverUseCase,
    private val duplicateBudget: DuplicateBudgetToNextMonthUseCase,
    private val checkBudgetCap: CheckBudgetCapUseCase,
    private val premiumGate: PremiumGate,
    private val analytics: Analytics,
) : ViewModel() {

    private val month = MutableStateFlow(YearMonth.now())
    private val editor = MutableStateFlow<BudgetEditorState?>(null)
    private val upsell = MutableStateFlow<UpsellPrompt?>(null)

    // Budgets for the displayed month, captured so a new budget can update the existing
    // one for the same category instead of creating a duplicate.
    private var monthBudgets: List<Budget> = emptyList()

    // Every personal budget across all months, captured so effectiveLimit/reset-rollover/
    // duplicate can look up a category's history without a fresh query.
    private var allBudgets: List<Budget> = emptyList()

    val uiState: StateFlow<BudgetsUiState> =
        combine(
            observeBudgets(),
            observeTransactions(),
            observeCategories(),
            month,
            editor,
        ) { budgets, transactions, categories, displayedMonth, editorState ->
            val monthKey = displayedMonth.toString()
            val categoryNames = categories.associate { it.id to it.name }
            val current = budgets.filter { it.yearMonth == monthKey }
            monthBudgets = current
            allBudgets = budgets

            val rows = current.map { budget ->
                val spent = BudgetProgressCalculator.spent(budget, transactions)
                val sameCategoryBudgets = budgets.filter { it.categoryId == budget.categoryId }
                val limit = BudgetProgressCalculator.effectiveLimit(budget, sameCategoryBudgets, transactions)
                val fraction = if (limit.signum() > 0) {
                    (spent.toFloat() / limit.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                BudgetRow(
                    id = budget.id,
                    categoryId = budget.categoryId,
                    title = budget.categoryId?.let { categoryNames[it] ?: "Category" } ?: "Overall",
                    spent = spent,
                    baseAmount = budget.amount,
                    limit = limit,
                    remaining = limit - spent,
                    fraction = fraction,
                    isOverBudget = spent > limit,
                    rolloverEnabled = budget.rolloverEnabled,
                    carriedAmount = limit - budget.amount,
                )
            }

            BudgetsUiState(
                isLoading = false,
                monthLabel = displayedMonth.format(MONTH_FORMATTER),
                nextMonthShortLabel = displayedMonth.plusMonths(1).format(SHORT_MONTH_FORMATTER),
                rows = rows,
                expenseCategories = categories.filter { it.type == CategoryType.EXPENSE },
                editor = editorState,
            )
            // Combine already carries the 5-arg maximum; fold the upsell + rollover-lock flows in
            // a second step.
        }.let { base ->
            combine(base, upsell, premiumGate.observeLocked()) { state, upsellState, rolloverLocked ->
                state.copy(upsell = upsellState, rolloverLocked = rolloverLocked)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = BudgetsUiState(),
        )

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }

    fun startCreate() {
        // Gate the per-month budgets cap at create-intent (S7) for the displayed month; Allowed
        // with enforcement off or under the cap.
        viewModelScope.launch {
            when (val check = checkBudgetCap(month.value.toString())) {
                CapCheck.Allowed -> editor.value = BudgetEditorState()
                is CapCheck.Blocked -> {
                    upsell.value = UpsellPrompt("budgets", check.freeLimit, check.premiumMax)
                }
            }
        }
    }

    fun dismissUpsell() {
        upsell.value = null
    }

    /** The upsell "Get Premium" tap — logs the funnel touchpoint (§10.10) before routing to paywall. */
    fun onUpsellUpgrade() {
        analytics.log("upsell_tap", source = "budgets")
        upsell.value = null
    }

    fun startEdit(row: BudgetRow) {
        editor.value = BudgetEditorState(
            id = row.id,
            categoryId = row.categoryId,
            amountText = row.baseAmount.toPlainString(),
            rolloverEnabled = row.rolloverEnabled,
        )
    }

    fun cancelEdit() {
        editor.value = null
    }

    fun onCategoryChange(categoryId: String?) = editor.update { it?.copy(categoryId = categoryId) }

    fun onAmountChange(value: String) = editor.update { it?.copy(amountText = value, amountError = false) }

    fun onRolloverToggle(enabled: Boolean) {
        // Defensive: a locked toggle can't be flipped (the screen shows a locked switch that routes
        // to the paywall instead). An already-enabled budget keeps its rollover (T1 freeze — never
        // reset the user's data), it just can't be changed while locked.
        if (uiState.value.rolloverLocked) return
        editor.update { it?.copy(rolloverEnabled = enabled) }
    }

    /** A tap on the locked rollover toggle — logs the §10.10 funnel touchpoint before the screen
     *  routes to the paywall. */
    fun onRolloverLockedTap() {
        analytics.log("upsell_tap", source = "budget_rollover")
    }

    fun save() {
        val s = editor.value ?: return
        val amount = s.amountText.trim().toBigDecimalOrNull()
        if (amount == null || amount.signum() <= 0) {
            editor.value = s.copy(amountError = true)
            return
        }
        // Reuse an existing budget for the same category+month instead of duplicating.
        val existingId = s.id ?: monthBudgets.firstOrNull { it.categoryId == s.categoryId }?.id
        val budget = Budget(
            id = existingId ?: UUID.randomUUID().toString(),
            categoryId = s.categoryId,
            amount = amount,
            yearMonth = month.value.toString(),
            rolloverEnabled = s.rolloverEnabled,
        )
        viewModelScope.launch {
            upsertBudget(budget)
            editor.value = null
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { deleteBudget(id) }
    }

    /** Resets this month's carried-in balance for this row's category (see use case doc). */
    fun resetRollover(row: BudgetRow) {
        val budget = monthBudgets.firstOrNull { it.id == row.id } ?: return
        viewModelScope.launch { resetBudgetRollover(budget) }
    }

    /** Copies this row's amount/rollover setting into next month so it isn't retyped by hand. */
    fun duplicateToNextMonth(row: BudgetRow) {
        val budget = monthBudgets.firstOrNull { it.id == row.id } ?: return
        val sameCategoryBudgets = allBudgets.filter { it.categoryId == budget.categoryId }
        // Duplicating creates next month's row unless it already exists (then it's an in-place
        // update, no new row). Gate the cap only when it would actually create — otherwise the
        // "Duplicate for next month" action would silently bypass the budget cap (S7).
        val nextMonth = month.value.plusMonths(1).toString()
        val wouldCreate = sameCategoryBudgets.none { it.yearMonth == nextMonth }
        viewModelScope.launch {
            if (wouldCreate) {
                when (val check = checkBudgetCap(nextMonth)) {
                    is CapCheck.Blocked -> {
                        upsell.value = UpsellPrompt("budgets", check.freeLimit, check.premiumMax)
                        return@launch
                    }
                    CapCheck.Allowed -> {}
                }
            }
            duplicateBudget(budget, sameCategoryBudgets)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
        val SHORT_MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM")
    }
}
