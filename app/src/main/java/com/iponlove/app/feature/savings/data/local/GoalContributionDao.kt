package com.iponlove.app.feature.savings.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalContributionDao {

    /** All non-deleted contributions (both authors, for every visible goal) — the list screen
     *  sums these per goal to derive `savedAmount`. Orphan rows (goal no longer in Room) are
     *  harmlessly ignored by the calculator, which only sums contributions of present goals. */
    @Query("SELECT * FROM goal_contributions WHERE isDeleted = 0")
    fun observeAllActive(): Flow<List<GoalContributionEntity>>

    /** One goal's ledger, newest first — both partners' contributions (ADR-0025). */
    @Query(
        "SELECT * FROM goal_contributions WHERE goalId = :goalId AND isDeleted = 0 ORDER BY date DESC, createdAt DESC",
    )
    fun observeByGoal(goalId: String): Flow<List<GoalContributionEntity>>

    @Query("SELECT * FROM goal_contributions WHERE id = :id")
    suspend fun getById(id: String): GoalContributionEntity?

    /** The caller's own non-deleted contributions to [goalId] — read by the delete-goal cascade. */
    @Query("SELECT * FROM goal_contributions WHERE goalId = :goalId AND userId = :userId AND isDeleted = 0")
    suspend fun activeOwnedForGoal(goalId: String, userId: String): List<GoalContributionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(contribution: GoalContributionEntity)

    /** Hard-delete one row — used to purge a redacted partner contribution (ADR-0005). */
    @Query("DELETE FROM goal_contributions WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Cascade safety net: purge the partner's contribution replicas for a goal whose replica
     *  was itself purged (unshare/delete), so no dead partner rows linger between sync batches.
     *  Own rows are left (they fund the goal and are ignored once the goal is gone). */
    @Query("DELETE FROM goal_contributions WHERE goalId = :goalId AND userId <> :userId")
    suspend fun deleteByGoalNotOwnedBy(goalId: String, userId: String)

    /** Hard-delete every replicated partner row on unpair (ADR-0008). */
    @Query("DELETE FROM goal_contributions WHERE userId <> :userId")
    suspend fun deleteNotOwnedBy(userId: String)

    // ---- sync engine plumbing ----

    @Query("SELECT * FROM goal_contributions WHERE pendingSync = 1")
    suspend fun dirtyRows(): List<GoalContributionEntity>

    @Query("UPDATE goal_contributions SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun applyPullBatch(contributions: List<GoalContributionEntity>)
}
