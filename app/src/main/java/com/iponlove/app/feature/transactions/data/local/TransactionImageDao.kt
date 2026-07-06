package com.iponlove.app.feature.transactions.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionImageDao {

    /** Active images for one transaction, ordered for display. */
    @Query("SELECT * FROM transaction_images WHERE transactionId = :transactionId AND isDeleted = 0 ORDER BY position ASC, createdAt ASC")
    suspend fun getByTransactionId(transactionId: String): List<TransactionImageEntity>

    /**
     * Every active, uploaded receipt image across all transactions (own + replicated partner),
     * keyed for the combined view's txnId → urls lookup. Pre-upload rows (url null) are excluded.
     */
    @Query("SELECT * FROM transaction_images WHERE isDeleted = 0 AND url IS NOT NULL")
    fun observeAllUploaded(): Flow<List<TransactionImageEntity>>

    @Query("SELECT * FROM transaction_images WHERE id = :id")
    suspend fun getById(id: String): TransactionImageEntity?

    @Query("SELECT COUNT(*) FROM transaction_images WHERE transactionId = :transactionId AND isDeleted = 0")
    suspend fun countActiveByTransactionId(transactionId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TransactionImageEntity)

    @Query("DELETE FROM transaction_images WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Purge replicated partner rows on unpair (ADR-0008): images whose parent txn isn't ours. */
    @Query("DELETE FROM transaction_images WHERE transactionId IN (SELECT id FROM transactions WHERE userId <> :userId)")
    suspend fun deleteNotOwnedByUser(userId: String)

    // ---- upload plumbing ----

    /** Rows with a local file still awaiting upload (uploader reads these). */
    @Query("SELECT * FROM transaction_images WHERE localPath IS NOT NULL AND url IS NULL AND isDeleted = 0")
    suspend fun pendingUploads(): List<TransactionImageEntity>

    /** Stamp the Storage URL and clear the local path after a successful upload. */
    @Query("UPDATE transaction_images SET url = :url, localPath = NULL WHERE id = :id")
    suspend fun markUploaded(id: String, url: String)

    // ---- sync engine plumbing ----

    /** Only uploaded rows (url IS NOT NULL) are ready to push to Postgrest. */
    @Query("SELECT * FROM transaction_images WHERE pendingSync = 1 AND url IS NOT NULL")
    suspend fun dirtyRows(): List<TransactionImageEntity>

    @Query("UPDATE transaction_images SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun applyPullBatch(entities: List<TransactionImageEntity>)
}
