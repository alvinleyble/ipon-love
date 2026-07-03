package com.iponlove.app.feature.savings.presentation

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** One ledger row, already attributed ("You" vs the partner's name) for display. */
data class ContributionRow(
    val id: String,
    val amount: BigDecimal,
    val note: String?,
    val date: Instant,
    val isMine: Boolean,
    val byLabel: String,
)

/**
 * Goal detail + ledger state. `savedAmount`/`reached`/`progress` are DERIVED (ADR-0025).
 * [canManage] gates creator-only actions (edit/share/archive/delete); a partner viewing a shared
 * goal can still contribute but not manage it.
 */
data class GoalDetailUiState(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val goalId: String = "",
    val name: String = "",
    val icon: String? = null,
    val color: String? = null,
    val targetAmount: BigDecimal = BigDecimal.ZERO,
    val savedAmount: BigDecimal = BigDecimal.ZERO,
    val progress: Float = 0f,
    val reached: Boolean = false,
    val targetDate: LocalDate? = null,
    val isShared: Boolean = false,
    val isArchived: Boolean = false,
    val canManage: Boolean = false,
    val contributions: List<ContributionRow> = emptyList(),
)
