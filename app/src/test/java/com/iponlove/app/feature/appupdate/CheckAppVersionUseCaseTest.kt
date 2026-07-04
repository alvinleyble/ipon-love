package com.iponlove.app.feature.appupdate

import com.google.common.truth.Truth.assertThat
import com.iponlove.app.feature.appupdate.domain.repository.AppReleaseInfoRepository
import com.iponlove.app.feature.appupdate.domain.usecase.CheckAppVersionUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CheckAppVersionUseCaseTest {

    private val repository = mockk<AppReleaseInfoRepository>()
    private val useCase = CheckAppVersionUseCase(repository)

    @Test
    fun isMismatched_exactMatch_isFalse() {
        assertThat(CheckAppVersionUseCase.isMismatched(installedVersionCode = 3, requiredVersionCode = 3))
            .isFalse()
    }

    @Test
    fun isMismatched_ahead_isTrue() {
        // Exact-match, not floor (ADR-0029) — even being ahead of the published row blocks,
        // since the goal is uniform builds for comparable bug reports, not merely "not behind."
        assertThat(CheckAppVersionUseCase.isMismatched(installedVersionCode = 4, requiredVersionCode = 3))
            .isTrue()
    }

    @Test
    fun isMismatched_behind_isTrue() {
        assertThat(CheckAppVersionUseCase.isMismatched(installedVersionCode = 2, requiredVersionCode = 3))
            .isTrue()
    }

    @Test
    fun invoke_matchingVersion_notBlocked() = runTest {
        coEvery { repository.getRequiredVersionCode() } returns 3

        assertThat(useCase(installedVersionCode = 3)).isFalse()
    }

    @Test
    fun invoke_mismatchedVersion_blocked() = runTest {
        coEvery { repository.getRequiredVersionCode() } returns 3

        assertThat(useCase(installedVersionCode = 2)).isTrue()
    }

    @Test
    fun invoke_fetchFails_failsOpen_notBlocked() = runTest {
        coEvery { repository.getRequiredVersionCode() } throws RuntimeException("offline")

        assertThat(useCase(installedVersionCode = 2)).isFalse()
    }
}
