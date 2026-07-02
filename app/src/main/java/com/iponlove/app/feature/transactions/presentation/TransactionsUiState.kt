package com.iponlove.app.feature.transactions.presentation

import com.iponlove.app.feature.transactions.domain.model.TransactionType
import java.math.BigDecimal
import java.time.Instant

/** Screen state for the Records tab. The editor now lives on its own route (see [AddTransactionUiState]). */
data class TransactionsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val items: List<TransactionListItem> = emptyList(),
    /** A transaction needs at least one account to exist. */
    val canAdd: Boolean = false,
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
