package com.iponlove.app.feature.applock

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.applock.domain.model.AppLockPreferences
import com.iponlove.app.feature.applock.domain.model.PinLockoutPolicy
import com.iponlove.app.feature.applock.domain.model.PinVerifyResult
import com.iponlove.app.feature.applock.domain.repository.AppLockRepository
import com.iponlove.app.feature.applock.domain.usecase.VerifyPinUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

class VerifyPinUseCaseTest {

    private val now = Instant.parse("2026-07-04T00:00:00Z")
    private val repo: AppLockRepository = mockk()

    private fun useCase() = VerifyPinUseCase(repo).apply { now = { this@VerifyPinUseCaseTest.now } }

    @Test
    fun correctPin_returnsSuccess_andResetsAttemptState() = runTest {
        coEvery { repo.observe() } returns flowOf(AppLockPreferences(failedPinAttempts = 3))
        coEvery { repo.verifyPin("1234") } returns true
        coEvery { repo.setPinAttemptState(any(), any()) } returns Unit

        val result = useCase()("1234")

        assertThat(result).isEqualTo(PinVerifyResult.Success)
        coVerify { repo.setPinAttemptState(0, null) }
    }

    @Test
    fun wrongPin_belowThreshold_returnsFailedWithRemainingAttempts() = runTest {
        coEvery { repo.observe() } returns flowOf(AppLockPreferences(failedPinAttempts = 1))
        coEvery { repo.verifyPin("0000") } returns false
        coEvery { repo.setPinAttemptState(any(), any()) } returns Unit

        val result = useCase()("0000")

        assertThat(result).isEqualTo(PinVerifyResult.Failed(remainingAttempts = 3))
        coVerify { repo.setPinAttemptState(2, null) }
    }

    @Test
    fun wrongPin_crossingThreshold_returnsLockedOut() = runTest {
        coEvery { repo.observe() } returns flowOf(AppLockPreferences(failedPinAttempts = 4))
        coEvery { repo.verifyPin("0000") } returns false
        coEvery { repo.setPinAttemptState(any(), any()) } returns Unit

        val result = useCase()("0000")

        assertThat(result).isEqualTo(PinVerifyResult.LockedOut(PinLockoutPolicy.COOLDOWN_SECONDS))
        coVerify { repo.setPinAttemptState(5, now.plusSeconds(PinLockoutPolicy.COOLDOWN_SECONDS)) }
    }

    @Test
    fun alreadyLockedOut_returnsLockedOut_withoutCallingVerifyPin() = runTest {
        coEvery { repo.observe() } returns flowOf(
            AppLockPreferences(failedPinAttempts = 5, pinLockoutUntil = now.plusSeconds(10)),
        )

        val result = useCase()("1234")

        assertThat(result).isEqualTo(PinVerifyResult.LockedOut(10))
        coVerify(exactly = 0) { repo.verifyPin(any()) }
    }

    @Test
    fun lockoutExpired_allowsAttemptAgain() = runTest {
        coEvery { repo.observe() } returns flowOf(
            AppLockPreferences(failedPinAttempts = 5, pinLockoutUntil = now.minusSeconds(1)),
        )
        coEvery { repo.verifyPin("1234") } returns true
        coEvery { repo.setPinAttemptState(any(), any()) } returns Unit

        val result = useCase()("1234")

        assertThat(result).isEqualTo(PinVerifyResult.Success)
    }
}
