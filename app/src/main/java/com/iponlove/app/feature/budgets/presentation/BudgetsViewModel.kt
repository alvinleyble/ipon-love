package com.iponlove.app.feature.budgets.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.core.entitlement.PremiumGate
import com.iponlove.app.core.ui.UpsellPrompt
import com.iponlove.app.feature.budgets.domain.model.Budget
import com.iponlove.app.feature.budgets.domain.usecase.BudgetLineId
import com.iponlove.app.feature.budgets.domain.usecase.BudgetRowsCalculator
import com.iponlove.app.feature.budgets.domain.usecase.CheckBudgetCapUseCase
import com.iponlove.app.feature.budgets.domain.usecase.DeleteBudgetUseCase
import com.iponlove.app.feature.budgets.domain.usecase.DuplicateBudgetToNextMonthUseCase
import com.iponlove.app.feature.budgets.domain.usecase.ObserveBudgetsUseCase
import com.iponlove.app.feature.budgets.domain.usecase.ObserveSharedBudgetUseCase
import com.iponlove.app.feature.budgets.domain.usecase.ResetBudgetRolloverUseCase
import com.iponlove.app.feature.budgets.domain.usecase.UpsertBudgetUseCase
import com.iponlove.app.feature.budgets.domain.usecase.UpsertSharedBudgetUseCase
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.categories.domain.model.CategoryType
import com.iponlove.app.feature.categories.domain.usecase.AnalysisExclusion
import com.iponlove.app.feature.categories.domain.usecase.ObserveCategoriesUseCase
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObserveMutedBudgetLinesUseCase
import com.iponlove.app.feature.settings.domain.usecase.ObservePrivacyModeUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetBudgetLineMutedUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetPrivacyModeUseCase
import com.iponlove.app.feature.transactions.domain.model.Transaction
import com.iponlove.app.feature.transactions.domain.usecase.ObserveCombinedTransactionsForBudgetsUseCase
import com.iponlove.app.feature.transactions.domain.usecase.ObserveTransactionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    observeSharedBudget: ObserveSharedBudgetUseCase,
    observeTransactions: ObserveTransactionsUseCase,
    observeCombinedTransactions: ObserveCombinedTransactionsForBudgetsUseCase,
    observeCategories: ObserveCategoriesUseCase,
    observePairingState: ObservePairingStateUseCase,
    private val observePrivacyMode: ObservePrivacyModeUseCase,
    private val setPrivacyMode: SetPrivacyModeUseCase,
    private val observeMutedBudgetLines: ObserveMutedBudgetLinesUseCase,
    private val setBudgetLineMuted: SetBudgetLineMutedUseCase,
    private val upsertBudget: UpsertBudgetUseCase,
    private val upsertSharedBudget: UpsertSharedBudgetUseCase,
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

    // The analytics source (§10.10) of the currently-shown upsell, so the "Get Premium" tap logs
    // the same key it was raised under — "budgets" (personal) or "shared_budgets".
    private var upsellSource = "budgets"

    // Budgets for the displayed month, split by scope, captured so save() can reuse the existing
    // row for the same category+month+scope instead of creating a duplicate.
    private var monthPersonal: List<Budget> = emptyList()
    private var monthShared: List<Budget> = emptyList()

    // Every budget across all months, split by scope, captured so duplicate/reset can look up a
    // category's history within its own scope without a fresh query.
    private var allPersonal: List<Budget> = emptyList()
    private var allShared: List<Budget> = emptyList()

    // The current couple id (null when unpaired) — needed to stamp ownership on a shared budget.
    private var coupleId: String? = null

    val uiState: StateFlow<BudgetsUiState> =
        combine(
            observeBudgets(),
            observeSharedBudget(),
            observeTransactions(),
            observeCombinedTransactions(),
            // Archived-inclusive so a budget row / rollover keeps the real category label after
            // that category is archived (v1.6.7 Item 5). The editor's category *picker*
            // ([expenseCategories]) re-excludes archived below, so archiving still hides it there.
            observeCategories(includeArchived = true),
        ) { personal, shared, ownTxns, combinedTxns, categories ->
            BudgetData(personal, shared, ownTxns, combinedTxns, categories)
        }.let { dataFlow ->
            val controlsFlow = combine(
                month,
                editor,
                upsell,
                premiumGate.observeLocked(),
            ) { displayedMonth, editorState, upsellState, rolloverLocked ->
                BudgetControls(displayedMonth, editorState, upsellState, rolloverLocked)
            }
            val pairingFlow = observePairingState().map { state ->
                when (state) {
                    is PairingState.Paired -> PairInfo(isPaired = true, coupleId = state.couple.id)
                    else -> PairInfo(isPaired = false, coupleId = null)
                }
            }
            combine(dataFlow, controlsFlow, pairingFlow, observePrivacyMode(), observeMutedBudgetLines()) {
                data, controls, pairing, privacyModeOn, mutedLines ->
                buildUiState(data, controls, pairing, privacyModeOn, mutedLines)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = BudgetsUiState(),
        )

    private fun buildUiState(
        data: BudgetData,
        controls: BudgetControls,
        pairing: PairInfo,
        privacyModeOn: Boolean,
        mutedLines: Set<String>,
    ): BudgetsUiState {
        val monthKey = controls.month.toString()
        val categoryNames = data.categories.associate { it.id to it.name }
        monthPersonal = data.personal.filter { it.yearMonth == monthKey }
        monthShared = data.shared.filter { it.yearMonth == monthKey }
        allPersonal = data.personal
        allShared = data.shared
        coupleId = pairing.coupleId

        // Pass-through categories (reimbursables, ADR-0049) never consume a budget — drop their
        // transactions before the calculator, on both the personal and shared (combined) sources.
        val excludedIds = AnalysisExclusion.excludedIds(data.categories)
        val rows = BudgetRowsCalculator.build(
            personalBudgets = data.personal,
            sharedBudgets = data.shared,
            ownTransactions = AnalysisExclusion.retainAnalyzable(data.ownTransactions, excludedIds) { it.categoryId },
            combinedTransactions = AnalysisExclusion.retainAnalyzable(data.combinedTransactions, excludedIds) { it.categoryId },
            categoryNames = categoryNames,
            monthKey = monthKey,
        ).map { it.toRow(mutedLines) }

        return BudgetsUiState(
            isLoading = false,
            monthLabel = controls.month.format(MONTH_FORMATTER),
            nextMonthShortLabel = controls.month.plusMonths(1).format(SHORT_MONTH_FORMATTER),
            rows = rows,
            // The create/edit picker offers active expense categories only — `data.categories` is
            // archived-inclusive now (for the label map above), so exclude archived here (Item 5).
            // Pass-through categories (ADR-0049) are excluded too — budgeting a reimbursable bucket
            // is meaningless (its spend is always ₱0 by the exclusion above).
            expenseCategories = data.categories.filter {
                it.type == CategoryType.EXPENSE && !it.isArchived && !it.excludeFromAnalysis
            },
            editor = controls.editor,
            upsell = controls.upsell,
            rolloverLocked = controls.rolloverLocked,
            isPaired = pairing.isPaired,
            privacyModeEnabled = privacyModeOn,
        )
    }

    private fun BudgetRowsCalculator.Row.toRow(mutedLines: Set<String>): BudgetRow = BudgetRow(
        id = id,
        categoryId = categoryId,
        title = title,
        spent = spent,
        baseAmount = baseAmount,
        limit = limit,
        remaining = remaining,
        fraction = fraction,
        isOverBudget = isOverBudget,
        rolloverEnabled = rolloverEnabled,
        carriedAmount = carriedAmount,
        isShared = isShared,
        isMuted = BudgetLineId.of(categoryId, isShared) in mutedLines,
    )

    fun togglePrivacyMode() {
        viewModelScope.launch { setPrivacyMode(!uiState.value.privacyModeEnabled) }
    }

    fun previousMonth() {
        month.value = month.value.minusMonths(1)
    }

    fun nextMonth() {
        month.value = month.value.plusMonths(1)
    }

    /**
     * Open the create-budget editor. The per-month cap (S7) is enforced at [save] now — the scope
     * (personal vs shared) is only known once the editor picks it, so the tier the cap reads can't
     * be decided here. Defaults to personal scope.
     */
    fun startCreate() {
        editor.value = BudgetEditorState()
    }

    fun dismissUpsell() {
        upsell.value = null
    }

    /** The upsell "Get Premium" tap — logs the funnel touchpoint (§10.10) before routing to paywall. */
    fun onUpsellUpgrade(): String {
        val source = upsellSource
        analytics.log("upsell_tap", source = source)
        upsell.value = null
        return source
    }

    fun startEdit(row: BudgetRow) {
        editor.value = BudgetEditorState(
            id = row.id,
            categoryId = row.categoryId,
            amountText = row.baseAmount.toPlainString(),
            rolloverEnabled = row.rolloverEnabled,
            shared = row.isShared,
        )
    }

    fun cancelEdit() {
        editor.value = null
    }

    fun onCategoryChange(categoryId: String?) = editor.update { it?.copy(categoryId = categoryId) }

    fun onAmountChange(value: String) = editor.update { it?.copy(amountText = value, amountError = false) }

    /** Choose Personal vs Shared scope — creation only (a no-op while editing, ADR-0047). */
    fun onScopeChange(shared: Boolean) = editor.update { current ->
        if (current == null || current.isEditing) current else current.copy(shared = shared)
    }

    fun onRolloverToggle(enabled: Boolean) {
        // Defensive: a locked toggle can't be flipped (the screen shows a locked switch that routes
        // to the paywall instead). An already-enabled budget keeps its rollover (T1 freeze — never
        // reset the user's data), it just can't be changed while locked.
        if (uiState.value.rolloverLocked) return
        editor.update { it?.copy(rolloverEnabled = enabled) }
    }

    /** A tap on the locked rollover toggle — logs the §10.10 funnel touchpoint before the screen
     *  routes to the paywall. */
    fun onRolloverLockedTap(): String {
        val source = "budget_rollover"
        analytics.log("upsell_tap", source = source)
        return source
    }

    fun save() {
        val s = editor.value ?: return
        val amount = s.amountText.trim().toBigDecimalOrNull()
        if (amount == null || amount.signum() <= 0) {
            editor.value = s.copy(amountError = true)
            return
        }
        // Shared budgets need a couple; if somehow chosen while unpaired, fall back to personal.
        val couple = coupleId
        val shared = s.shared && couple != null
        // Reuse an existing budget for the same category+month **within the same scope** instead of
        // duplicating (a personal and a shared budget for the same category are distinct rows).
        val monthInScope = if (shared) monthShared else monthPersonal
        val existingId = s.id ?: monthInScope.firstOrNull { it.categoryId == s.categoryId }?.id
        viewModelScope.launch {
            // Only a *new* budget hits the cap (an edit of an existing row never does).
            if (existingId == null) {
                when (val check = checkBudgetCap(month.value.toString(), shared = shared)) {
                    is CapCheck.Blocked -> {
                        upsellSource = if (shared) "shared_budgets" else "budgets"
                        upsell.value = UpsellPrompt(
                            entityLabel = if (shared) "shared budgets" else "budgets",
                            freeLimit = check.freeLimit,
                            premiumMax = check.premiumMax,
                        )
                        editor.value = null
                        return@launch
                    }
                    CapCheck.Allowed -> {}
                }
            }
            val budget = Budget(
                id = existingId ?: UUID.randomUUID().toString(),
                categoryId = s.categoryId,
                amount = amount,
                yearMonth = month.value.toString(),
                rolloverEnabled = s.rolloverEnabled,
                isShared = shared,
            )
            if (shared) upsertSharedBudget(budget, couple!!) else upsertBudget(budget)
            editor.value = null
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { deleteBudget(id) }
    }

    /** Toggles this row's line mute (ADR-0054 decisions 6-8) — local, per-device, survives months. */
    fun toggleMute(row: BudgetRow) {
        viewModelScope.launch {
            setBudgetLineMuted(BudgetLineId.of(row.categoryId, row.isShared), !row.isMuted)
        }
    }

    /** Resets this month's carried-in balance for this row's category (see use case doc). */
    fun resetRollover(row: BudgetRow) {
        val budget = (monthPersonal + monthShared).firstOrNull { it.id == row.id } ?: return
        viewModelScope.launch { resetBudgetRollover(budget) }
    }

    /** Copies this row's amount/rollover setting (and scope) into next month so it isn't retyped. */
    fun duplicateToNextMonth(row: BudgetRow) {
        val budget = (monthPersonal + monthShared).firstOrNull { it.id == row.id } ?: return
        val sameScopeAll = if (budget.isShared) allShared else allPersonal
        val sameCategoryBudgets = sameScopeAll.filter { it.categoryId == budget.categoryId }
        // Duplicating creates next month's row unless it already exists (then it's an in-place
        // update, no new row). Gate the cap only when it would actually create — otherwise the
        // "Duplicate for next month" action would silently bypass the budget cap (S7).
        val nextMonth = month.value.plusMonths(1).toString()
        val wouldCreate = sameCategoryBudgets.none { it.yearMonth == nextMonth }
        viewModelScope.launch {
            if (wouldCreate) {
                when (val check = checkBudgetCap(nextMonth, shared = budget.isShared)) {
                    is CapCheck.Blocked -> {
                        upsellSource = if (budget.isShared) "shared_budgets" else "budgets"
                        upsell.value = UpsellPrompt(
                            entityLabel = if (budget.isShared) "shared budgets" else "budgets",
                            freeLimit = check.freeLimit,
                            premiumMax = check.premiumMax,
                        )
                        return@launch
                    }
                    CapCheck.Allowed -> {}
                }
            }
            duplicateBudget(budget, sameCategoryBudgets, coupleId)
        }
    }

    /** The five reactive data inputs, bundled so the controls + pairing fold in via nested combines. */
    private data class BudgetData(
        val personal: List<Budget>,
        val shared: List<Budget>,
        val ownTransactions: List<Transaction>,
        val combinedTransactions: List<Transaction>,
        val categories: List<Category>,
    )

    /** The transient UI controls, bundled to keep each `combine` within its typed 5-flow arity. */
    private data class BudgetControls(
        val month: YearMonth,
        val editor: BudgetEditorState?,
        val upsell: UpsellPrompt?,
        val rolloverLocked: Boolean,
    )

    private data class PairInfo(val isPaired: Boolean, val coupleId: String?)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        val MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
        val SHORT_MONTH_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM")
    }
}
