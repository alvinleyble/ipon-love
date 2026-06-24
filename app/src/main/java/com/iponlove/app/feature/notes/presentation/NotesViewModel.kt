package com.iponlove.app.feature.notes.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.notes.domain.model.Note
import com.iponlove.app.feature.notes.domain.usecase.DeleteNoteUseCase
import com.iponlove.app.feature.notes.domain.usecase.NoteContentText
import com.iponlove.app.feature.notes.domain.usecase.ObserveNotesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    observeNotes: ObserveNotesUseCase,
    private val deleteNote: DeleteNoteUseCase,
) : ViewModel() {

    val uiState: StateFlow<NotesUiState> =
        observeNotes()
            .map { notes -> NotesUiState(isLoading = false, notes = notes.map { it.toListItem() }) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = NotesUiState(),
            )

    fun delete(id: String) {
        viewModelScope.launch { deleteNote(id) }
    }

    private fun Note.toListItem() = NoteListItem(
        id = id,
        title = title.ifBlank { "Untitled" },
        preview = NoteContentText.plainText(contentHtml).take(PREVIEW_CHARS),
        updatedAt = updatedAt,
        isShared = isShared,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PREVIEW_CHARS = 140
    }
}
