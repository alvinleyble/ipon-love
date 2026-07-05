package com.iponlove.app.feature.tutorial.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iponlove.app.core.ui.CoachMarkStep
import com.iponlove.app.core.ui.TutorialController
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.domain.usecase.ClearAllToursUseCase
import com.iponlove.app.feature.tutorial.domain.usecase.MarkTourSeenUseCase
import com.iponlove.app.feature.tutorial.domain.usecase.ObserveSeenToursUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the onboarding coach-mark state (ADR-0038): a registry of per-module lazy tours gated by a
 * local set of seen tour IDs, with couple tours additionally gated on pairing. It drives the single
 * [com.iponlove.app.core.ui.CoachMarkOverlay] at the app shell and is shared to every feature screen
 * as a [TutorialController] so a screen can arm its own first-visit tour without knowing about Hilt
 * scoping. It never drives navigation — [onTargetActivated] is how the shell reports a real tap.
 */
@HiltViewModel
class TutorialViewModel @Inject constructor(
    observeSeenTours: ObserveSeenToursUseCase,
    private val markTourSeen: MarkTourSeenUseCase,
    private val clearAllTours: ClearAllToursUseCase,
    observePairingState: ObservePairingStateUseCase,
) : ViewModel(), TutorialController {

    private val _uiState = MutableStateFlow(TutorialUiState())
    val uiState: StateFlow<TutorialUiState> = _uiState.asStateFlow()

    // null until the first DataStore read lands — gates every start so a tour the user already saw
    // never fires just because the set hadn't loaded when the screen composed.
    private val seenTours: StateFlow<Set<String>?> =
        observeSeenTours().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val pairingState: StateFlow<PairingState> =
        observePairingState().stateIn(viewModelScope, SharingStarted.Eagerly, PairingState.Loading)

    private fun isPaired(): Boolean = pairingState.value is PairingState.Paired

    // The pairing-filtered step list backing the active tour — the source for advancesOnTap lookups.
    private var activeSteps: List<TutorialStep> = emptyList()

    init {
        // The one-shot pairing bridge fires only on a real NotPaired→Paired transition observed
        // live — the actual pairing moment. Keying off the raw state (not a mapped boolean with a
        // synthetic `false` initial) avoids a spurious bridge for an already-paired reinstall, whose
        // sequence is Loading→Paired: no preceding NotPaired, so no fire. Couple screen tours still
        // fire lazily on first visit either way.
        viewModelScope.launch {
            var prev: PairingState? = null
            pairingState.collect { state ->
                if (prev is PairingState.NotPaired && state is PairingState.Paired) {
                    maybeStartTour(TutorialTours.COUPLE_BRIDGE)
                }
                prev = state
            }
        }
    }

    /**
     * First-visit trigger for [tourId]. No-ops if the seen set hasn't loaded to a decision yet, if
     * the tour is already seen, if another tour is active (this one stays unseen and re-fires
     * later), or if it's a couple tour and the user isn't paired (also stays unseen until paired).
     */
    override fun maybeStartTour(tourId: String) {
        viewModelScope.launch {
            val seen = seenTours.filterNotNull().first()
            if (_uiState.value.active) return@launch
            if (tourId in seen) return@launch
            if (tourId in TutorialTours.PAIRED_TOURS && !isPaired()) return@launch

            val steps = TutorialScript.stepsFor(tourId)
                .filter { it.condition.matches(isPaired()) }
            if (steps.isEmpty()) {
                // Nothing to show for this pairing state — count it seen so it doesn't keep retrying.
                markTourSeen(tourId)
                return@launch
            }
            activeSteps = steps
            _uiState.value = TutorialUiState(
                activeTourId = tourId,
                stepIndex = 0,
                steps = steps.toCoachMarks(),
            )
        }
    }

    /** Manual "Replay tutorial" (ADR-0038 dec. 5): wipe the whole set and restart the shell tour. */
    fun replay() {
        viewModelScope.launch {
            _uiState.value = TutorialUiState()
            activeSteps = emptyList()
            clearAllTours()
            // Wait for the cleared set to propagate so maybeStartTour below sees the shell un-seen,
            // not the stale pre-clear value the StateFlow may still be holding.
            seenTours.first { it != null && TutorialTours.SHELL !in it }
            maybeStartTour(TutorialTours.SHELL)
        }
    }

    /** Advance from a "Next"/"Got it" step, or finish the tour if this was its last step. */
    fun next() {
        val state = _uiState.value
        if (!state.active) return
        if (state.stepIndex >= state.steps.lastIndex) {
            finish()
        } else {
            _uiState.value = state.copy(stepIndex = state.stepIndex + 1)
        }
    }

    /** Advances only when the current step is a wait-for-tap step for [targetKey]. */
    override fun onTargetActivated(targetKey: String) {
        val state = _uiState.value
        if (!state.active) return
        val step = activeSteps.getOrNull(state.stepIndex) ?: return
        if (step.advancesOnTap && step.targetKey == targetKey) next()
    }

    /** Skip = mark only this tour seen (ADR-0038 dec. 5); other tours still fire on their visits. */
    fun skip() = finish()

    private fun finish() {
        val tourId = _uiState.value.activeTourId
        _uiState.value = TutorialUiState()
        activeSteps = emptyList()
        if (tourId != null) viewModelScope.launch { markTourSeen(tourId) }
    }

    /** Resolve raw steps into rendered coach marks — runtime "N of M" label + primary-button copy. */
    private fun List<TutorialStep>.toCoachMarks(): List<CoachMarkStep> =
        mapIndexed { i, step ->
            CoachMarkStep(
                targetKey = step.targetKey,
                title = step.title,
                text = step.text,
                stepLabel = if (size > 1) "${i + 1} of $size" else null,
                primaryLabel = when {
                    step.advancesOnTap -> null
                    i == lastIndex -> "Got it"
                    else -> "Next"
                },
            )
        }
}
