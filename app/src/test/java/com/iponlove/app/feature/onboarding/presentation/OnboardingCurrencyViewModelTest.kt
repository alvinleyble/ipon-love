package com.iponlove.app.feature.onboarding.presentation

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.settings.domain.model.CurrencySymbol
import com.iponlove.app.feature.settings.domain.usecase.ObserveCurrencySymbolUseCase
import com.iponlove.app.feature.settings.domain.usecase.SetCurrencySymbolUseCase
import io.mockk.coVerify
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

/** Item 27 — the onboarding Currency step's VM is a thin re-entry onto the Item 18 use cases;
 *  these tests cover the pre-selection seed and the instant write-through on selection. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingCurrencyViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val observeCurrencySymbol: ObserveCurrencySymbolUseCase = mockk()
    private val setCurrencySymbol: SetCurrencySymbolUseCase = mockk(relaxed = true)

    private fun viewModel(stored: CurrencySymbol = CurrencySymbol.DEFAULT): OnboardingCurrencyViewModel {
        every { observeCurrencySymbol() } returns MutableStateFlow(stored)
        return OnboardingCurrencyViewModel(
            observeCurrencySymbol = observeCurrencySymbol,
            setCurrencySymbol = setCurrencySymbol,
        )
    }

    @Test
    fun initialState_defaultsToPhp_soATapThroughKeepsPeso() = runTest {
        val viewModel = viewModel()

        assertThat(viewModel.state.value.selected).isEqualTo(CurrencySymbol.PHP)
    }

    @Test
    fun initialState_seedsFromWhateverIsAlreadyStored() = runTest {
        val viewModel = viewModel(stored = CurrencySymbol.USD)

        assertThat(viewModel.state.value.selected).isEqualTo(CurrencySymbol.USD)
    }

    @Test
    fun selectSymbol_updatesLocalStateImmediately() = runTest {
        val viewModel = viewModel()

        viewModel.selectSymbol(CurrencySymbol.EUR)

        assertThat(viewModel.state.value.selected).isEqualTo(CurrencySymbol.EUR)
    }

    @Test
    fun selectSymbol_writesThroughToTheSharedUseCase_instantlyLikeTheSettingsPicker() = runTest {
        val viewModel = viewModel()

        viewModel.selectSymbol(CurrencySymbol.JPY)

        coVerify { setCurrencySymbol(CurrencySymbol.JPY) }
    }
}
