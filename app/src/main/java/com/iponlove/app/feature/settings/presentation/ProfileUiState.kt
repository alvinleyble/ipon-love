package com.iponlove.app.feature.settings.presentation

/**
 * Profile screen state (ADR-0016). [nameDraft] is the editable field; [accentColor] and
 * [isPaired] drive the attribution-color picker, which is hidden when single. [email] is the
 * read-only account address.
 */
data class ProfileUiState(
    val nameDraft: String = "",
    val email: String? = null,
    val accentColor: String? = null,
    val isPaired: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = nameDraft.isNotBlank()
}
