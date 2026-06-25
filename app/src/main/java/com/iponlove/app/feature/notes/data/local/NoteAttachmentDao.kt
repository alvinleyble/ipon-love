package com.iponlove.app.feature.notes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface NoteAttachmentDao {

    @Query("SELECT * FROM note_attachments WHERE noteId = :noteId AND isDeleted = 0 ORDER BY position ASC, createdAt ASC")
    fun observeByNoteId(noteId: String): Flow<List<NoteAttachmentEntity>>

    @Query("SELECT * FROM note_attachments WHERE id = :id")
    suspend fun getById(id: String): NoteAttachmentEntity?

    @Query("SELECT COUNT(*) FROM note_attachments WHERE noteId = :noteId AND isDeleted = 0")
    suspend fun countActiveByNoteId(noteId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: NoteAttachmentEntity)

    @Query("DELETE FROM note_attachments WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM note_attachments WHERE noteId IN (SELECT id FROM notes WHERE userId <> :userId)")
    suspend fun deleteNotOwnedByUser(userId: String)

    // ---- upload plumbing ----

    /** Rows with a local file still awaiting upload (uploader reads these). */
    @Query("SELECT * FROM note_attachments WHERE localPath IS NOT NULL AND url IS NULL AND isDeleted = 0")
    suspend fun pendingUploads(): List<NoteAttachmentEntity>

    /** Stamp the Storage URL and clear the local path after a successful upload. */
    @Query("UPDATE note_attachments SET url = :url, localPath = NULL WHERE id = :id")
    suspend fun markUploaded(id: String, url: String)

    // ---- sync engine plumbing ----

    /** Only rows that have been uploaded (url IS NOT NULL) are ready to push to Postgrest. */
    @Query("SELECT * FROM note_attachments WHERE pendingSync = 1 AND url IS NOT NULL")
    suspend fun dirtyRows(): List<NoteAttachmentEntity>

    @Query("UPDATE note_attachments SET pendingSync = 0 WHERE id IN (:ids)")
    suspend fun clearPending(ids: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    @Transaction
    suspend fun applyPullBatch(entities: List<NoteAttachmentEntity>)

    // ---- cascade soft-delete (called when a note is deleted) ----

    @Query(
        """
        UPDATE note_attachments
        SET isDeleted = 1, pendingSync = 1, updatedAt = :updatedAt
        WHERE noteId = :noteId AND isDeleted = 0
        """,
    )
    suspend fun softDeleteAllForNote(noteId: String, updatedAt: Instant)
}
