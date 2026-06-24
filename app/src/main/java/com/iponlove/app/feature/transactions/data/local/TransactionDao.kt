package com.iponlove.app.feature.transactions.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    /**
     * The current user's own active transactions, most recent first. Filtered to [userId]
     * so replicated partner transactions (ADR-0004) never leak into the individual view —
     * the combined couple view is a separate, deliberately-merged query.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE userId = :userId AND isDeleted = 0
        ORDER BY date DESC, createdAt DESC
        """,
    )
    fun observeTransactions(userId: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transaction: TransactionEntity)

    /** Hard-delete one row — used to purge a redacted partner row (ADR-0005). */
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Hard-delete every replicated partner row on unpair (ADR-0008). */
    @Query("DELETE FROM transactions WHERE userId <> :userId")
    suspend fun deleteNotOwnedBy(userId: String)

    // ---- sync engine plumbing ----

    @Query("SELECT * FROM transactions WHERE pendingSync = 1")
    suspend fun dirtyRows(): List<TransactionEntity>

    @Query("UPDATE transactions SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun applyPullBatch(transactions: List<TransactionEntity>)
}
