package com.iponlove.app.feature.notes.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.notes.domain.model.Note
import com.iponlove.app.feature.notes.domain.usecase.DeleteNoteUseCase
import com.iponlove.app.feature.notes.domain.usecase.GetNoteUseCase
import com.iponlove.app.feature.notes.domain.usecase.NoteContentText
import com.iponlove.app.feature.notes.domain.usecase.UpsertNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Owns one note in the full-screen editor. The nav arg [NOTE_ID_KEY] is either an existing
 * note id or [NEW_NOTE] for a fresh one; a new note's id is minted up front so a save and a
 * later edit address the same row.
 *
 * The HTML body lives in the Compose `RichTextState` in the screen — not here — so this VM
 * holds only the seed values and the save logic. [save] discards a note left fully empty
 * (Keep-style): a never-saved one simply vanishes, an emptied existing one is soft-deleted.
 */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getNote: GetNoteUseCase,
    private val upsertNote: UpsertNoteUseCase,
    private val deleteNote: DeleteNoteUseCase,
) : ViewModel() {

    private val argId: String = savedStateHandle[NOTE_ID_KEY] ?: NEW_NOTE
    private val isNew: Boolean = argId == NEW_NOTE

    private val _uiState = MutableStateFlow(NoteEditorUiState(isNew = isNew))
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    init {
        if (isNew) {
            _uiState.update { it.copy(loaded = true, noteId = UUID.randomUUID().toString()) }
        } else {
            viewModelScope.launch {
                val note = getNote(argId)
                _uiState.update {
                    if (note == null) {
                        it.copy(loaded = true, missing = true)
                    } else {
                        it.copy(
                            loaded = true,
                            noteId = note.id,
                            initialTitle = note.title,
                            initialHtml = note.contentHtml,
                        )
                    }
                }
            }
        }
    }

    /** Persist (or discard) the note, then invoke [onDone] so the screen can navigate back. */
    fun save(title: String, html: String, onDone: () -> Unit) {
        val id = _uiState.value.noteId
        if (id == null || _uiState.value.missing) {
            onDone()
            return
        }
        viewModelScope.launch {
            when {
                NoteContentText.isBlank(title, html) && isNew -> Unit // never persisted
                NoteContentText.isBlank(title, html) -> deleteNote(id) // emptied existing
                else -> upsertNote(Note(id = id, title = title, contentHtml = html))
            }
            onDone()
        }
    }

    companion object {
        const val NOTE_ID_KEY = "noteId"
        const val NEW_NOTE = "new"
    }
}
