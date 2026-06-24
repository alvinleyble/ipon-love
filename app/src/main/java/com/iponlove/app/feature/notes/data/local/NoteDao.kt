package com.iponlove.app.feature.notes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    /** The current user's own active notes — filtered to [userId] so replicated partner
     *  notes (ADR-0004) never leak into the individual list. */
    @Query(
        """
        SELECT * FROM notes
        WHERE userId = :userId AND isDeleted = 0
        ORDER BY updatedAt DESC, createdAt DESC
        """,
    )
    fun observeNotes(userId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    /** Hard-delete one row — used to purge a redacted partner row (ADR-0005). */
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Hard-delete every replicated partner row on unpair (ADR-0008). */
    @Query("DELETE FROM notes WHERE userId <> :userId")
    suspend fun deleteNotOwnedBy(userId: String)

    // ---- sync engine plumbing ----

    @Query("SELECT * FROM notes WHERE pendingSync = 1")
    suspend fun dirtyRows(): List<NoteEntity>

    @Query("UPDATE notes SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun applyPullBatch(notes: List<NoteEntity>)
}
