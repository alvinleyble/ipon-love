package com.iponlove.app.feature.savings.domain.repository

import com.iponlove.app.feature.savings.domain.model.SavingsGoal
import kotlinx.coroutines.flow.Flow

/**
 * Owned + replicated-partner savings goals. Every write applies the sync bookkeeping in one
 * place (monotonic `updated_at` + `pending_sync`, soft delete). Metadata is creator-owned, so
 * writes on a partner goal are rejected upstream (the UI hides them) and no-op here defensively.
 */
interface SavingsGoalRepository {
    fun observeGoals(): Flow<List<SavingsGoal>>
    suspend fun getGoal(id: String): SavingsGoal?

    /** Create or edit a goal's metadata (name/target/date/icon/color). Ownership, sharing,
     *  archived state and provenance are preserved from the existing row. */
    suspend fun upsertGoal(goal: SavingsGoal)

    suspend fun setArchived(id: String, archived: Boolean)

    /** Creator-only soft delete (ADR-0010). Contribution cascade is handled by the use case. */
    suspend fun deleteGoal(id: String)

    suspend fun shareGoal(id: String, coupleId: String)
    suspend fun unshareGoal(id: String)

    /** Hard-delete replicated partner goals on unpair (ADR-0008). */
    suspend fun purgePartnerData(userId: String)
}
