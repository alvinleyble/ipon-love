package com.iponlove.app.feature.savings.presentation

import com.iponlove.app.core.ui.UpsellPrompt
import java.time.LocalDate

/**
 * Create/edit state for a savings goal. [isPartnerGoal] gates the whole form read-only (only the
 * creator edits metadata). Sharing mirrors the notes pattern: for a new goal the [isShared]
 * toggle is a local intent applied on save; for an existing goal it takes effect immediately.
 */
data class GoalEditorUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = true,
    val missing: Boolean = false,
    val goalId: String? = null,
    val name: String = "",
    val targetText: String = "",
    val targetDate: LocalDate? = null,
    val icon: String? = null,
    val color: String? = null,
    val isShared: Boolean = false,
    val isPaired: Boolean = false,
    val coupleId: String? = null,
    val isPartnerGoal: Boolean = false,
    val nameError: Boolean = false,
    val targetError: Boolean = false,
    /** Non-null while the count-cap upsell sheet is showing (S7; only ever set under enforcement). */
    val upsell: UpsellPrompt? = null,
)
