package com.iponlove.app.feature.transactions.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    /** Active (non-deleted) transactions, most recent first. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE isDeleted = 0
        ORDER BY date DESC, createdAt DESC
        """,
    )
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transaction: TransactionEntity)

    // ---- sync engine plumbing ----

    @Query("SELECT * FROM transactions WHERE pendingSync = 1")
    suspend fun dirtyRows(): List<TransactionEntity>

    @Query("UPDATE transactions SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun applyPullBatch(transactions: List<TransactionEntity>)
}
