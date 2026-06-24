package com.iponlove.app.feature.notes.domain.usecase

import com.iponlove.app.feature.notes.domain.repository.NoteRepository
import javax.inject.Inject

/** Soft delete (ADR-0010) — the row is tombstoned and the delete syncs like any edit. */
class DeleteNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    suspend operator fun invoke(id: String) = repository.deleteNote(id)
}
