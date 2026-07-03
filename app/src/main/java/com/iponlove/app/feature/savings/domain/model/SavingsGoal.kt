package com.iponlove.app.feature.savings.domain.model

import java.math.BigDecimal
import java.time.LocalDate

/**
 * A savings goal — personal by default, optionally shared to the couple (ADR-0025). The
 * domain model hides ownership/sync columns; [isPartnerGoal] is the only ownership signal the
 * UI needs: a partner-owned shared goal is read-only for metadata (you can still contribute).
 *
 * `savedAmount`/`reached` are NOT here — they are derived from the contribution ledger
 * ([com.iponlove.app.feature.savings.domain.usecase.SavingsGoalCalculator]).
 */
data class SavingsGoal(
    val id: String,
    val name: String,
    val targetAmount: BigDecimal,
    val targetDate: LocalDate?,
    val icon: String?,
    val color: String?,
    val isShared: Boolean = false,
    val isArchived: Boolean = false,
    val isPartnerGoal: Boolean = false,
)
