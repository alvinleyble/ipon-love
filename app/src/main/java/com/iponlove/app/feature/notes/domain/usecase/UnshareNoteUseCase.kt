package com.iponlove.app.feature.notes.domain.usecase

import com.iponlove.app.feature.notes.domain.repository.NoteRepository
import javax.inject.Inject

class UnshareNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    suspend operator fun invoke(noteId: String) = repository.unshareNote(noteId)
}
