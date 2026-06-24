package com.iponlove.app.feature.notes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query(
        """
        SELECT * FROM notes
        WHERE isDeleted = 0
        ORDER BY updatedAt DESC, createdAt DESC
        """,
    )
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    // ---- sync engine plumbing ----

    @Query("SELECT * FROM notes WHERE pendingSync = 1")
    suspend fun dirtyRows(): List<NoteEntity>

    @Query("UPDATE notes SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun applyPullBatch(notes: List<NoteEntity>)
}
