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
     * The current user's own active accounts (not soft-deleted), optionally including
     * archived ones. Filtered to [userId] so replicated partner accounts (ADR-0004) never
     * leak into the individual view.
     */
    @Query(
        """
        SELECT * FROM accounts
        WHERE userId = :userId AND isDeleted = 0 AND (:includeArchived = 1 OR isArchived = 0)
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

    /** Hard-delete every replicated partner row on unpair (ADR-0008). */
    @Query("DELETE FROM accounts WHERE userId <> :userId")
    suspend fun deleteNotOwnedBy(userId: String)

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
