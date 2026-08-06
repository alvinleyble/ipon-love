package com.iponlove.app.feature.drafts.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDraftDao {

    /** The queue, oldest first — a parking area is worked off the front, not the back. */
    @Query(
        "SELECT * FROM transaction_drafts WHERE userId = :userId AND isDeleted = 0 " +
            "ORDER BY createdAt ASC",
    )
    fun observeDrafts(userId: String): Flow<List<TransactionDraftEntity>>

    @Query("SELECT COUNT(*) FROM transaction_drafts WHERE userId = :userId AND isDeleted = 0")
    fun observeDraftCount(userId: String): Flow<Int>

    @Query("SELECT * FROM transaction_drafts WHERE id = :id")
    suspend fun getById(id: String): TransactionDraftEntity?

    /**
     * Every **active** draft — the rows whose local image ids the orphaned-receipt sweep unions
     * into its known ids (ADR-0066 decision 6). Retired and deleted drafts are excluded on
     * purpose: their files are then unreachable and the sweep should collect them.
     *
     * Whole rows rather than a projection of the one column: a single-column `List<List<String>>`
     * is not something Room can map, and a parking area holds few enough rows that reading them
     * whole once per cold start costs nothing.
     */
    @Query("SELECT * FROM transaction_drafts WHERE isDeleted = 0")
    suspend fun activeDrafts(): List<TransactionDraftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: TransactionDraftEntity)

    // ---- sync engine plumbing ----

    @Query("SELECT * FROM transaction_drafts WHERE pendingSync = 1")
    suspend fun dirtyRows(): List<TransactionDraftEntity>

    @Query("UPDATE transaction_drafts SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<TransactionDraftEntity>)

    /**
     * Applies a pulled batch, **preserving each row's local-only `localImageIds`**. A pulled DTO
     * carries none (the column is local-only, ADR-0066 decision 1), so a straight REPLACE would
     * wipe this device's own file association whenever another device touched the same draft —
     * stranding the photos until the orphan sweep ate them, which is the exact failure the sweep
     * change exists to prevent. Incoming ids win when present; otherwise the local set survives.
     */
    @Transaction
    suspend fun applyPullBatch(rows: List<TransactionDraftEntity>) {
        upsertAll(
            rows.map { row ->
                if (row.localImageIds.isNotEmpty()) {
                    row
                } else {
                    row.copy(localImageIds = getById(row.id)?.localImageIds.orEmpty())
                }
            },
        )
    }
}
