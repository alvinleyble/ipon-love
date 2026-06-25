package com.iponlove.app.feature.notes.domain.usecase

import com.iponlove.app.feature.notes.domain.repository.NoteAttachmentRepository
import com.iponlove.app.feature.notes.domain.repository.NoteRepository
import javax.inject.Inject

/** Soft delete (ADR-0010) — tombstones the note and all its attachments so both sync. */
class DeleteNoteUseCase @Inject constructor(
    private val notes: NoteRepository,
    private val attachments: NoteAttachmentRepository,
) {
    suspend operator fun invoke(id: String) {
        attachments.softDeleteAllForNote(id)
        notes.deleteNote(id)
    }
}
