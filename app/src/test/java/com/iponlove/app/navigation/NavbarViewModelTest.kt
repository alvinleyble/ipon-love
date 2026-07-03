package com.iponlove.app.navigation

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.couple.domain.model.Couple
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavbarViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun paired() = PairingState.Paired(
        couple = Couple(
            id = "c1",
            name = "Us",
            inviteCode = "ABC123",
            user1Id = "u1",
            user2Id = "u2",
            isDeleted = false,
        ),
        partner = null,
    )

    /**
     * F11 regression: the Activity-scoped NavbarViewModel drives the always-present bottom bar,
     * but IponApp (its only subscriber) is off-composition for the whole onboarding flow. A
     * pairing that lands during that gap must still be reflected — i.e. the state must not freeze
     * stale for want of an active collector. `Eagerly` guarantees this; the old
     * `WhileSubscribed(5s)` did not (the upstream never even started without a subscriber, and
     * cancelled after the grace window once one went away), so the Couple tab never reached the
     * bar until a full process restart.
     */
    @Test
    fun uiState_reflectsPairingChange_withoutAnyActiveSubscriber() = runTest {
        val pairing = MutableStateFlow<PairingState>(PairingState.NotPaired)
        val navConfigRepository = mockk<NavConfigRepository> {
            every { observe() } returns MutableStateFlow(NavConfig())
        }
        val observePairingState = mockk<ObservePairingStateUseCase> {
            every { this@mockk() } returns pairing
        }

        val viewModel = NavbarViewModel(navConfigRepository, observePairingState)

        // Nothing is collecting uiState — mirrors IponApp being torn down during onboarding.
        assertThat(viewModel.uiState.value.loaded).isTrue()
        assertThat(viewModel.uiState.value.isPaired).isFalse()

        // Pairing completes while the bar has no active subscriber.
        pairing.value = paired()

        assertThat(viewModel.uiState.value.isPaired).isTrue()
    }

    @Test
    fun uiState_reflectsUnpair_withoutAnyActiveSubscriber() = runTest {
        val pairing = MutableStateFlow<PairingState>(paired())
        val navConfigRepository = mockk<NavConfigRepository> {
            every { observe() } returns MutableStateFlow(NavConfig())
        }
        val observePairingState = mockk<ObservePairingStateUseCase> {
            every { this@mockk() } returns pairing
        }

        val viewModel = NavbarViewModel(navConfigRepository, observePairingState)
        assertThat(viewModel.uiState.value.isPaired).isTrue()

        pairing.value = PairingState.NotPaired

        assertThat(viewModel.uiState.value.isPaired).isFalse()
    }
}
