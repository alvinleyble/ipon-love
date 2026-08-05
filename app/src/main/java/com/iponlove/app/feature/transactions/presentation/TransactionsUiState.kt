package com.iponlove.app.feature.transactions.presentation

import com.iponlove.app.core.date.DayGrouping
import com.iponlove.app.feature.transactions.domain.model.TransactionFilter
import com.iponlove.app.feature.transactions.domain.usecase.BulkDeletePlan
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.Instant

/**
 * Screen state for the Records tab. The editor now lives on its own route (see
 * [AddTransactionUiState]). Bounded to a single stepped calendar month, day-grouped for
 * sticky headers (ADR-0032) — [hasAnyTransactionEver] distinguishes a brand-new user's
 * empty state from an existing user with no activity in the viewed month.
 */
data class TransactionsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val monthLabel: String = "",
    val dayGroups: List<DayGrouping.DayGroup<TransactionListItem>> = emptyList(),
    val hasAnyTransactionEver: Boolean = false,
    /** A transaction needs at least one account to exist. */
    val canAdd: Boolean = false,
    /** False once paged to the current month — the ledger can't step into empty future months. */
    val canGoToNextMonth: Boolean = false,
    /**
     * False only at the DEEP_HISTORY −12mo back-wall while locked (S10) — the ← becomes an
     * "unlock older history" affordance routing to the paywall. Always true while dormant.
     */
    val canGoToPreviousMonth: Boolean = true,
    /** The currently-*applied* filter (v1.7.0 Item 7) — seeds the sheet's draft and drives the
     *  title-row active dot. [TransactionFilter.NONE] = unfiltered. */
    val appliedFilter: TransactionFilter = TransactionFilter.NONE,
    /** Convenience mirror of `appliedFilter.isActive` for the active dot + empty-state branch. */
    val filterIsActive: Boolean = false,
    /** True when the viewed month had rows *before* the filter ran — distinguishes the "No matches"
     *  empty state (rows exist but are filtered out) from a genuinely empty month (decision 8). */
    val hadRowsBeforeFilter: Boolean = false,
    /** Active (non-archived) categories offered as filter chips — archived entities keep their
     *  display label on a historical row but are not a filter criterion (decision 9). */
    val filterableCategories: List<FilterOption> = emptyList(),
    /** Active (non-archived) accounts offered as filter chips (decision 9). */
    val filterableAccounts: List<FilterOption> = emptyList(),
    /** Widget-adoption nudge card visibility (Item 11) — non-adopter + cooldown-elapsed gate.
     *  The screen latches this locally once true so the card doesn't vanish mid-visit the moment
     *  [TransactionsViewModel.onWidgetNudgeCardShown] stamps it as shown. */
    val showWidgetNudgeCard: Boolean = false,
    /** Ticked rows in multi-select mode (v1.7.3 Item 7). Empty = not in selection mode; there is
     *  deliberately no separate flag, so unticking the last row exits (ADR-0064 decision 6). */
    val selectedIds: Set<String> = emptySet(),
    /** Every row id currently rendered by [dayGroups] — the exact, and only, set select-all may
     *  tick (ADR-0064 decision 6: never all history). Derived here so the bound is inspectable in
     *  the ViewModel rather than assembled ad hoc in the composable. */
    val visibleIds: List<String> = emptyList(),
    /** Non-null while the bulk-delete confirmation is up — carries the counts it names. */
    val pendingBulkDelete: BulkDeletePlan? = null,
) {
    val selectionMode: Boolean get() = selectedIds.isNotEmpty()

    /** True when every visible row is ticked — flips the select-all affordance to "clear". */
    val allVisibleSelected: Boolean
        get() = visibleIds.isNotEmpty() && selectedIds.containsAll(visibleIds)
}

/** A pickable id/label pair for the filter sheet's category and account chip sections. */
data class FilterOption(val id: String, val label: String)

/** A transaction rendered for the list, with account/category ids resolved to names. */
data class TransactionListItem(
    val id: String,
    val type: TransactionType,
    val amount: BigDecimal,
    val title: String,
    val subtitle: String,
    val date: Instant,
    /** The filed category's icon/color key (v1.6.7 Item 8 Slice 6a), for the row's tinted icon
     *  squircle — null for transfers/settlements/uncategorized, which fall back to a letter avatar
     *  (same fallback AccountCard uses for icon-less accounts). */
    val categoryIcon: String? = null,
    val categoryColor: String? = null,
)
