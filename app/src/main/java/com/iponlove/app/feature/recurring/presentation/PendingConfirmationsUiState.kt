package com.iponlove.app.feature.recurring.presentation

import com.iponlove.app.feature.recurring.domain.model.PendingConfirmation

/**
 * State for the Records "To confirm" card (Item 37). [items] is the live, derived list of
 * due-but-unrecorded occurrences of confirm-on-arrival rules, oldest-first; the card is hidden
 * entirely when it's empty.
 */
data class PendingConfirmationsUiState(
    val items: List<PendingConfirmation> = emptyList(),
    val collapsed: Boolean = false,
)
