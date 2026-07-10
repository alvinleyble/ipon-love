package com.iponlove.app.feature.savings.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {

    /** Own goals plus replicated partner goals that are still shared (ADR-0005 guarantees a
     *  surviving partner row has isShared=1, isDeleted=0). Includes archived — the UI splits
     *  active vs archived so the "show archived" toggle needs both. */
    @Query(
        """
        SELECT * FROM savings_goals
        WHERE (userId = :userId OR isShared = 1) AND isDeleted = 0
        ORDER BY isArchived ASC, createdAt DESC
        """,
    )
    fun observeGoals(userId: String): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getById(id: String): SavingsGoalEntity?

    /** This user's personal (non-shared) goal count for the `maxPersonalSavingsGoals` cap
     *  (§10.1 / G1: all non-deleted rows, archived included). */
    @Query("SELECT COUNT(*) FROM savings_goals WHERE userId = :userId AND isShared = 0 AND isDeleted = 0")
    suspend fun countPersonal(userId: String): Int

    /** Couple-owned (shared) goal count for the `maxSharedSavingsGoals` cap (§10.1 / G1). */
    @Query("SELECT COUNT(*) FROM savings_goals WHERE isShared = 1 AND isDeleted = 0")
    suspend fun countShared(): Int

    /** Ids of goals this user may still push a contribution against — own goals, or goals shared
     *  into the couple — mirroring the server's `goal_contributions` INSERT check (ADR-0025). Lets
     *  the contribution syncer skip pushing a contribution whose goal was unshared/deleted out from
     *  under a pending offline row, which would otherwise be RLS-rejected forever (F1). */
    @Query("SELECT id FROM savings_goals WHERE (userId = :userId OR isShared = 1) AND isDeleted = 0")
    suspend fun pushableGoalIds(userId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: SavingsGoalEntity)

    /** Hard-delete one row — used to purge a redacted partner row (ADR-0005). */
    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Hard-delete every replicated partner row on unpair (ADR-0008). */
    @Query("DELETE FROM savings_goals WHERE userId <> :userId")
    suspend fun deleteNotOwnedBy(userId: String)

    // ---- sync engine plumbing ----

    @Query("SELECT * FROM savings_goals WHERE pendingSync = 1")
    suspend fun dirtyRows(): List<SavingsGoalEntity>

    @Query("UPDATE savings_goals SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun applyPullBatch(goals: List<SavingsGoalEntity>)
}
