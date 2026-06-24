package com.iponlove.app.feature.notes.domain.usecase

import com.iponlove.app.feature.notes.domain.model.Note
import com.iponlove.app.feature.notes.domain.repository.NoteRepository
import javax.inject.Inject

/** Loads a single note to seed the editor; null when it's gone (deleted on another device). */
class GetNoteUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    suspend operator fun invoke(id: String): Note? = repository.getNote(id)
}
