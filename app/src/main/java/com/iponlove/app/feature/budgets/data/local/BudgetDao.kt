package com.iponlove.app.feature.budgets.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    /**
     * Personal budgets only (`coupleId IS NULL`). Shared couple budgets live in the same
     * table once pulled, so the personal Budgets tab must exclude them — same individual-view
     * isolation the transaction/account/category DAOs apply (ADR-0011).
     */
    @Query("SELECT * FROM budgets WHERE isDeleted = 0 AND coupleId IS NULL ORDER BY yearMonth DESC, createdAt ASC")
    fun observeBudgets(): Flow<List<BudgetEntity>>

    /** The couple's shared budgets (`couple_id` set, owned jointly per ADR; no redaction). */
    @Query("SELECT * FROM budgets WHERE isDeleted = 0 AND coupleId = :coupleId ORDER BY yearMonth DESC, createdAt ASC")
    fun observeSharedBudgets(coupleId: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: String): BudgetEntity?

    /**
     * Personal (non-shared) budget count **for one month** — the `maxBudgets` cap (§10.1) is
     * per-month, since budgets are inherently monthly (this is a deliberate scoping of G1's
     * "all rows" to the month being created into, not an all-time total). Mirrors
     * [observeBudgets]'s `coupleId IS NULL` isolation — shared couple budgets have their own cap.
     */
    @Query("SELECT COUNT(*) FROM budgets WHERE isDeleted = 0 AND coupleId IS NULL AND yearMonth = :yearMonth")
    suspend fun countPersonal(yearMonth: String): Int

    /**
     * Shared (couple-owned) budget count **for one month** — the `maxSharedBudgets` cap (§10.1,
     * Item 35), gated at the moment a new shared budget is created (Scope.SHARED). Per-month like
     * [countPersonal]. No `coupleId` filter needed: a couple_id row locally is always this couple's.
     */
    @Query("SELECT COUNT(*) FROM budgets WHERE isDeleted = 0 AND coupleId IS NOT NULL AND yearMonth = :yearMonth")
    suspend fun countShared(yearMonth: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    /**
     * Hard-delete every shared (couple-owned) budget on unpair (ADR-0008). RLS stops
     * returning these the moment `couple_id` clears, so the server tombstone can never reach
     * the device — it purges locally off the same signal as partner replicas. Personal
     * budgets (`coupleId IS NULL`) are untouched.
     */
    @Query("DELETE FROM budgets WHERE coupleId IS NOT NULL")
    suspend fun deleteCoupleBudgets()

    // ---- sync engine plumbing ----

    @Query("SELECT * FROM budgets WHERE pendingSync = 1")
    suspend fun dirtyRows(): List<BudgetEntity>

    /** The current user's couple, read from the local users row — feeds the push ownership
     *  filter (v1.6.5 Item 20). Null when unpaired or the row hasn't synced in. */
    @Query("SELECT coupleId FROM users WHERE id = :userId")
    suspend fun coupleIdOf(userId: String): String?

    @Query("UPDATE budgets SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun applyPullBatch(budgets: List<BudgetEntity>)
}
