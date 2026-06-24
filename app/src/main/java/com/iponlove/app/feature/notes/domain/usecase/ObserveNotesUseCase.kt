package com.iponlove.app.feature.notes.domain.usecase

import com.iponlove.app.feature.notes.domain.model.Note
import com.iponlove.app.feature.notes.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveNotesUseCase @Inject constructor(
    private val repository: NoteRepository,
) {
    operator fun invoke(): Flow<List<Note>> = repository.observeNotes()
}
