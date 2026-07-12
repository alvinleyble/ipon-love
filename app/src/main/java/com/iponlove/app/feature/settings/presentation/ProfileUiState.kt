package com.iponlove.app.feature.settings.presentation

import com.iponlove.app.feature.settings.domain.model.ResetFinancesCounts

/**
 * Profile screen state (ADR-0016). [nameDraft] is the editable field; [accentColor] and
 * [isPaired] drive the attribution-color picker, which is hidden when single. [email] is the
 * read-only account address. The `resetFinances*` fields back the "Restart fresh" confirm
 * dialog (ADR-0037) — a self-contained flow, distinct from the name/color editors above.
 */
data class ProfileUiState(
    val nameDraft: String = "",
    val email: String? = null,
    val accentColor: String? = null,
    val isPaired: Boolean = false,
    val saved: Boolean = false,
    val showResetFinancesDialog: Boolean = false,
    val resetFinancesCounts: ResetFinancesCounts? = null,
    val resetFinancesPassword: String = "",
    val isResetFinancesLoading: Boolean = false,
    val resetFinancesError: String? = null,
    val financesJustReset: Boolean = false,
    // Delete account (ADR-0045) — a self-contained flow like reset above, but destroys the
    // account entirely. On success the session teardown navigates away, so there is no
    // "just deleted" success field.
    val showDeleteAccountDialog: Boolean = false,
    val deleteAccountPassword: String = "",
    val isDeleteAccountLoading: Boolean = false,
    val deleteAccountError: String? = null,
    // Optimistic default: the ConnectivityObserver seeds the real state within milliseconds of
    // collection, so the confirm never flashes disabled when actually online.
    val isOnline: Boolean = true,
) {
    val canSave: Boolean get() = nameDraft.isNotBlank()

    /** Reset needs the network for its re-auth (ADR-0037), so the confirm is gated on [isOnline]. */
    val canConfirmReset: Boolean
        get() = isOnline && resetFinancesPassword.isNotBlank() && !isResetFinancesLoading

    /** Delete needs the network for its re-auth + RPC (ADR-0045), so the confirm is gated on [isOnline]. */
    val canConfirmDelete: Boolean
        get() = isOnline && deleteAccountPassword.isNotBlank() && !isDeleteAccountLoading
}
