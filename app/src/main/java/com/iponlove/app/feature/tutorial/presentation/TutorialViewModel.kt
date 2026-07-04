package com.iponlove.app.feature.tutorial.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.feature.tutorial.domain.usecase.MarkTutorialSeenUseCase
import com.iponlove.app.feature.tutorial.domain.usecase.ShouldShowTutorialUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the first-run tutorial state (ADR-0034). Auto-starts the tour once per app-shell mount when
 * the local gate says it hasn't been seen; can also be replayed on demand from Settings. It never
 * drives navigation — [onTargetActivated] is how the shell tells it a "wait for the real tap" step
 * (e.g. the More sheet opening) actually happened.
 */
@HiltViewModel
class TutorialViewModel @Inject constructor(
    private val shouldShowTutorial: ShouldShowTutorialUseCase,
    private val markTutorialSeen: MarkTutorialSeenUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TutorialUiState())
    val uiState: StateFlow<TutorialUiState> = _uiState.asStateFlow()

    // Guards against re-checking the gate on every recomposition-driven maybeStart() call.
    private var autoChecked = false

    /** First-run trigger: start the tour only if the gate hasn't been satisfied yet. */
    fun maybeStart() {
        if (autoChecked) return
        autoChecked = true
        viewModelScope.launch {
            if (shouldShowTutorial()) start()
        }
    }

    /** Manual "Replay tutorial" entry point from Settings — always restarts, ignoring the flag. */
    fun replay() {
        start()
    }

    private fun start() {
        _uiState.value = TutorialUiState(active = true, stepIndex = 0)
    }

    /** Advance from a "Next"-button step, or finish if this was the last step. */
    fun next() {
        val state = _uiState.value
        if (!state.active) return
        if (state.stepIndex >= TutorialScript.lastIndex) {
            finish()
        } else {
            _uiState.value = state.copy(stepIndex = state.stepIndex + 1)
        }
    }

    /**
     * Notify the tutorial that [targetKey] was really operated. Advances only when the current step
     * is a wait-for-tap step for that same target, so unrelated shell events are ignored.
     */
    fun onTargetActivated(targetKey: String) {
        val state = _uiState.value
        if (!state.active) return
        val step = TutorialScript.stepAt(state.stepIndex) ?: return
        if (step.advancesOnTap && step.coachMark.targetKey == targetKey) {
            next()
        }
    }

    fun skip() {
        finish()
    }

    private fun finish() {
        _uiState.value = TutorialUiState(active = false)
        viewModelScope.launch { markTutorialSeen() }
    }
}
