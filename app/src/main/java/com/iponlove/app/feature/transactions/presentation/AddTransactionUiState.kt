package com.iponlove.app.feature.transactions.presentation

import com.iponlove.app.feature.accounts.domain.model.Account
import com.iponlove.app.feature.categories.domain.model.Category

/** Screen state for the full-screen add/edit-transaction route. */
data class AddTransactionUiState(
    /** Editor is null until the draft is hydrated (fresh, restored, or loaded from DB). */
    val editor: TransactionEditorState? = null,
    /** Picker sources, already sorted by position (DAO order). */
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    /** True only when creating an expense while paired with an available partner (ADR-0019 #12). */
    val canPayForPartner: Boolean = false,
    /** Partner's display name for the toggle label; "Partner" fallback. */
    val partnerName: String = "Partner",
    /** Whether the user is paired — drives the Private toggle's caption framing (ADR-0038 dec. 6). */
    val isPaired: Boolean = false,
    /** True when the id nav-arg pointed at a transaction that no longer exists. */
    val missing: Boolean = false,
) {
    val loading: Boolean get() = editor == null && !missing
}
