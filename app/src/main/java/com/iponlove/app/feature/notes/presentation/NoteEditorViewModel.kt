package com.iponlove.app.feature.notes.presentation

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import com.iponlove.app.feature.notes.domain.model.Note
import com.iponlove.app.feature.notes.domain.usecase.AddNoteImageUseCase
import com.iponlove.app.feature.notes.domain.usecase.DeleteNoteAttachmentUseCase
import com.iponlove.app.feature.notes.domain.usecase.DeleteNoteUseCase
import com.iponlove.app.feature.notes.domain.usecase.GetNoteUseCase
import com.iponlove.app.feature.notes.domain.usecase.NoteContentText
import com.iponlove.app.feature.notes.domain.usecase.ObserveNoteAttachmentsUseCase
import com.iponlove.app.feature.notes.domain.usecase.ShareNoteUseCase
import com.iponlove.app.feature.notes.domain.usecase.UnshareNoteUseCase
import com.iponlove.app.feature.notes.domain.usecase.UpsertNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getNote: GetNoteUseCase,
    private val upsertNote: UpsertNoteUseCase,
    private val deleteNote: DeleteNoteUseCase,
    private val observeAttachments: ObserveNoteAttachmentsUseCase,
    private val addNoteImage: AddNoteImageUseCase,
    private val deleteAttachment: DeleteNoteAttachmentUseCase,
    private val shareNote: ShareNoteUseCase,
    private val unshareNote: UnshareNoteUseCase,
    observePairingState: ObservePairingStateUseCase,
) : ViewModel() {

    private val argId: String = savedStateHandle[NOTE_ID_KEY] ?: NEW_NOTE
    private val isNew: Boolean = argId == NEW_NOTE

    private val _uiState = MutableStateFlow(NoteEditorUiState(isNew = isNew))
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    init {
        if (isNew) {
            val noteId = UUID.randomUUID().toString()
            _uiState.update { it.copy(loaded = true, noteId = noteId) }
            collectAttachments(noteId)
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
                            isShared = note.isShared,
                            isPartnerNote = note.isPartnerNote,
                        )
                    }
                }
                if (note != null) collectAttachments(argId)
            }
        }

        viewModelScope.launch {
            observePairingState().collect { state ->
                val paired = state is PairingState.Paired
                val coupleId = (state as? PairingState.Paired)?.couple?.id
                _uiState.update { it.copy(isPaired = paired, coupleId = coupleId) }
            }
        }
    }

    private fun collectAttachments(noteId: String) {
        viewModelScope.launch {
            observeAttachments(noteId).collect { list ->
                _uiState.update { it.copy(attachments = list) }
            }
        }
    }

    /** Persist (or discard) the note, then invoke [onDone] so the screen can navigate back. */
    fun save(title: String, html: String, onDone: () -> Unit) {
        val id = _uiState.value.noteId
        if (id == null || _uiState.value.missing || _uiState.value.isPartnerNote) {
            onDone()
            return
        }
        viewModelScope.launch {
            when {
                NoteContentText.isBlank(title, html) && isNew -> Unit // never persisted; share intent discarded silently
                NoteContentText.isBlank(title, html) -> deleteNote(id) // emptied existing
                else -> {
                    upsertNote(Note(id = id, title = title, contentHtml = html))
                    if (isNew && _uiState.value.isShared) {
                        val coupleId = _uiState.value.coupleId
                        if (coupleId != null) shareNote(id, coupleId)
                    }
                }
            }
            onDone()
        }
    }

    fun toggleShared() {
        if (_uiState.value.isPartnerNote) return
        _uiState.value.noteId ?: return
        if (isNew) {
            // Note doesn't exist yet — just track intent locally; save() will call shareNote().
            _uiState.update { it.copy(isShared = !it.isShared) }
            return
        }
        val coupleId = _uiState.value.coupleId ?: return
        val nowShared = _uiState.value.isShared
        viewModelScope.launch {
            if (nowShared) {
                unshareNote(_uiState.value.noteId!!)
            } else {
                shareNote(_uiState.value.noteId!!, coupleId)
            }
            _uiState.update { it.copy(isShared = !nowShared) }
        }
    }

    fun addImage(uri: Uri) {
        if (_uiState.value.isPartnerNote) return
        val noteId = _uiState.value.noteId ?: return
        viewModelScope.launch {
            runCatching { addNoteImage(noteId, uri) }
        }
    }

    fun removeAttachment(id: String) {
        viewModelScope.launch { deleteAttachment(id) }
    }

    companion object {
        const val NOTE_ID_KEY = "noteId"
        const val NEW_NOTE = "new"
    }
}
