package com.iponlove.app.feature.notes.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import com.iponlove.app.feature.notes.domain.model.Note
import com.iponlove.app.feature.notes.domain.usecase.DeleteNoteUseCase
import com.iponlove.app.feature.notes.domain.usecase.NoteContentText
import com.iponlove.app.feature.notes.domain.usecase.ObserveNotesUseCase
import com.iponlove.app.feature.user.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotesViewModel @Inject constructor(
    observeNotes: ObserveNotesUseCase,
    observePairingState: ObservePairingStateUseCase,
    private val userRepository: UserRepository,
    private val deleteNote: DeleteNoteUseCase,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<NotesUiState> = observePairingState()
        .flatMapLatest { pairing ->
            val coupleId = (pairing as? PairingState.Paired)?.couple?.id
            val partnerFlow = if (coupleId != null) {
                userRepository.observePartner(coupleId)
            } else {
                flowOf(null)
            }
            combine(observeNotes(), partnerFlow) { notes, partner ->
                NotesUiState(
                    isLoading = false,
                    notes = notes.map { it.toListItem(partner?.displayName) },
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = NotesUiState(),
        )

    fun delete(id: String) {
        viewModelScope.launch { deleteNote(id) }
    }

    private fun Note.toListItem(partnerName: String?) = NoteListItem(
        id = id,
        title = title.ifBlank { "Untitled" },
        preview = NoteContentText.plainText(contentHtml).take(PREVIEW_CHARS),
        updatedAt = updatedAt,
        isShared = isShared,
        isPartnerNote = isPartnerNote,
        partnerName = if (isPartnerNote) partnerName else null,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val PREVIEW_CHARS = 140
    }
}
