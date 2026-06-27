package com.iponlove.app.feature.accounts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    /**
     * The current user's own active accounts plus any couple-owned shared accounts (ADR-0018),
     * optionally including archived ones. `coupleId IS NOT NULL` admits shared accounts into
     * both partners' lists; replicated partner *personal* accounts (userId = partner,
     * coupleId null, ADR-0004) are still excluded from the individual view.
     */
    @Query(
        """
        SELECT * FROM accounts
        WHERE (userId = :userId OR coupleId IS NOT NULL)
          AND isDeleted = 0 AND (:includeArchived = 1 OR isArchived = 0)
        ORDER BY position ASC, createdAt ASC
        """,
    )
    fun observeAccounts(userId: String, includeArchived: Boolean): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

    /** Hard-delete one row — used to purge a redacted partner row (ADR-0005). */
    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Hard-delete replicated partner *personal* rows on unpair (ADR-0008). Couple-owned rows
     * (userId IS NULL) are handled by the revert/delete pair below, so the `userId IS NOT NULL`
     * guard keeps this from touching them (and SQL `NULL <> x` would skip them anyway).
     */
    @Query("DELETE FROM accounts WHERE userId IS NOT NULL AND userId <> :userId")
    suspend fun deleteNotOwnedBy(userId: String)

    /**
     * Revert-to-creator for the unpair purge (ADR-0018): a couple-owned account this user
     * created becomes their personal account, keeping its balance/history. Mirrors the
     * server-side revert in unpair(); marked dirty so it re-converges if done offline.
     */
    @Query(
        """
        UPDATE accounts
        SET userId = createdBy, coupleId = NULL, pendingSync = 1, updatedAt = :updatedAt
        WHERE coupleId IS NOT NULL AND createdBy = :userId
        """,
    )
    suspend fun revertOwnCoupleRowsToCreator(userId: String, updatedAt: Long)

    /** Hard-delete couple-owned accounts created by the *other* partner on unpair (ADR-0018). */
    @Query("DELETE FROM accounts WHERE coupleId IS NOT NULL AND (createdBy IS NULL OR createdBy <> :userId)")
    suspend fun deleteCoupleRowsNotCreatedBy(userId: String)

    // ---- sync engine plumbing ----

    /** Rows awaiting push (ADR-0002). */
    @Query("SELECT * FROM accounts WHERE pendingSync = 1")
    suspend fun dirtyRows(): List<AccountEntity>

    @Query("UPDATE accounts SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun applyPullBatch(accounts: List<AccountEntity>)
}
