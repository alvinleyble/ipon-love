package com.iponlove.app.feature.notes.domain.usecase

import com.iponlove.app.feature.notes.domain.repository.NoteAttachmentRepository
import javax.inject.Inject

class DeleteNoteAttachmentUseCase @Inject constructor(
    private val repository: NoteAttachmentRepository,
) {
    suspend operator fun invoke(id: String) = repository.deleteAttachment(id)
}
