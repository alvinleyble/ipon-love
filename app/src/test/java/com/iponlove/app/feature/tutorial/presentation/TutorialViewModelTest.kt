package com.iponlove.app.feature.tutorial.presentation

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.couple.domain.model.Couple
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import com.iponlove.app.feature.tutorial.domain.TutorialTours
import com.iponlove.app.feature.tutorial.domain.repository.TutorialRepository
import com.iponlove.app.feature.tutorial.domain.usecase.ClearAllToursUseCase
import com.iponlove.app.feature.tutorial.domain.usecase.MarkTourSeenUseCase
import com.iponlove.app.feature.tutorial.domain.usecase.ObserveSeenToursUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/** Onboarding gating logic (ADR-0038): seen-set gate, active-tour guard, paired gate, conditional
 *  steps + runtime labels, per-tour skip, replay-clears-all, and the pairing bridge. */
@OptIn(ExperimentalCoroutinesApi::class)
class TutorialViewModelTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeTutorialRepository(initial: Set<String> = emptySet()) : TutorialRepository {
        val seen = MutableStateFlow(initial)
        override fun observeSeenTours(): Flow<Set<String>> = seen
        override suspend fun markTourSeen(tourId: String) { seen.value = seen.value + tourId }
        override suspend fun clearAllTours() { seen.value = emptySet() }
    }

    private fun paired() = PairingState.Paired(
        couple = Couple(
            id = "c1", name = "Us", inviteCode = "ABC123",
            user1Id = "u1", user2Id = "u2", isDeleted = false,
        ),
        partner = null,
    )

    private fun vm(
        repo: FakeTutorialRepository = FakeTutorialRepository(),
        pairing: MutableStateFlow<PairingState> = MutableStateFlow(PairingState.NotPaired),
    ): TutorialViewModel {
        val observePairing = mockk<ObservePairingStateUseCase> {
            every { this@mockk() } returns pairing
        }
        return TutorialViewModel(
            observeSeenTours = ObserveSeenToursUseCase(repo),
            markTourSeen = MarkTourSeenUseCase(repo),
            clearAllTours = ClearAllToursUseCase(repo),
            observePairingState = observePairing,
        )
    }

    @Test
    fun unseenTour_startsAndShowsFirstStep() {
        val vm = vm()
        vm.maybeStartTour(TutorialTours.RECORDS)

        val state = vm.uiState.value
        assertThat(state.activeTourId).isEqualTo(TutorialTours.RECORDS)
        assertThat(state.stepIndex).isEqualTo(0)
        assertThat(state.currentStep?.stepLabel).isEqualTo("1 of 2")
    }

    @Test
    fun seenTour_doesNotStart() {
        val vm = vm(FakeTutorialRepository(setOf(TutorialTours.RECORDS)))
        vm.maybeStartTour(TutorialTours.RECORDS)
        assertThat(vm.uiState.value.active).isFalse()
    }

    @Test
    fun activeTourGuard_secondTourIgnored() {
        val vm = vm()
        vm.maybeStartTour(TutorialTours.RECORDS)
        vm.maybeStartTour(TutorialTours.ANALYSIS)
        assertThat(vm.uiState.value.activeTourId).isEqualTo(TutorialTours.RECORDS)
    }

    @Test
    fun coupleTour_whenUnpaired_staysUnseenAndDoesNotStart() {
        val repo = FakeTutorialRepository()
        val vm = vm(repo)

        vm.maybeStartTour(TutorialTours.COUPLE)

        assertThat(vm.uiState.value.active).isFalse()
        // Must NOT be marked seen — it should fire later once paired.
        assertThat(repo.seen.value).doesNotContain(TutorialTours.COUPLE)
    }

    @Test
    fun coupleTour_whenPaired_starts() {
        val vm = vm(pairing = MutableStateFlow(paired()))
        vm.maybeStartTour(TutorialTours.COUPLE)
        assertThat(vm.uiState.value.activeTourId).isEqualTo(TutorialTours.COUPLE)
    }

    @Test
    fun skip_marksOnlyCurrentTourSeen() {
        val repo = FakeTutorialRepository()
        val vm = vm(repo)
        vm.maybeStartTour(TutorialTours.RECORDS)

        vm.skip()

        assertThat(vm.uiState.value.active).isFalse()
        assertThat(repo.seen.value).containsExactly(TutorialTours.RECORDS)
    }

    @Test
    fun next_advancesThenFinishes_marksSeen() {
        val repo = FakeTutorialRepository()
        val vm = vm(repo)
        vm.maybeStartTour(TutorialTours.RECORDS) // 2 steps

        vm.next()
        assertThat(vm.uiState.value.stepIndex).isEqualTo(1)

        vm.next()
        assertThat(vm.uiState.value.active).isFalse()
        assertThat(repo.seen.value).contains(TutorialTours.RECORDS)
    }

    @Test
    fun conditionalStep_droppedWhenPaired_labelReflectsFilteredCount() {
        // transaction_entry = 1 always + 1 unpaired-only step.
        val unpaired = vm()
        unpaired.maybeStartTour(TutorialTours.TRANSACTION_ENTRY)
        assertThat(unpaired.uiState.value.steps).hasSize(2)
        assertThat(unpaired.uiState.value.currentStep?.stepLabel).isEqualTo("1 of 2")

        val pairedVm = vm(pairing = MutableStateFlow(paired()))
        pairedVm.maybeStartTour(TutorialTours.TRANSACTION_ENTRY)
        assertThat(pairedVm.uiState.value.steps).hasSize(1)
        // A single-step tour shows no "N of M" label and a terminal "Got it" button.
        assertThat(pairedVm.uiState.value.currentStep?.stepLabel).isNull()
        assertThat(pairedVm.uiState.value.currentStep?.primaryLabel).isEqualTo("Got it")
    }

    @Test
    fun replay_clearsSeenSet_andRestartsShell() {
        val repo = FakeTutorialRepository(setOf(TutorialTours.SHELL, TutorialTours.RECORDS))
        val vm = vm(repo)

        vm.replay()

        // Whole set wiped (shell re-marked only when the restarted shell tour finishes).
        assertThat(repo.seen.value).doesNotContain(TutorialTours.RECORDS)
        assertThat(vm.uiState.value.activeTourId).isEqualTo(TutorialTours.SHELL)
    }

    @Test
    fun pairingTransition_firesBridge() {
        val pairing = MutableStateFlow<PairingState>(PairingState.NotPaired)
        val vm = vm(pairing = pairing)
        assertThat(vm.uiState.value.active).isFalse()

        pairing.value = paired()

        assertThat(vm.uiState.value.activeTourId).isEqualTo(TutorialTours.COUPLE_BRIDGE)
    }
}
