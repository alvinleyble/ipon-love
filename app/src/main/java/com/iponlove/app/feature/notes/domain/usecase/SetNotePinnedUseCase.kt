package com.iponlove.app.feature.notes.domain.usecase

import com.iponlove.app.feature.notes.domain.repository.NoteRepository
import javax.inject.Inject

/** Pin or unpin a note (ADR-0040). Reuses the note write path — no new sync logic. */
class SetNotePinnedUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    suspend operator fun invoke(noteId: String, isPinned: Boolean) =
        repository.setPinned(noteId, isPinned)
}
