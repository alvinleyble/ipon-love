package com.iponlove.app.feature.budgets.domain.repository

import com.iponlove.app.feature.budgets.domain.model.Budget
import kotlinx.coroutines.flow.Flow

/**
 * Budgets source of truth (Room-backed). All writes funnel through here so the sync
 * bookkeeping — `updated_at` stamping (ADR-0001), `pending_sync` (ADR-0002), soft delete
 * (ADR-0010) — is applied in one place; V1 budgets are personal (owner set, couple null).
 */
interface BudgetRepository {

    /** Active (non-deleted) budgets across all months; callers filter by month. */
    fun observeBudgets(): Flow<List<Budget>>

    suspend fun getBudget(id: String): Budget?

    suspend fun upsertBudget(budget: Budget)

    /** Soft delete — sets `is_deleted = true`; never a hard delete (ADR-0010). */
    suspend fun deleteBudget(id: String)
}
