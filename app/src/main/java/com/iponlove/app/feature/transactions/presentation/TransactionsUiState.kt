package com.iponlove.app.feature.transactions.presentation

import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.categories.domain.model.Category
import com.iponlove.app.feature.transactions.domain.model.TransactionType
import com.iponlove.app.feature.transactions.domain.usecase.TransactionError
import java.math.BigDecimal
import java.time.Instant

/** Screen state for the Records tab. */
data class TransactionsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val items: List<TransactionListItem> = emptyList(),
    /** Picker sources for the editor. */
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val editor: TransactionEditorState? = null,
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

/** Editor form state. [id] null means a new transaction; non-null means editing. */
data class TransactionEditorState(
    val id: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val accountId: String? = null,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val note: String = "",
    val isPrivate: Boolean = false,
    val date: Instant = Instant.now(),
    val errors: Set<TransactionError> = emptySet(),
) {
    val isEditing: Boolean get() = id != null
}
