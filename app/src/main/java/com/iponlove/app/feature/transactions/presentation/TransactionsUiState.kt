package com.iponlove.app.feature.transactions.presentation

import com.iponlove.app.core.date.DayGrouping
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
)

/** A transaction rendered for the list, with account/category ids resolved to names. */
data class TransactionListItem(
    val id: String,
    val type: TransactionType,
    val amount: BigDecimal,
    val title: String,
    val subtitle: String,
    val date: Instant,
)
