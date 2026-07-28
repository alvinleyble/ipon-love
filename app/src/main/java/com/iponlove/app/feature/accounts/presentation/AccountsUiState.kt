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
    /** Non-null while the delete-confirm dialog is open (v1.6.7 Item 5). */
    val pendingDelete: PendingAccountDelete? = null,
)

/**
 * An account the user has asked to delete, with how many active transactions reference it on
 * either leg ([transactionCount], incl. transfer destinations). >0 shows the archive-steering
 * confirm (deleting removes it from balance + orphans those rows); 0 shows the plain confirm.
 * (v1.6.7 Item 5)
 */
data class PendingAccountDelete(
    val id: String,
    val name: String,
    val transactionCount: Int,
)

/**
 * Editor form state. [source] is the account being edited (null for a new one); it is
 * kept so a save preserves the fields the form doesn't touch (position, icon, color,
 * archived) instead of resetting them.
 *
 * [balanceText] doubles as two different fields depending on [hasTransactions] (ADR-0057): for a
 * new account or one with an empty ledger it's the starting `opening_balance`; for one with real
 * activity it's a **target** balance, pre-filled from [baselineBalance] (the derived figure at
 * the moment the editor opened) and corrected via a marked ledger row on Save rather than
 * rewriting `opening_balance` (LWW-unsafe once an account has activity).
 */
data class AccountEditorState(
    val source: Account? = null,
    val name: String = "",
    val type: AccountType = AccountType.EWALLET,
    val balanceText: String = "",
    val icon: String? = null,
    val color: String? = null,
    val nameError: Boolean = false,
    /** True when [source] already has ledger rows — decides which of the two Save paths above runs. */
    val hasTransactions: Boolean = false,
    /** The derived current balance the field was pre-filled with; the delta baseline Save computes
     *  against — captured once at open time, not re-read live (ADR-0057's concurrency decision). */
    val baselineBalance: BigDecimal = BigDecimal.ZERO,
) {
    val isEditing: Boolean get() = source != null
}
