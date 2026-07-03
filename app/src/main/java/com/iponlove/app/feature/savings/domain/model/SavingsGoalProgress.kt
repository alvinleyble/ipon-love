package com.iponlove.app.feature.savings.domain.model

import java.math.BigDecimal

/**
 * A goal paired with its DERIVED progress (ADR-0025). [savedAmount] = Σ non-deleted
 * contributions (both partners); [reached] = saved ≥ target; [progress] = clamped 0f..1f for
 * the bar. Nothing here is stored — it's recomputed from the ledger every emission.
 */
data class SavingsGoalProgress(
    val goal: SavingsGoal,
    val savedAmount: BigDecimal,
    val reached: Boolean,
    val progress: Float,
)
