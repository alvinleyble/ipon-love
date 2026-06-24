package com.iponlove.app.feature.accounts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    /** Active accounts (not soft-deleted), optionally including archived ones. */
    @Query(
        """
        SELECT * FROM accounts
        WHERE isDeleted = 0 AND (:includeArchived = 1 OR isArchived = 0)
        ORDER BY position ASC, createdAt ASC
        """,
    )
    fun observeAccounts(includeArchived: Boolean): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: String): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: AccountEntity)

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
