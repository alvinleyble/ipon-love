package com.iponlove.app.feature.notes.domain.usecase

import com.iponlove.app.feature.notes.domain.model.NoteAttachment
import com.iponlove.app.feature.notes.domain.repository.NoteAttachmentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveNoteAttachmentsUseCase @Inject constructor(
    private val repository: NoteAttachmentRepository,
) {
    operator fun invoke(noteId: String): Flow<List<NoteAttachment>> =
        repository.observeByNote(noteId)
}
