package com.iponlove.app.feature.notes.domain.usecase

import com.iponlove.app.feature.notes.domain.model.Note
import com.iponlove.app.feature.notes.domain.repository.NoteRepository
import javax.inject.Inject

/**
 * Persist a note. Trims the title; unlike a category a note may have a blank title (the
 * body carries it), so blankness is not rejected here — the editor discards a fully-empty
 * note before it ever reaches this use case (see [NoteContentText.isBlank]).
 */
class UpsertNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    suspend operator fun invoke(note: Note) {
        repository.upsertNote(note.copy(title = note.title.trim()))
    }
}
