package com.iponlove.app.feature.recurring.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {

    @Query("SELECT * FROM recurring_rules WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun observeRules(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE isDeleted = 0")
    suspend fun activeRules(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: String): RecurringRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RecurringRuleEntity)

    // ---- sync engine plumbing ----

    @Query("SELECT * FROM recurring_rules WHERE pendingSync = 1")
    suspend fun dirtyRows(): List<RecurringRuleEntity>

    @Query("UPDATE recurring_rules SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun applyPullBatch(rules: List<RecurringRuleEntity>)
}
