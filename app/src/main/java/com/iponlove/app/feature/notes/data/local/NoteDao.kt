package com.iponlove.app.feature.notes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    /** Own notes plus any replicated partner notes that are still shared (ADR-0005 guarantees
     *  surviving partner rows have isShared=1 and isDeleted=0; the OR is safe because
     *  shouldPurge() in PartnerNoteTableSyncer hard-deletes unshared partner rows). */
    @Query(
        """
        SELECT * FROM notes
        WHERE (userId = :userId OR isShared = 1) AND isDeleted = 0
        ORDER BY isPinned DESC, updatedAt DESC, createdAt DESC
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
