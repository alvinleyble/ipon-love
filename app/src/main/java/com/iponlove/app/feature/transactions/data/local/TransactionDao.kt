package com.iponlove.app.feature.transactions.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

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

    /**
     * Records' month-windowed view (ADR-0032): same as [observeTransactions], but bounded to
     * a half-open date range at the query layer so Room only re-emits and re-maps the viewed
     * month's rows on a write, not the user's entire history.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE userId = :userId AND isDeleted = 0 AND date >= :startInclusive AND date < :endExclusive
        ORDER BY date DESC, createdAt DESC
        """,
    )
    fun observeTransactions(
        userId: String,
        startInclusive: Instant,
        endExclusive: Instant,
    ): Flow<List<TransactionEntity>>

    /** Whether [userId] has ever recorded an active transaction (ADR-0032 empty-state signal). */
    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE userId = :userId AND isDeleted = 0)")
    fun observeHasAnyTransaction(userId: String): Flow<Boolean>

    /**
     * The couple's shared ledger, month-windowed (ADR-0032): both members' active, non-private
     * transactions within a half-open date range, most recent first. No owner filter — every
     * row in the table is either the user's own or a replicated partner row (ADR-0004), and
     * private rows are excluded for both (the partner's are already purged on redaction,
     * ADR-0005; the user's own are hidden here so the combined view matches what the partner
     * sees, ADR-0011).
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE isDeleted = 0 AND isPrivate = 0 AND date >= :startInclusive AND date < :endExclusive
        ORDER BY date DESC, createdAt DESC
        """,
    )
    fun observeCombined(startInclusive: Instant, endExclusive: Instant): Flow<List<TransactionEntity>>

    /** Whether the couple has ever shared an active transaction (ADR-0032 empty-state signal). */
    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE isDeleted = 0 AND isPrivate = 0)")
    fun observeHasAnyCombinedTransaction(): Flow<Boolean>

    /**
     * The ledger for deriving account balances, including shared accounts (ADR-0018): the
     * user's own transactions (private and not) plus every member's non-private ones. A shared
     * account's spend is forced non-private, so the partner's postings against it are included;
     * [AccountBalanceCalculator] ignores postings to accounts not in the opening-balance map, so
     * over-fetched partner postings against their personal accounts simply fall away.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE isDeleted = 0 AND (userId = :userId OR isPrivate = 0)
        ORDER BY date DESC, createdAt DESC
        """,
    )
    fun observeForBalances(userId: String): Flow<List<TransactionEntity>>

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

    // ---- receipt upload plumbing ----

    /** Rows with a locally-saved receipt that has not yet been uploaded to Storage. */
    @Query("SELECT * FROM transactions WHERE attachmentLocalPath IS NOT NULL AND isDeleted = 0")
    suspend fun pendingReceiptUploads(): List<TransactionEntity>

    /** Stamp the Storage URL and clear the local path after a successful upload. */
    @Query(
        """
        UPDATE transactions
        SET attachmentUrl = :url, attachmentLocalPath = NULL, pendingSync = 1, updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun markReceiptUploaded(id: String, url: String, updatedAt: Long)

    // ---- sync engine plumbing ----

    @Query("SELECT * FROM transactions WHERE pendingSync = 1")
    suspend fun dirtyRows(): List<TransactionEntity>

    @Query("UPDATE transactions SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun applyPullBatch(transactions: List<TransactionEntity>)
}
