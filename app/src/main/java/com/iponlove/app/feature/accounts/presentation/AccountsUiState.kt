package com.iponlove.app.feature.accounts.presentation

import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.accounts.domain.model.AccountType
import java.math.BigDecimal

/** Screen state for the Accounts tab. */
data class AccountsUiState(
    val isLoading: Boolean = true,
    val accounts: List<Account> = emptyList(),
    /** Current balance per account id (opening + ledger, ADR-0007). */
    val balances: Map<String, BigDecimal> = emptyMap(),
    /** Whether the user is paired — gates the "Share with partner" action (ADR-0018). */
    val isPaired: Boolean = false,
    /** Non-null while the add/edit sheet is open. */
    val editor: AccountEditorState? = null,
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
