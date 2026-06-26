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

/** Editor form state. [isEditing] true means updating an existing transaction. */
data class TransactionEditorState(
    /** Always pre-generated so the receipt file can be named before save. */
    val id: String,
    val isEditing: Boolean = false,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountText: String = "",
    val accountId: String? = null,
    val toAccountId: String? = null,
    val categoryId: String? = null,
    val note: String = "",
    val isPrivate: Boolean = false,
    val date: Instant = Instant.now(),
    val errors: Set<TransactionError> = emptySet(),
    /** Local path of a receipt picked this session, pending upload. */
    val attachmentLocalPath: String? = null,
    /** Existing server URL loaded when editing a transaction that already has a receipt. */
    val attachmentUrl: String? = null,
    /**
     * True only when creating an EXPENSE while paired with a partner whose row is available —
     * gates the "Paid for partner" affordance (ADR-0019 #12). False when editing, since the
     * debt is fire-and-forget and must not be re-created on edit.
     */
    val canPayForPartner: Boolean = false,
    /** Partner's display name, for the toggle label; "Partner" fallback. */
    val partnerName: String = "Partner",
    /** "Paid for partner" toggle: on save, also creates a partner debt for [amountOwedText]. */
    val paidForPartner: Boolean = false,
    /** What the partner owes; defaults to the full amount, editable down. Blank = full amount. */
    val amountOwedText: String = "",
    val amountOwedError: Boolean = false,
)
