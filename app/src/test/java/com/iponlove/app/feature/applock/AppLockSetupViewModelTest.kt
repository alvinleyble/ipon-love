package com.iponlove.app.feature.applock

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.applock.domain.model.AppLockPreferences
import com.iponlove.app.feature.applock.domain.usecase.EnableBiometricUseCase
import com.iponlove.app.feature.applock.domain.usecase.MarkBiometricNudgeShownUseCase
import com.iponlove.app.feature.applock.domain.usecase.ObserveAppLockUseCase
import com.iponlove.app.feature.applock.domain.usecase.SetPinUseCase
import com.iponlove.app.feature.applock.presentation.AppLockManager
import com.iponlove.app.feature.applock.presentation.AppLockSetupViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockSetupViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val setPin: SetPinUseCase = mockk(relaxed = true)
    private val enableBiometric: EnableBiometricUseCase = mockk(relaxed = true)
    private val markNudgeShown: MarkBiometricNudgeShownUseCase = mockk(relaxed = true)
    private val observeAppLock: ObserveAppLockUseCase = mockk()

    private fun viewModel(appLockManager: AppLockManager) = AppLockSetupViewModel(
        setPin = setPin,
        enableBiometric = enableBiometric,
        markNudgeShown = markNudgeShown,
        appLockManager = appLockManager,
        observeAppLock = observeAppLock,
    )

    @Test
    fun confirmSet_matchingPins_unlocksTheSession() = runTest {
        coEvery { observeAppLock() } returns flowOf(AppLockPreferences())
        val appLockManager = AppLockManager { }
        val viewModel = viewModel(appLockManager)

        "1234".forEach { viewModel.onDigit(it) }
        "1234".forEach { viewModel.onDigit(it) }

        assertThat(appLockManager.isLocked.value).isFalse()
        assertThat(viewModel.uiState.value.pinSetSuccess).isTrue()
    }

    @Test
    fun confirmSet_mismatchedPins_doesNotUnlock() = runTest {
        coEvery { observeAppLock() } returns flowOf(AppLockPreferences())
        val appLockManager = AppLockManager { }
        val viewModel = viewModel(appLockManager)

        "1234".forEach { viewModel.onDigit(it) }
        "5678".forEach { viewModel.onDigit(it) }

        assertThat(appLockManager.isLocked.value).isTrue()
        assertThat(viewModel.uiState.value.pinSetSuccess).isFalse()
    }
}
