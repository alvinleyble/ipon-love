package com.iponlove.app.feature.drafts.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.drafts.domain.usecase.ObserveDraftCountUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Backs the pinned "Drafts (N)" card on Records — a count and nothing else. */
@HiltViewModel
class DraftsCardViewModel @Inject constructor(
    observeDraftCount: ObserveDraftCountUseCase,
) : ViewModel() {

    val draftCount: StateFlow<Int> = observeDraftCount().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        // Zero until the first emission, so the card can't flash in and out on every visit.
        initialValue = 0,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
