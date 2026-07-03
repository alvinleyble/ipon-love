package com.iponlove.app.feature.savings.presentation

import com.iponlove.app.feature.savings.domain.model.SavingsGoalProgress

/**
 * Savings-goals list state. Active goals always show; archived ones only when [showArchived]
 * is toggled on. Both lists carry the DERIVED progress (ADR-0025).
 */
data class SavingsGoalsUiState(
    val isLoading: Boolean = true,
    val active: List<SavingsGoalProgress> = emptyList(),
    val archived: List<SavingsGoalProgress> = emptyList(),
    val showArchived: Boolean = false,
)
