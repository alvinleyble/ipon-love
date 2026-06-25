package com.iponlove.app.feature.notes.domain.repository

import com.iponlove.app.feature.notes.domain.model.NoteAttachment
import kotlinx.coroutines.flow.Flow

interface NoteAttachmentRepository {

    /** Active (non-deleted) attachments for a note, ordered by position. */
    fun observeByNote(noteId: String): Flow<List<NoteAttachment>>

    /** Create an attachment row with a local file path, awaiting upload. */
    suspend fun addAttachment(noteId: String, localPath: String): NoteAttachment

    /** Soft delete one attachment (ADR-0010). */
    suspend fun deleteAttachment(id: String)

    /** Soft delete all active attachments for a note (called when the note itself is deleted). */
    suspend fun softDeleteAllForNote(noteId: String)

    /** Hard-delete all replicated partner attachments on unpair (ADR-0008). */
    suspend fun purgePartnerData(userId: String)
}
