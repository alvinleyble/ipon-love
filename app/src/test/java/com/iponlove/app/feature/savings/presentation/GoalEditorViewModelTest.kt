package com.iponlove.app.feature.savings.presentation

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.iponlove.app.core.analytics.Analytics
import com.iponlove.app.core.entitlement.CapCheck
import com.iponlove.app.feature.couple.domain.model.PairingState
import com.iponlove.app.feature.couple.domain.usecase.ObservePairingStateUseCase
import com.iponlove.app.feature.savings.domain.usecase.CheckSavingsGoalCapUseCase
import com.iponlove.app.feature.savings.domain.usecase.GetSavingsGoalUseCase
import com.iponlove.app.feature.savings.domain.usecase.ShareSavingsGoalUseCase
import com.iponlove.app.feature.savings.domain.usecase.UnshareSavingsGoalUseCase
import com.iponlove.app.feature.savings.domain.usecase.UpsertSavingsGoalUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalEditorViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // Caps are dormant in these tests — the gate always allows, so save()/toggleShared() behave
        // exactly as before S7.
        coEvery { checkGoalCap(any()) } returns CapCheck.Allowed
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val getGoal: GetSavingsGoalUseCase = mockk(relaxed = true)
    private val upsertGoal: UpsertSavingsGoalUseCase = mockk(relaxed = true)
    private val shareGoal: ShareSavingsGoalUseCase = mockk(relaxed = true)
    private val unshareGoal: UnshareSavingsGoalUseCase = mockk(relaxed = true)
    private val checkGoalCap: CheckSavingsGoalCapUseCase = mockk()
    private val analytics: Analytics = mockk(relaxed = true)
    private val observePairingState: ObservePairingStateUseCase = mockk()

    private fun viewModel(savedStateHandle: SavedStateHandle) = GoalEditorViewModel(
        savedStateHandle = savedStateHandle,
        getGoal = getGoal,
        upsertGoal = upsertGoal,
        shareGoal = shareGoal,
        unshareGoal = unshareGoal,
        checkGoalCap = checkGoalCap,
        analytics = analytics,
        observePairingState = observePairingState,
    )

    @Test
    fun editsSurviveRecreation_viaSavedStateHandle() = runTest {
        every { observePairingState() } returns flowOf(PairingState.NotPaired)
        val handle = SavedStateHandle(mapOf(GoalEditorViewModel.GOAL_ID_KEY to GoalEditorViewModel.NEW_GOAL))
        val first = viewModel(handle)

        first.onNameChange("Emergency fund")
        first.onTargetChange("50000")
        first.onDateChange(LocalDate.of(2027, 1, 1))
        first.onIconChange("piggy_bank")
        first.onColorChange("#FF0000")

        // Simulate process death + recreation: a fresh VM over the same (surviving) handle.
        val recreated = viewModel(handle)

        with(recreated.uiState.value) {
            assertThat(name).isEqualTo("Emergency fund")
            assertThat(targetText).isEqualTo("50000")
            assertThat(targetDate).isEqualTo(LocalDate.of(2027, 1, 1))
            assertThat(icon).isEqualTo("piggy_bank")
            assertThat(color).isEqualTo("#FF0000")
            assertThat(goalId).isEqualTo(first.uiState.value.goalId)
        }
    }

    @Test
    fun draftClearsFromSavedStateHandle_afterSuccessfulSave() = runTest {
        every { observePairingState() } returns flowOf(PairingState.NotPaired)
        val handle = SavedStateHandle(mapOf(GoalEditorViewModel.GOAL_ID_KEY to GoalEditorViewModel.NEW_GOAL))
        val viewModel = viewModel(handle)

        viewModel.onNameChange("Vacation")
        viewModel.onTargetChange("10000")
        var doneCalled = false
        viewModel.save { doneCalled = true }

        assertThat(doneCalled).isTrue()
        assertThat(handle.get<String>("draft_name")).isNull()
        assertThat(handle.get<String>("draft_target_text")).isNull()
    }
}
