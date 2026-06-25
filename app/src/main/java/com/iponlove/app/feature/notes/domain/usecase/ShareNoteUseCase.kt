package com.iponlove.app.feature.notes.domain.usecase

import com.iponlove.app.feature.notes.domain.repository.NoteRepository
import javax.inject.Inject

class ShareNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    suspend operator fun invoke(noteId: String, coupleId: String) =
        repository.shareNote(noteId, coupleId)
}
