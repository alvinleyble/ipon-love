package com.iponlove.app.feature.savings.domain.repository

import com.iponlove.app.feature.savings.domain.model.GoalContribution
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant

/**
 * The append-only contribution ledger (ADR-0025). Adds mint a fresh random id every time (never
 * deterministic) so concurrent contributions from both partners never collide. You may only
 * edit/delete your OWN rows.
 */
interface GoalContributionRepository {
    /** All non-deleted contributions across visible goals — the list screen derives per-goal sums. */
    fun observeAllActive(): Flow<List<GoalContribution>>

    /** One goal's ledger (both partners), newest first. */
    fun observeByGoal(goalId: String): Flow<List<GoalContribution>>

    /** Append a contribution to [goalId] with a brand-new random id (LWW-safe, ADR-0025). */
    suspend fun addContribution(goalId: String, amount: BigDecimal, date: Instant, note: String?)

    /** Edit one of your own contributions. No-op if the row is a partner's. */
    suspend fun editContribution(id: String, amount: BigDecimal, date: Instant, note: String?)

    /** Soft-delete one of your own contributions. No-op if the row is a partner's. */
    suspend fun deleteContribution(id: String)

    /** Soft-delete all of your own contributions to [goalId] — the delete-goal cascade (ADR-0025). */
    suspend fun softDeleteOwnForGoal(goalId: String)

    /** Hard-delete replicated partner contributions on unpair (ADR-0008). */
    suspend fun purgePartnerData(userId: String)
}
