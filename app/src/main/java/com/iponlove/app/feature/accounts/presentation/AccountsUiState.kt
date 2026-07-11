package com.iponlove.app.feature.accounts.presentation

import com.iponlove.app.core.ui.UpsellPrompt
import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.model.AccountType
import java.math.BigDecimal

/** Screen state for the Accounts tab. */
data class AccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<Account> = emptyList(),
    /** Current balance per account id (opening + ledger, ADR-0007). */
    val balances: Map<String, BigDecimal> = emptyMap(),
    /** Net assets across active (non-archived) accounts only — stable regardless of [showArchived]. */
    val netAssets: BigDecimal = BigDecimal.ZERO,
    /** When true the list also renders archived accounts (so they can be unarchived); default off. */
    val showArchived: Boolean = false,
    /** Whether any archived account exists — gates showing the "Show archived" toggle at all. */
    val hasArchived: Boolean = false,
    /** Whether the user is paired — gates the "Share with partner" action (ADR-0018). */
    val isPaired: Boolean = false,
    /** Non-null while the add/edit sheet is open. */
    val editor: AccountEditorState? = null,
    /** Non-null while the count-cap upsell sheet is showing (S7; only ever set under enforcement). */
    val upsell: UpsellPrompt? = null,
)

/**
 * Editor form state. [source] is the account being edited (null for a new one); it is
 * kept so a save preserves the fields the form doesn't touch (position, icon, color,
 * archived) instead of resetting them.
 */
data class AccountEditorState(
    val source: Account? = null,
    val name: String = "",
    val type: AccountType = AccountType.EWALLET,
    val openingBalanceText: String = "",
    val icon: String? = null,
    val color: String? = null,
    val nameError: Boolean = false,
) {
    val isEditing: Boolean get() = source != null
}
