package com.iponlove.app.feature.couple.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.usecase.DeleteBudgetUseCase
import com.iponlove.app.feature.budgets.domain.usecase.ObserveSharedBudgetUseCase
import com.iponlove.app.feature.budgets.domain.usecase.UpsertSharedBudgetUseCase
import com.iponlove.app.feature.categories.domain.usecase.ObserveAllCategoriesUseCase
import com.iponlove.app.feature.couple.domain.usecase.CombinedLedgerCalculator
import com.iponlove.app.feature.couple.domain.usecase.ObserveCoupleMembersUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ObserveCombinedTransactionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
 * monthly spend, and the couple's joint budget for the current month. The derived state is
 * recomputed on the fly from the live transaction + category + member + shared-budget streams;
 * only the budget editor holds transient UI state. The current-month window is computed from
 * the system clock here so [CombinedLedgerCalculator] stays pure/testable.
 */
@HiltViewModel
class CombinedViewModel @Inject constructor(
    observeCombinedTransactions: ObserveCombinedTransactionsUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    observeCoupleMembers: ObserveCoupleMembersUseCase,
    observeSharedBudget: ObserveSharedBudgetUseCase,
    private val upsertSharedBudget: UpsertSharedBudgetUseCase,
    private val deleteBudget: DeleteBudgetUseCase,
) : ViewModel() {

    private val budgetEditor = MutableStateFlow<BudgetEditorState?>(null)

    // Captured from the latest emission so the editor's save/clear can act without re-deriving:
    // the couple to stamp ownership on, the month to target, and the existing budget to reuse.
    private var coupleId: String? = null
    private var monthKey: String = YearMonth.now().toString()
    private var currentBudget: Budget? = null

    val uiState: StateFlow<CombinedUiState> =
        combine(
            observeCombinedTransactions(),
            observeAllCategories(),
            observeCoupleMembers(),
            observeSharedBudget(),
            budgetEditor,
        ) { transactions, categories, members, sharedBudgets, editor ->
            if (members == null) {
                coupleId = null
                currentBudget = null
                return@combine CombinedUiState(isLoading = false, isPaired = false)
            }

            val zone = ZoneId.systemDefault()
            val firstOfMonth = LocalDate.now(zone).withDayOfMonth(1)
            val monthStart = firstOfMonth.atStartOfDay(zone).toInstant()
            val monthEnd = firstOfMonth.plusMonths(1).atStartOfDay(zone).toInstant()

            val ledger = CombinedLedgerCalculator.analyze(
                transactions = transactions,
                categoryNames = categories.associateBy({ it.id }, { it.name }),
                me = members.me,
                partner = members.partner,
                monthStartInclusive = monthStart,
                monthEndExclusive = monthEnd,
            )

            coupleId = members.me.coupleId
            monthKey = YearMonth.from(firstOfMonth).toString()

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

            CombinedUiState(
                isLoading = false,
                isPaired = true,
                monthLabel = firstOfMonth.format(MONTH_FORMAT),
                members = ledger.members,
                entries = ledger.entries,
                coupleBudget = coupleBudget,
                budgetEditor = editor,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = CombinedUiState(),
        )

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
        val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    }
}
