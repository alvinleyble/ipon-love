package com.iponlove.app.feature.budgets.domain.repository

import com.iponlove.app.feature.budgets.domain.model.Budget
import kotlinx.coroutines.flow.Flow

/**
 * Budgets source of truth (Room-backed). All writes funnel through here so the sync
 * bookkeeping — `updated_at` stamping (ADR-0001), `pending_sync` (ADR-0002), soft delete
 * (ADR-0010) — is applied in one place; V1 budgets are personal (owner set, couple null).
 */
interface BudgetRepository {

    /** Active **personal** budgets across all months; callers filter by month. */
    fun observeBudgets(): Flow<List<Budget>>

    /**
     * Active **shared** budgets for [coupleId] — the couple's joint budgets, readable and
     * writable by both partners (ADR; no redaction). They sync through the same `budgets`
     * table as personal budgets; the `budgets_couple` RLS policy makes them visible to both.
     */
    fun observeSharedBudgets(coupleId: String): Flow<List<Budget>>

    suspend fun getBudget(id: String): Budget?

    /** Count of active personal budgets for [yearMonth] — the per-month `maxBudgets` cap (§10.1). */
    suspend fun countPersonalBudgets(yearMonth: String): Int

    suspend fun upsertBudget(budget: Budget)

    /**
     * Create or edit a couple-owned budget: stamps `couple_id = [coupleId]` and a null
     * `user_id` (the schema's owner check requires exactly one set). Ownership survives edits.
     */
    suspend fun upsertSharedBudget(budget: Budget, coupleId: String)

    /** Soft delete — sets `is_deleted = true`; never a hard delete (ADR-0010). */
    suspend fun deleteBudget(id: String)

    /** Hard-delete all shared (couple-owned) budgets on unpair (ADR-0008). */
    suspend fun purgeSharedBudgets()
}
