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
    /** The couple's name for the identity banner (v1.7.0 Item 9 Slice B); null until it replicates. */
    val coupleName: String? = null,
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
    /**
     * The couple's shared budgets for the displayed month, **read-only** (Item 35 / ADR-0047):
     * a glanceable summary of joint-budget progress. Creating/editing shared budgets lives in the
     * Budgets tab; this list self-hides (empty) when the couple has no shared budgets this month.
     */
    val sharedBudgets: List<SharedBudgetSummaryUi> = emptyList(),
    /** Global "Hide amounts" flag (Item 15/Item 7) — the Spending header's eye reads/writes this. */
    val privacyModeEnabled: Boolean = false,
)

/** A read-only shared-budget summary line — its progress against the couple's combined spend. */
data class SharedBudgetSummaryUi(
    val id: String,
    /** Category name, or "Overall" for a no-category budget. */
    val title: String,
    val spent: BigDecimal,
    /** Rollover-adjusted limit (ADR-0036). */
    val limit: BigDecimal,
    val remaining: BigDecimal,
    /** 0f..1f, clamped, for the progress bar. */
    val fraction: Float,
    val isOverBudget: Boolean,
)
