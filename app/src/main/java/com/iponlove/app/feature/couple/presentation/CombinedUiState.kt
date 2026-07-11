package com.iponlove.app.feature.couple.presentation

import com.iponlove.app.core.date.DayGrouping
import com.iponlove.app.feature.couple.domain.model.CombinedEntry
import com.iponlove.app.feature.couple.domain.model.MemberSpend
import java.math.BigDecimal

/**
 * Screen state for the combined couple view. [isPaired] is false when the user has no
 * partner (the screen is normally only reachable while paired, but guards the edge). Bounded
 * to a single stepped calendar month, day-grouped for sticky headers (ADR-0032) —
 * [hasAnySharedActivityEver] distinguishes "never shared anything" from "nothing shared this
 * month."
 */
data class CombinedUiState(
    val isLoading: Boolean = true,
    val isPaired: Boolean = false,
    val isRefreshing: Boolean = false,
    val monthLabel: String = "",
    val members: List<MemberSpend> = emptyList(),
    val dayGroups: List<DayGrouping.DayGroup<CombinedEntry>> = emptyList(),
    val hasAnySharedActivityEver: Boolean = false,
    /** False once paged to the current month — the ledger can't step into empty future months. */
    val canGoToNextMonth: Boolean = false,
    /**
     * False only at the DEEP_HISTORY −12mo back-wall while locked (S10) — the ← becomes an
     * "unlock older history" affordance routing to the paywall. Always true while dormant.
     */
    val canGoToPreviousMonth: Boolean = true,
    /** The couple's joint budget for the displayed month, or null if none is set yet. */
    val coupleBudget: CoupleBudgetUi? = null,
    /** Non-null while the set/edit budget dialog is open. */
    val budgetEditor: BudgetEditorState? = null,
)

/** The joint budget with its progress against the couple's combined spend this month. */
data class CoupleBudgetUi(
    val id: String,
    val limit: BigDecimal,
    val spent: BigDecimal,
    val remaining: BigDecimal,
    /** 0f..1f, clamped, for the progress bar. */
    val fraction: Float,
    val isOverBudget: Boolean,
)

/** Editor form state for the joint monthly budget (overall — no category in V1). */
data class BudgetEditorState(
    val amountText: String = "",
    val amountError: Boolean = false,
    val isEditing: Boolean = false,
)
